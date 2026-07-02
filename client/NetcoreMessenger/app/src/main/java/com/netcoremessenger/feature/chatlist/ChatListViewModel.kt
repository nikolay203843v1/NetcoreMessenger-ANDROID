package com.netcoremessenger.feature.chatlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.netcoremessenger.core.data.local.dao.ChatWithLastMessage
import com.netcoremessenger.core.data.repository.ChatRepository
import com.netcoremessenger.core.data.store.TokenDataStore
import com.netcoremessenger.core.data.websocket.ConnectionState
import com.netcoremessenger.core.data.websocket.WebSocketEvent
import com.netcoremessenger.core.data.websocket.WebSocketManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

data class ChatListUiState(
    val chats: List<ChatListItemUi> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val totalUnread: Int = 0,
    val isRefreshing: Boolean = false
)

data class ChatListItemUi(
    val chatId: Long,
    val title: String,
    val avatarUrl: String?,
    val lastMessage: String?,
    val lastImagePreviewUrls: List<String>,
    val lastMessageTime: Instant?,
    val unreadCount: Int,
    val isOnline: Boolean,
    val isTyping: Boolean,
    val chatType: String,
    val lastMessageType: String?
)

@HiltViewModel
class ChatListViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val tokenDataStore: TokenDataStore,
    private val webSocketManager: WebSocketManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatListUiState())
    val uiState: StateFlow<ChatListUiState> = _uiState.asStateFlow()

    private var currentUserId: Long = 0
    private val fetchedChatDetails = mutableSetOf<Long>()

    init {
        loadChats()
        observeWebSocketEvents()
    }

    private fun loadChats() {
        viewModelScope.launch {
            currentUserId = tokenDataStore.userId.first() ?: return@launch

            launch {
                chatRepository.observeChats(currentUserId).collect { chats ->
                    val items = chats.map { it.toUiModel() }
                    _uiState.update {
                        it.copy(
                            chats = items,
                            totalUnread = items.sumOf { chat -> chat.unreadCount }
                        )
                    }
                }
            }

            _uiState.update { it.copy(isLoading = true) }
            chatRepository.fetchChats(currentUserId)
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    private fun ChatWithLastMessage.toUiModel(): ChatListItemUi {
        val avatarId = avatarMediaId ?: otherUserAvatar
        val itemTitle = title ?: otherUserName
        if ((itemTitle == null || itemTitle == "Unknown") && !fetchedChatDetails.contains(id)) {
            fetchedChatDetails.add(id)
            viewModelScope.launch {
                runCatching { chatRepository.fetchChatDetails(id) }
            }
        }

        val finalTitle = if (!itemTitle.isNullOrBlank() && itemTitle != "Unknown") itemTitle else "User"

        return ChatListItemUi(
            chatId = id,
            title = finalTitle,
            avatarUrl = avatarId?.let { "${com.netcoremessenger.core.util.Constants.BASE_URL}/api/v1/media/$it" },
            lastMessage = formatLastMessage(lastMessage, lastMessageType),
            lastImagePreviewUrls = parseImagePreviewUrls(lastImageIds),
            lastMessageTime = lastMessageTime?.let { Instant.ofEpochMilli(it) },
            unreadCount = unreadCount,
            isOnline = otherUserStatus == "online",
            isTyping = false,
            chatType = type,
            lastMessageType = lastMessageType
        )
    }

    private fun formatLastMessage(content: String?, type: String?): String? {
        if (content == null) return null
        return when (type) {
            "text" -> content.take(100)
            "image" -> "Фото"
            "video" -> "\uD83C\uDFAC Video"
            "voice" -> "\uD83C\uDFA4 Voice message"
            "service" -> content.take(100)
            "circle" -> "\uD83D\uDD35 Кружок"
            "document" -> "\uD83D\uDCC4 Document"
            "location" -> "\uD83D\uDCCD Location"
            "contact" -> "\uD83D\uDC64 Contact"
            else -> content.take(100)
        }
    }

    private fun parseImagePreviewUrls(ids: String?): List<String> {
        if (ids.isNullOrBlank()) return emptyList()
        return ids.split(",")
            .mapNotNull { it.trim().toLongOrNull() }
            .take(4)
            .map { "${com.netcoremessenger.core.util.Constants.BASE_URL}/api/v1/media/$it/thumbnail" }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            chatRepository.fetchChats(currentUserId)
                .onFailure { e ->
                    _uiState.update { it.copy(error = e.message) }
                }
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    fun onChatTapped(chatId: Long) {
        // Navigation handled by ChatListScreen
    }

    fun onErrorDismissed() {
        _uiState.update { it.copy(error = null) }
    }

    private fun observeWebSocketEvents() {
        viewModelScope.launch {
            webSocketManager.events.collect { event ->
                when (event) {
                    is WebSocketEvent.MessageNew -> {
                        if (chatRepository.getChat(event.data.chat_id).getOrNull() == null) {
                            refresh()
                        }
                    }
                    is WebSocketEvent.ChatNew -> {
                        refresh()
                    }
                    is WebSocketEvent.PresenceOnline,
                    is WebSocketEvent.PresenceOffline -> {
                        // Статус пишет WebSocketManager в users; список обновится через Room.
                    }
                    else -> {}
                }
            }
        }
    }
}
