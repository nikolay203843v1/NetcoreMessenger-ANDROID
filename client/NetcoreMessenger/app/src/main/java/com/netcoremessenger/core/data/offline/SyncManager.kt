package com.netcoremessenger.core.data.offline

import com.netcoremessenger.core.data.local.dao.ChatDao
import com.netcoremessenger.core.data.local.dao.MessageDao
import com.netcoremessenger.core.data.local.dao.SyncStateDao
import com.netcoremessenger.core.data.local.entity.MessageEntity
import com.netcoremessenger.core.data.local.entity.SyncStateEntity
import com.netcoremessenger.core.data.remote.api.MessagesApi
import com.netcoremessenger.core.data.remote.dto.MessageDto
import com.netcoremessenger.core.data.repository.ChatRepository
import com.netcoremessenger.core.data.store.TokenDataStore
import com.netcoremessenger.core.data.websocket.ConnectionState
import com.netcoremessenger.core.data.websocket.WebSocketManager
import com.netcoremessenger.core.util.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class SyncManager @Inject constructor(
    private val messageDao: MessageDao,
    private val syncStateDao: SyncStateDao,
    private val chatDao: ChatDao,
    private val messagesApi: MessagesApi,
    private val tokenDataStore: TokenDataStore,
    private val webSocketManagerProvider: Provider<WebSocketManager>,
    private val chatRepositoryProvider: Provider<ChatRepository>
) {
    private var syncJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var isSyncing = false

    fun startObserving(connectionState: StateFlow<ConnectionState>) {
        syncJob?.cancel()
        syncJob = scope.launch {
            connectionState.collect { state ->
                if (state == ConnectionState.CONNECTED && !isSyncing) {
                    sync()
                }
            }
        }
    }

    suspend fun sync() {
        if (isSyncing) return
        isSyncing = true

        try {
            val userId = tokenDataStore.userId.firstOrNull() ?: return
            val deviceId = tokenDataStore.deviceId.firstOrNull() ?: return

            val syncState = syncStateDao.getByUserId(userId)
            var lastSortKey = syncState?.lastSortKey ?: 0L

            webSocketManagerProvider.get().send(
                "sync.request",
                mapOf(
                    "last_sort_key" to lastSortKey,
                    "device_id" to deviceId
                )
            )
        } catch (e: Exception) {
            // Sync failed, will retry on next reconnect
        } finally {
            isSyncing = false
        }
    }

    suspend fun handleSyncMessages(
        messages: List<MessageDto>,
        newLastSortKey: Long,
        hasMore: Boolean
    ) {
        val uniqueChatIds = messages.map { it.chatId }.distinct()
        for (chatId in uniqueChatIds) {
            val chatExists = chatDao.getById(chatId) != null
            if (!chatExists) {
                runCatching {
                    chatRepositoryProvider.get().fetchChatDetails(chatId)
                }.onFailure {
                    // Log or handle error to avoid aborting sync if one chat fails
                }
            }
        }

        val entities = messages.map { dto ->
            MessageEntity(
                id = dto.id,
                clientId = dto.clientId,
                chatId = dto.chatId,
                senderId = dto.senderId,
                type = dto.type,
                content = dto.content,
                albumId = dto.albumId,
                replyToMsgId = dto.replyToMsgId,
                sortKey = dto.sortKey,
                createdAt = dto.createdAt,
                status = dto.status ?: "sent",
                isDeleted = false
            )
        }

        val clientIds = entities.mapNotNull { it.clientId }
        for (clientId in clientIds) {
            messageDao.deletePendingByClientId(clientId)
        }
        messageDao.insertAll(entities)

        val userId = tokenDataStore.userId.firstOrNull() ?: return
        syncStateDao.update(userId, newLastSortKey, System.currentTimeMillis())

        tokenDataStore.saveLastSortKey(newLastSortKey)

        if (hasMore) {
            sync()
        }
    }

    fun stop() {
        syncJob?.cancel()
    }
}
