package com.netcoremessenger.core.data.websocket

import android.util.Log
import com.netcoremessenger.core.data.local.dao.MessageDao
import com.netcoremessenger.core.data.local.dao.ChatDao
import com.netcoremessenger.core.data.local.dao.UserDao
import com.netcoremessenger.core.data.local.entity.MessageEntity
import com.netcoremessenger.core.data.remote.dto.MessageDto
import com.netcoremessenger.core.data.remote.interceptor.AuthTokenRefresher
import com.netcoremessenger.core.data.offline.OutboxManager
import com.netcoremessenger.core.data.offline.SyncManager
import com.netcoremessenger.core.data.repository.ChatRepository
import com.netcoremessenger.core.util.Constants
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.UUID
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class WebSocketManager @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val gson: Gson,
    private val eventRouter: EventRouter,
    private val outboxManager: OutboxManager,
    private val syncManager: SyncManager,
    private val messageDao: MessageDao,
    private val chatDao: ChatDao,
    private val userDao: UserDao,
    private val chatRepositoryProvider: Provider<ChatRepository>,
    private val tokenRefresher: AuthTokenRefresher
) {
    private var webSocket: WebSocket? = null
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    val events: SharedFlow<WebSocketEvent> get() = eventRouter.events

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val heartbeatScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val reconnectScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var heartbeatActive = false
    private var reconnecting = false
    private var authRefreshPending = false
    @Volatile private var appForeground = false

    init {
        scope.launch {
            events.collect { event ->
                try {
                    when (event) {
                        is WebSocketEvent.MessageNew -> {
                            val chatExists = chatDao.getById(event.data.chat_id) != null
                            if (!chatExists) {
                                chatRepositoryProvider.get().fetchChatDetails(event.data.chat_id)
                            }
                            event.data.client_id?.let {
                                messageDao.deletePendingByClientId(it)
                            }
                            val entity = MessageEntity(
                                id = event.data.id,
                                clientId = event.data.client_id,
                                chatId = event.data.chat_id,
                                senderId = event.data.sender_id,
                                type = event.data.type,
                                content = event.data.content,
                                albumId = event.data.album_id,
                                replyToMsgId = event.data.reply_to_msg_id,
                                sortKey = event.data.sort_key,
                                createdAt = event.data.created_at,
                                status = event.data.status ?: "sent",
                                isDeleted = false
                            )
                            messageDao.insertPreservingStatus(entity)
                            sendMessageDelivery(event.data.id, event.data.chat_id)
                        }
                        is WebSocketEvent.MessageSent -> {
                            messageDao.confirmSent(event.data.client_id, event.data.id, event.data.sort_key)
                        }
                        is WebSocketEvent.MessageDelivered -> {
                            messageDao.updateStatus(event.data.message_id, "delivered")
                        }
                        is WebSocketEvent.MessageRead -> {
                            messageDao.updateStatus(event.data.message_id, "read")
                        }
                        is WebSocketEvent.PresenceOnline -> {
                            userDao.updatePresence(event.userId, "online", null)
                        }
                        is WebSocketEvent.PresenceOffline -> {
                            userDao.updatePresence(
                                event.userId,
                                "offline",
                                event.lastSeen ?: System.currentTimeMillis()
                            )
                        }
                        is WebSocketEvent.ChatNew -> {
                            chatRepositoryProvider.get().fetchChatDetails(event.data.id)
                        }
                        is WebSocketEvent.ChatUpdated -> {
                            chatRepositoryProvider.get().fetchChatDetails(event.chatId)
                        }
                        is WebSocketEvent.SyncMessages -> {
                            val dtos = event.messages.map { msg ->
                                MessageDto(
                                    id = msg.id,
                                    clientId = msg.client_id,
                                    chatId = msg.chat_id,
                                    senderId = msg.sender_id,
                                    type = msg.type,
                                    content = msg.content,
                                    albumId = msg.album_id,
                                    replyToMsgId = msg.reply_to_msg_id,
                                    sortKey = msg.sort_key,
                                    createdAt = msg.created_at,
                                    status = msg.status ?: "sent"
                                )
                            }
                            syncManager.handleSyncMessages(dtos, event.newLastSortKey, event.hasMore)
                        }
                        else -> {}
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to apply websocket event: $event", e)
                }
            }
        }
    }

    private var lastToken: String? = null
    private var lastDeviceId: String? = null

    fun connect(token: String, deviceId: String) {
        connectInternal(token, deviceId, resetReconnectState = true)
    }

    private fun connectInternal(token: String, deviceId: String, resetReconnectState: Boolean) {
        if (_connectionState.value == ConnectionState.CONNECTED && webSocket != null) return

        lastToken = token
        lastDeviceId = deviceId
        _connectionState.value = ConnectionState.CONNECTING
        if (resetReconnectState) {
            reconnecting = false
            authRefreshPending = false
        }

        webSocket?.cancel()
        webSocket = null

        outboxManager.startObserving(connectionState)
        syncManager.startObserving(connectionState)

        val url = "${Constants.WS_URL}/ws/v1?token=$token&device_id=$deviceId"
        val request = Request.Builder().url(url).build()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                _connectionState.value = ConnectionState.CONNECTED
                reconnecting = false
                startHeartbeat()
                if (appForeground) {
                    sendPresenceForeground()
                }
            }

            override fun onMessage(ws: WebSocket, text: String) {
                try {
                    val envelope = gson.fromJson(text, WebSocketEnvelope::class.java)
                    eventRouter.route(envelope)
                } catch (e: Exception) {
                    Log.e(TAG, "Malformed websocket message: $text", e)
                }
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                ws.close(1000, null)
                _connectionState.value = ConnectionState.DISCONNECTED
                stopHeartbeat()
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                val code = response?.code
                Log.e(TAG, "WebSocket failure: $code", t)
                _connectionState.value = ConnectionState.RECONNECTING
                stopHeartbeat()
                if (code == 401 || code == 403) {
                    authRefreshPending = true
                    scheduleReconnect(initialDelayMs = 0L)
                } else {
                    scheduleReconnect()
                }
            }
        })
    }

    private fun startHeartbeat() {
        heartbeatActive = true
        heartbeatScope.launch {
            while (isActive && heartbeatActive) {
                delay(Constants.HEARTBEAT_INTERVAL_MS)
                send("ping", emptyMap<String, Any>())
            }
        }
    }

    private fun stopHeartbeat() {
        heartbeatActive = false
    }

    private fun scheduleReconnect(initialDelayMs: Long = 1000L) {
        if (reconnecting) return
        reconnecting = true
        reconnectScope.launch {
            var delayMs = initialDelayMs
            repeat(10) {
                if (delayMs > 0L) {
                    delay(delayMs)
                }
                if (_connectionState.value == ConnectionState.CONNECTED) {
                    reconnecting = false
                    return@launch
                }
                val deviceId = lastDeviceId
                if (deviceId == null) {
                    reconnecting = false
                    _connectionState.value = ConnectionState.DISCONNECTED
                    return@launch
                }

                val token = if (authRefreshPending || lastToken == null) {
                    authRefreshPending = false
                    tokenRefresher.refreshAccessToken()?.also { lastToken = it }
                } else {
                    lastToken
                }

                if (token == null) {
                    reconnecting = false
                    _connectionState.value = ConnectionState.DISCONNECTED
                    return@launch
                }

                connectInternal(token, deviceId, resetReconnectState = false)
                delayMs = (if (delayMs == 0L) 1000L else delayMs * 2).coerceAtMost(60_000)
            }
            reconnecting = false
            if (_connectionState.value != ConnectionState.CONNECTED) {
                _connectionState.value = ConnectionState.DISCONNECTED
            }
        }
    }

    fun send(event: String, data: Any) {
        val envelope = mapOf(
            "event" to event,
            "data" to data,
            "id" to UUID.randomUUID().toString(),
            "timestamp" to System.currentTimeMillis()
        )
        webSocket?.send(gson.toJson(envelope))
    }

    fun setAppForeground(foreground: Boolean) {
        appForeground = foreground
        if (_connectionState.value == ConnectionState.CONNECTED) {
            if (foreground) {
                sendPresenceForeground()
            } else {
                sendPresenceBackground()
            }
        }
    }

    fun isAppForeground(): Boolean = appForeground

    fun sendPresenceForeground() {
        send("presence.foreground", emptyMap<String, Any>())
    }

    fun sendPresenceBackground() {
        send("presence.background", emptyMap<String, Any>())
    }

    fun disconnect() {
        if (_connectionState.value == ConnectionState.CONNECTED) {
            sendPresenceBackground()
        }
        stopHeartbeat()
        outboxManager.stop()
        syncManager.stop()
        webSocket?.close(1000, "User disconnected")
        webSocket = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    fun sendTyping(chatId: Long, typing: Boolean) {
        val event = if (typing) "typing.start" else "typing.stop"
        send(event, mapOf("chat_id" to chatId))
    }

    fun sendMessageDelivery(messageId: Long, chatId: Long) {
        send("message.delivered", mapOf("message_id" to messageId, "chat_id" to chatId))
    }

    fun sendReadReceipt(messageId: Long, chatId: Long) {
        send("message.read", mapOf("message_id" to messageId, "chat_id" to chatId))
    }

    private companion object {
        const val TAG = "WebSocketManager"
    }
}
