package com.netcoremessenger.feature.chat

import android.content.Context
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.netcoremessenger.core.data.local.entity.MessageEntity
import com.netcoremessenger.core.data.offline.MessageDeduplicator
import com.netcoremessenger.core.data.repository.ChatRepository
import com.netcoremessenger.core.data.repository.MessageRepository
import com.netcoremessenger.core.data.store.TokenDataStore
import com.netcoremessenger.core.data.websocket.WebSocketEvent
import com.netcoremessenger.core.data.websocket.WebSocketManager
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

@Immutable
data class ChatUiState(
    val chatId: Long,
    val chatTitle: String = "",
    val chatAvatar: String? = null,
    val peerUserId: Long? = null,
    val isOnline: Boolean = false,
    val messages: List<MessageUiModel> = emptyList(),
    val inputText: String = "",
    val isRecordingVoice: Boolean = false,
    val selectedImageUris: List<Uri> = emptyList(),
    val isSendingAttachments: Boolean = false,
    val uploadingAlbumId: String? = null,
    val uploadProgress: Int = 0,
    val circleUploadProgress: Int? = null,
    val replyTo: MessageUiModel.Message? = null,
    val actionMessage: MessageUiModel.Message? = null,
    val actionAlbum: MessageUiModel.Album? = null,
    val editingMessage: MessageUiModel.Message? = null,
    val forwardMessage: MessageUiModel.Message? = null,
    val forwardTargets: List<ForwardTargetUi> = emptyList(),
    val highlightedMessageLocalId: String? = null,
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val otherUserTyping: Boolean = false,
    val error: String? = null
)

@Immutable
data class ForwardTargetUi(
    val chatId: Long,
    val title: String
)

@Immutable
sealed class MessageUiModel {
    @Immutable
    data class Message(
        val localId: String,
        val serverId: Long?,
        val type: String,
        val content: String,
        val albumId: String? = null,
        val isOutgoing: Boolean,
        val timestamp: Instant,
        val status: String,
        val replyTo: MessageUiModel? = null,
        val isDeleted: Boolean = false
    ) : MessageUiModel()

    @Immutable
    data class Album(
        val localId: String,
        val serverIds: List<Long?>,
        val items: List<Message>,
        val caption: String?,
        val isOutgoing: Boolean,
        val timestamp: Instant,
        val status: String
    ) : MessageUiModel()

