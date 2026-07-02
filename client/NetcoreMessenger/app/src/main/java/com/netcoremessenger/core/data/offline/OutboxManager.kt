package com.netcoremessenger.core.data.offline

import com.netcoremessenger.core.data.local.dao.MessageDao
import com.netcoremessenger.core.data.local.entity.MessageEntity
import com.netcoremessenger.core.data.websocket.ConnectionState
import com.netcoremessenger.core.data.websocket.WebSocketManager
import com.netcoremessenger.core.util.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class OutboxManager @Inject constructor(
    private val messageDao: MessageDao,
    private val webSocketManagerProvider: Provider<WebSocketManager>
) {
    private val retryDelays = longArrayOf(1, 2, 4, 8, 16, 32, 60)
    private var flushJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    fun startObserving(connectionState: StateFlow<ConnectionState>) {
        flushJob?.cancel()
        flushJob = scope.launch {
            connectionState.collect { state ->
                if (state == ConnectionState.CONNECTED) {
                    flushOutbox()
                }
            }
        }
    }

    suspend fun flushOutbox() {
        val pending = messageDao.getPendingMessages()
        for (message in pending) {
            val retryCount = message.retryCount

            if (retryCount >= Constants.MAX_MESSAGE_RETRY) {
                messageDao.updateStatusByClientId(message.clientId!!, "failed")
                continue
            }

            if (message.nextRetryAt > System.currentTimeMillis()) {
                continue
            }

            try {
                webSocketManagerProvider.get().send(
                    "message.send",
                    mapOf(
                        "client_id" to message.clientId,
                        "chat_id" to message.chatId,
                        "type" to message.type,
                        "content" to message.content,
                        "album_id" to message.albumId,
                        "reply_to_msg_id" to message.replyToMsgId
                    )
                )
            } catch (e: Exception) {
                val delaySec = retryDelays[retryCount.coerceAtMost(retryDelays.size - 1)]
                messageDao.incrementRetry(
                    message.clientId!!,
                    System.currentTimeMillis() + delaySec * 1000
                )
            }
        }
    }

    suspend fun addToOutbox(message: MessageEntity) {
        messageDao.insert(message)
        if (webSocketManagerProvider.get().connectionState.value == ConnectionState.CONNECTED) {
            flushOutbox()
        }
    }

    fun stop() {
        flushJob?.cancel()
    }
}