    @Immutable
    data class DateSeparator(val date: LocalDate) : MessageUiModel()
}

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val messageRepository: MessageRepository,
    private val chatRepository: ChatRepository,
    private val tokenDataStore: TokenDataStore,
    private val webSocketManager: WebSocketManager,
    private val messageDeduplicator: MessageDeduplicator,
    private val gson: Gson,
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private var mediaRecorder: MediaRecorder? = null
    private var recordingFile: File? = null

    private val chatId: Long = savedStateHandle["chatId"]!!

    private val _uiState = MutableStateFlow(ChatUiState(chatId = chatId))
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var currentUserId: Long = 0
    private var circleSendJob: Job? = null

    init {
        loadUserId()
        observeMessages()
        observeUploads()
        observeWebSocketEvents()
        loadChatInfo()
        observeChatPartnerInfo()
    }

    private fun observeUploads() {
        viewModelScope.launch {
            messageRepository.albumUploadProgress.collect { progressByAlbum ->
                val active = progressByAlbum.entries.firstOrNull()
                _uiState.update {
                    it.copy(
                        isSendingAttachments = active != null,
                        uploadingAlbumId = active?.key,
                        uploadProgress = active?.value ?: 0
                    )
                }
            }
        }
    }

    private fun loadUserId() {
        viewModelScope.launch {
            currentUserId = tokenDataStore.userId.first() ?: return@launch
        }
    }

    private fun observeMessages() {
        viewModelScope.launch {
            combine(
                messageRepository.observeMessages(chatId),
                tokenDataStore.userId
            ) { messages, userId ->
                currentUserId = userId ?: 0L
                messages to messagesToUiModels(messages)
            }.collect { (rawMessages, uiModels) ->
                try {
                    _uiState.update {
                        it.copy(messages = uiModels, isLoading = false)
                    }
                    // Помечаем входящие как прочитанные
                    val unread = rawMessages.filter {
                        it.senderId != currentUserId && it.status != "read" && it.id != null && !it.isDeleted
                    }
                    val unreadIds = unread.mapNotNull { it.id }
                    if (unreadIds.isNotEmpty()) {
                        messageRepository.updateStatuses(unreadIds, "read")
                        unreadIds.forEach { id ->
                            webSocketManager.sendReadReceipt(id, chatId)
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ChatViewModel", "Error in messages collect", e)
                }
            }
        }
    }

    private fun messagesToUiModels(messages: List<MessageEntity>): List<MessageUiModel> {
        // Список идёт ASC сначала, потом разворачиваем для reverseLayout=true
        val ascResult = mutableListOf<MessageUiModel>()
        var lastDate: LocalDate? = null

        val sorted = messages.sortedBy { it.sortKey }
        val byServerId = sorted.mapNotNull { entity -> entity.id?.let { it to entity } }.toMap()
        val albumGroups = sorted
            .asSequence()
            .filter { it.albumId != null && (it.type == "image" || it.type == "text") }
            .groupBy { it.albumId!! }
        val consumedAlbumIds = mutableSetOf<String>()
        var index = 0

        while (index < sorted.size) {
            val msg = sorted[index]
            val date = Instant.ofEpochMilli(msg.createdAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
            if (date != lastDate) {
                ascResult.add(MessageUiModel.DateSeparator(date))
                lastDate = date
            }

            val albumId = msg.albumId
            if (albumId != null && (msg.type == "image" || msg.type == "text")) {
                if (albumId in consumedAlbumIds) {
                    index++
                    continue
                }
                consumedAlbumIds.add(albumId)

                val albumMembers = albumGroups[albumId].orEmpty()

                val imageMembers = albumMembers.filter { it.type == "image" }
                if (imageMembers.isEmpty()) {
                    val text = albumMembers.first()
                    ascResult.add(
                        MessageUiModel.Message(
                            localId = text.clientId ?: text.id.toString(),
                            serverId = text.id,
                            type = text.type,
                            content = text.content,
                            albumId = text.albumId,
                            isOutgoing = text.senderId == currentUserId,
                            timestamp = Instant.ofEpochMilli(text.createdAt),
                            status = text.status,
                            isDeleted = text.isDeleted
                        )
                    )
                    index++
                    continue
                }

                val items = imageMembers.map { item ->
                    MessageUiModel.Message(
                        localId = item.clientId ?: item.id.toString(),
                        serverId = item.id,
                        type = item.type,
                        content = item.content,
                        albumId = item.albumId,
                        isOutgoing = item.senderId == currentUserId,
                        timestamp = Instant.ofEpochMilli(item.createdAt),
                        status = item.status,
                        replyTo = item.replyToMsgId?.let { byServerId[it]?.toReplyMessage() },
                        isDeleted = item.isDeleted
                    )
                }
                val caption = albumMembers.firstOrNull { it.type == "text" }?.content?.takeIf { it.isNotBlank() }

                ascResult.add(
                    MessageUiModel.Album(
                        localId = "album_$albumId",
                        serverIds = items.map { it.serverId },
                        items = items,
                        caption = caption,
                        isOutgoing = msg.senderId == currentUserId,
                        timestamp = Instant.ofEpochMilli(albumMembers.first().createdAt),
                        status = albumMembers.maxByOrNull { it.sortKey }?.status ?: msg.status
                    )
                )
                index++
                continue
            }

            ascResult.add(
                MessageUiModel.Message(
                    localId = msg.clientId ?: msg.id.toString(),
                    serverId = msg.id,
                    type = msg.type,
                    content = msg.content,
                    albumId = msg.albumId,
                    isOutgoing = msg.senderId == currentUserId,
                    timestamp = Instant.ofEpochMilli(msg.createdAt),
                    status = msg.status,
                    replyTo = msg.replyToMsgId?.let { byServerId[it]?.toReplyMessage() },
                    isDeleted = msg.isDeleted
                )
            )
            index++
        }

        return ascResult.reversed()
    }

    private fun MessageEntity.toReplyMessage(): MessageUiModel.Message {
        return MessageUiModel.Message(
            localId = clientId ?: id.toString(),
            serverId = id,
            type = type,
            content = content,
            albumId = albumId,
            isOutgoing = senderId == currentUserId,
            timestamp = Instant.ofEpochMilli(createdAt),
            status = status,
            isDeleted = isDeleted
        )
    }

    fun onTextChanged(text: String) {
        _uiState.update { it.copy(inputText = text) }
        viewModelScope.launch {
            webSocketManager.sendTyping(chatId, text.isNotEmpty())
        }
    }

    fun onSendTapped() {
        val text = _uiState.value.inputText
        val images = _uiState.value.selectedImageUris
        if (text.isBlank() && images.isEmpty()) return

        viewModelScope.launch {
            val editing = _uiState.value.editingMessage
            if (editing != null && images.isEmpty()) {
                val serverId = editing.serverId ?: return@launch
                messageRepository.editMessage(serverId, text.trim())
                    .onFailure { e -> _uiState.update { it.copy(error = "Edit: ${e.message}") } }
                _uiState.update { it.copy(inputText = "", editingMessage = null, replyTo = null) }
                return@launch
            }

            if (images.isNotEmpty()) {
                val albumId = UUID.randomUUID().toString()
                val replyId = _uiState.value.replyTo?.serverId
                _uiState.update {
                    it.copy(
                        inputText = "",
                        selectedImageUris = emptyList(),
                        replyTo = null,
                        isSendingAttachments = true,
                        uploadingAlbumId = albumId,
                        uploadProgress = 0
                    )
                }
                messageRepository.sendImagesInBackground(
                        chatId = chatId,
                        uris = images,
                        caption = text.trim().takeIf { it.isNotBlank() },
                        albumId = albumId,
                        replyToMsgId = replyId,
                        onFailure = { e ->
                            _uiState.update { it.copy(error = "Image send: ${e.message}") }
                        }
                )
            } else {
                val replyId = _uiState.value.replyTo?.serverId
                _uiState.update {
                    it.copy(
                        inputText = "",
                        replyTo = null
                    )
                }
                messageRepository.sendMessage(chatId, "text", text, replyToMsgId = replyId)
                    .onFailure { e ->
                        android.util.Log.e("ChatViewModel", "Failed to send message", e)
                    }
            }
        }
    }

    fun onImagesPicked(uris: List<Uri>) {
        if (uris.isEmpty()) return
        _uiState.update { state ->
            val merged = (state.selectedImageUris + uris).distinct().take(10)
            state.copy(selectedImageUris = merged)
        }
    }

    fun onRemoveSelectedImage(uri: Uri) {
        _uiState.update { state ->
            state.copy(selectedImageUris = state.selectedImageUris.filterNot { it == uri })
        }
    }

    fun onClearSelectedImages() {
        _uiState.update { it.copy(selectedImageUris = emptyList()) }
    }

    fun onCancelAttachmentUpload() {
        val albumId = _uiState.value.uploadingAlbumId ?: return
        messageRepository.cancelAlbumUpload(albumId)
        _uiState.update {
            it.copy(isSendingAttachments = false, uploadingAlbumId = null, uploadProgress = 0)
        }
    }

    fun onCancelAlbumUpload(albumId: String) {
        messageRepository.cancelAlbumUpload(albumId)
        _uiState.update { state ->
            if (state.uploadingAlbumId == albumId) {
                state.copy(isSendingAttachments = false, uploadingAlbumId = null, uploadProgress = 0)
            } else {
                state
            }
        }
    }

    fun onSendImages(uris: List<Uri>) {
        onImagesPicked(uris)
    }

    fun onVoiceRecordStart() {
        try {
            val outFile = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
            recordingFile = outFile
            val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
            rec.setAudioSource(MediaRecorder.AudioSource.MIC)
            rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            rec.setOutputFile(outFile.absolutePath)
            rec.prepare()
            rec.start()
            mediaRecorder = rec
            _uiState.update { it.copy(isRecordingVoice = true) }
        } catch (e: Exception) {
            _uiState.update { it.copy(error = "Mic error: ${e.message}", isRecordingVoice = false) }
        }
    }

    fun onVoiceRecordStop() {
        val rec = mediaRecorder
        val file = recordingFile
        mediaRecorder = null
        recordingFile = null
        _uiState.update { it.copy(isRecordingVoice = false) }
        try {
            rec?.stop()
            rec?.release()
        } catch (_: Exception) {}
        if (file != null && file.exists() && file.length() > 0) {
            viewModelScope.launch {
                messageRepository.sendVoice(chatId, file)
            }
        }
    }

    fun onVoiceRecordCancel() {
        try { mediaRecorder?.stop() } catch (_: Exception) {}
        mediaRecorder?.release()
        mediaRecorder = null
        recordingFile?.delete()
        recordingFile = null
        _uiState.update { it.copy(isRecordingVoice = false) }
    }

    fun onCircleVideoRecorded(file: File) {
        if (!file.exists() || file.length() == 0L) {
            file.delete()
            return
        }
        circleSendJob?.cancel()
        circleSendJob = viewModelScope.launch {
            _uiState.update { it.copy(circleUploadProgress = 0) }
            messageRepository.sendCircle(chatId, file) { progress ->
                _uiState.update { state -> state.copy(circleUploadProgress = progress) }
            }
                .onSuccess {
                    _uiState.update { state -> state.copy(circleUploadProgress = null) }
                }
                .onFailure { e ->
                    if (e is CancellationException) {
                        _uiState.update { it.copy(circleUploadProgress = null) }
                        return@onFailure
                    }
                    file.delete()
                    _uiState.update { it.copy(error = "Circle video: ${e.message}", circleUploadProgress = null) }
                }
        }
    }

    fun onCancelCircleUpload(message: MessageUiModel.Message) {
        if (message.type != "circle" || message.status != "uploading") return
        circleSendJob?.cancel()
        viewModelScope.launch {
            messageRepository.cancelPendingMessage(message.localId)
            _uiState.update { it.copy(circleUploadProgress = null) }
        }
    }

    fun onSendImage(uri: Uri) {
        onSendImages(listOf(uri))
    }

    fun onAttachTapped() {
        // обрабатывается в ChatScreen (фото-пикером)
    }

    fun onMessageLongPress(message: MessageUiModel.Message) {
        _uiState.update { it.copy(actionMessage = message, actionAlbum = null) }
    }

    fun onAlbumLongPress(album: MessageUiModel.Album) {
        _uiState.update { it.copy(actionAlbum = album, actionMessage = null) }
    }

    fun onDismissMessageActions() {
        _uiState.update { it.copy(actionMessage = null, actionAlbum = null) }
    }

    fun onReplyTap(message: MessageUiModel.Message) {
        _uiState.update { it.copy(replyTo = message, actionMessage = null, actionAlbum = null) }
    }

    fun onReplyToAlbum(album: MessageUiModel.Album) {
        val target = album.items.firstOrNull() ?: return
        _uiState.update { it.copy(replyTo = target, actionMessage = null, actionAlbum = null) }
    }

    fun onCancelReply() {
        _uiState.update { it.copy(replyTo = null) }
    }

    fun onEditMessage(message: MessageUiModel.Message) {
        if (message.type != "text" || message.serverId == null) return
        _uiState.update {
            it.copy(
                inputText = message.content,
                editingMessage = message,
                replyTo = null,
                actionMessage = null,
                actionAlbum = null
            )
        }
    }

    fun onCancelEdit() {
        _uiState.update { it.copy(editingMessage = null, inputText = "") }
    }

    fun onDeleteMessage(message: MessageUiModel.Message) {
        val serverId = message.serverId ?: return
        _uiState.update { it.copy(actionMessage = null, actionAlbum = null) }
        viewModelScope.launch {
            messageRepository.deleteMessage(serverId)
                .onFailure { e -> _uiState.update { it.copy(error = "Delete: ${e.message}") } }
        }
    }

    fun onDeleteAlbum(album: MessageUiModel.Album) {
        val serverIds = album.items.mapNotNull { it.serverId }
        if (serverIds.isEmpty()) return
        _uiState.update { it.copy(actionMessage = null, actionAlbum = null) }
        viewModelScope.launch {
            serverIds.forEach { serverId ->
                messageRepository.deleteMessage(serverId)
                    .onFailure { e -> _uiState.update { it.copy(error = "Delete: ${e.message}") } }
            }
        }
    }

    fun onForwardMessage(message: MessageUiModel.Message) {
        val serverId = message.serverId ?: return
        viewModelScope.launch {
            val userId = currentUserId.takeIf { it > 0L } ?: tokenDataStore.userId.first() ?: 0L
            val targets = chatRepository.observeChats(userId).first().map { chat ->
                ForwardTargetUi(
                    chatId = chat.id,
                    title = chat.title ?: chat.otherUserName ?: "Chat ${chat.id}"
                )
            }
            _uiState.update {
                it.copy(
                    actionMessage = null,
                    actionAlbum = null,
                    forwardMessage = message.copy(serverId = serverId),
                    forwardTargets = targets
                )
            }
        }
    }

    fun onDismissForwardPicker() {
        _uiState.update { it.copy(forwardMessage = null, forwardTargets = emptyList()) }
    }

    fun onForwardToChat(targetChatId: Long) {
        val message = _uiState.value.forwardMessage ?: return
        val serverId = message.serverId ?: return
        viewModelScope.launch {
            messageRepository.forwardMessage(serverId, targetChatId)
                .onFailure { e -> _uiState.update { it.copy(error = "Forward: ${e.message}") } }
            _uiState.update { it.copy(forwardMessage = null, forwardTargets = emptyList()) }
        }
    }

    fun highlightMessage(localId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(highlightedMessageLocalId = localId) }
            kotlinx.coroutines.delay(2_000)
            _uiState.update { state ->
                if (state.highlightedMessageLocalId == localId) {
                    state.copy(highlightedMessageLocalId = null)
                } else {
                    state
                }
            }
        }
    }

    fun onLoadMore() {
        if (_uiState.value.isLoadingMore || !_uiState.value.hasMore) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            val oldestSortKey = _uiState.value.messages
                .mapNotNull { item ->
                    when (item) {
                        is MessageUiModel.Message -> item.timestamp.toEpochMilli()
                        is MessageUiModel.Album -> item.timestamp.toEpochMilli()
                        else -> null
                    }
                }
                .minOrNull()

            messageRepository.fetchMessages(chatId, oldestSortKey)
                .onSuccess { messages ->
                    _uiState.update {
                        it.copy(
                            isLoadingMore = false,
                            hasMore = messages.isNotEmpty()
                        )
                    }
                }
                .onFailure {
                    _uiState.update { it.copy(isLoadingMore = false) }
                }
        }
    }

    fun onErrorDismissed() {
        _uiState.update { it.copy(error = null) }
    }

    private fun loadChatInfo() {
        viewModelScope.launch {
            val currentUserId = tokenDataStore.userId.first() ?: 0L
            
            // 1. Load local chat info instantly
            chatRepository.getChat(chatId).onSuccess { chat ->
                chat?.let {
                    if (it.type == "private") {
                        chatRepository.getChatPartner(chatId, currentUserId).onSuccess { partner ->
                            partner?.let { u ->
                                _uiState.update { state ->
                                    val resolvedTitle = if (!u.displayName.isNullOrBlank() && u.displayName != "Unknown") {
                                        u.displayName
                                    } else {
                                        u.username?.let { "@$it" } ?: "User ${u.id}"
                                    }
                                    state.copy(
                                        chatTitle = resolvedTitle,
                                        chatAvatar = u.avatarMediaId?.toString(),
                                        isOnline = u.status == "online",
                                        peerUserId = u.id
                                    )
                                }
                            }
                        }
                    } else {
                        _uiState.update { state ->
                            state.copy(
                                chatTitle = it.title ?: "Group",
                                chatAvatar = it.avatarMediaId?.toString(),
                                isOnline = false,
                                peerUserId = null
                            )
                        }
                    }
                }
            }

            // 2. Fetch chat details in background
            launch {
                runCatching { chatRepository.fetchChatDetails(chatId) }
                    .onSuccess {
                        chatRepository.getChat(chatId).onSuccess { chat ->
                            chat?.let {
                                if (it.type == "private") {
                                    chatRepository.getChatPartner(chatId, currentUserId).onSuccess { partner ->
                                        partner?.let { u ->
                                            _uiState.update { state ->
                                                val resolvedTitle = if (!u.displayName.isNullOrBlank() && u.displayName != "Unknown") {
                                                    u.displayName
                                                } else {
                                                    u.username?.let { "@$it" } ?: "User ${u.id}"
                                                }
                                                state.copy(
                                                    chatTitle = resolvedTitle,
                                                    chatAvatar = u.avatarMediaId?.toString(),
                                                    isOnline = u.status == "online",
                                                    peerUserId = u.id
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
            }

            // 3. Fetch messages in background
            launch {
                runCatching { messageRepository.fetchMessages(chatId) }
            }
        }
    }

    private fun observeChatPartnerInfo() {
        viewModelScope.launch {
            val userId = tokenDataStore.userId.first() ?: return@launch
            chatRepository.observeChatPartner(chatId, userId).collect { partner ->
                partner ?: return@collect
                _uiState.update { state ->
                    val resolvedTitle = if (!partner.displayName.isNullOrBlank() && partner.displayName != "Unknown") {
                        partner.displayName
                    } else {
                        partner.username?.let { "@$it" } ?: "User ${partner.id}"
                    }
                    state.copy(
                        chatTitle = resolvedTitle,
                        chatAvatar = partner.avatarMediaId?.toString(),
                        isOnline = partner.status == "online",
                        peerUserId = partner.id
                    )
                }
            }
        }
    }

    private var typingAutoStopJob: kotlinx.coroutines.Job? = null

    private fun observeWebSocketEvents() {
        viewModelScope.launch {
            webSocketManager.events.collect { event ->
                try {
                    when (event) {
                        is WebSocketEvent.TypingStart -> {
                            if (event.chatId == chatId && event.userId != currentUserId) {
                                _uiState.update { it.copy(otherUserTyping = true) }
                                typingAutoStopJob?.cancel()
                                typingAutoStopJob = viewModelScope.launch {
                                    kotlinx.coroutines.delay(5_000)
                                    _uiState.update { it.copy(otherUserTyping = false) }
                                }
                            }
                        }
                        is WebSocketEvent.TypingStop -> {
                            if (event.chatId == chatId) {
                                typingAutoStopJob?.cancel()
                                _uiState.update { it.copy(otherUserTyping = false) }
                            }
                        }
                        is WebSocketEvent.PresenceOnline -> {
                            if (event.userId == _uiState.value.peerUserId) {
                                _uiState.update { it.copy(isOnline = true) }
                            }
                        }
                        is WebSocketEvent.PresenceOffline -> {
                            if (event.userId == _uiState.value.peerUserId) {
                                _uiState.update { it.copy(isOnline = false) }
                            }
                        }
                        else -> {}
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ChatViewModel", "Error in WebSocket event collection", e)
                }
            }
        }
    }
}
