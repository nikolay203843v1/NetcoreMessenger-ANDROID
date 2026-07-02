package com.netcoremessenger.feature.group

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.netcoremessenger.core.data.remote.api.ChatsApi
import com.netcoremessenger.core.data.remote.dto.ChatDto
import com.netcoremessenger.core.data.remote.dto.UserDto
import com.netcoremessenger.core.data.repository.AuthRepository
import com.netcoremessenger.core.data.repository.ChatRepository
import com.netcoremessenger.core.data.store.TokenDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GroupSettingsUiState(
    val chat: ChatDto? = null,
    val title: String = "",
    val contacts: List<UserDto> = emptyList(),
    val selectedToAdd: List<UserDto> = emptyList(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val error: String? = null
) {
    val participantIds: Set<Long>
        get() = chat?.participants?.map { it.userId }?.toSet().orEmpty()

    val canSave: Boolean
        get() = chat != null && title.trim() != (chat.title ?: "")
}

@HiltViewModel
class GroupSettingsViewModel @Inject constructor(
    private val chatsApi: ChatsApi,
    private val chatRepository: ChatRepository,
    private val authRepository: AuthRepository,
    private val tokenDataStore: TokenDataStore,
    handle: SavedStateHandle
) : ViewModel() {
    private val chatId: Long = handle["chatId"]!!

    private val _ui = MutableStateFlow(GroupSettingsUiState())
    val ui: StateFlow<GroupSettingsUiState> = _ui.asStateFlow()

    init {
        load()
        observeContacts()
    }

    fun load() {
        viewModelScope.launch {
            _ui.update { it.copy(isLoading = true, error = null) }
            runCatching { chatsApi.getChat(chatId) }
                .onSuccess { chat ->
                    _ui.update {
                        it.copy(
                            chat = chat,
                            title = chat.title ?: "",
                            isLoading = false,
                            selectedToAdd = emptyList()
                        )
                    }
                }
                .onFailure { e -> _ui.update { it.copy(isLoading = false, error = e.message) } }
        }
    }

    private fun observeContacts() {
        viewModelScope.launch {
            val currentUserId = tokenDataStore.userId.first() ?: return@launch
            chatRepository.observeChats(currentUserId).collect { chats ->
                val participants = _ui.value.participantIds
                val contacts = chats
                    .filter { it.type == "private" && it.otherUserId != null && it.otherUserId !in participants }
                    .mapNotNull { chat ->
                        val userId = chat.otherUserId ?: return@mapNotNull null
                        UserDto(
                            id = userId,
                            phone = null,
                            googleId = null,
                            username = null,
                            displayName = chat.otherUserName.takeIf { !it.isNullOrBlank() && it != "Unknown" },
                            bio = null,
                            avatarMediaId = chat.otherUserAvatar,
                            status = chat.otherUserStatus,
                            lastOnlineAt = null
                        )
                    }
                    .distinctBy { it.id }
                    .sortedBy { it.displayName?.lowercase() ?: "" }
                _ui.update {
                    it.copy(
                        contacts = contacts,
                        selectedToAdd = it.selectedToAdd.filter { selected -> contacts.any { c -> c.id == selected.id } }
                    )
                }
            }
        }
    }

    fun onTitleChanged(value: String) {
        _ui.update { it.copy(title = value.take(128), error = null) }
    }

    fun toggleAdd(user: UserDto) {
        _ui.update {
            val selected = it.selectedToAdd.toMutableList()
            if (selected.any { u -> u.id == user.id }) selected.removeAll { u -> u.id == user.id }
            else selected.add(user)
            it.copy(selectedToAdd = selected, error = null)
        }
    }

    fun saveTitle() {
        val state = _ui.value
        if (state.title.isBlank()) {
            _ui.update { it.copy(error = "Укажите название группы") }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(isSaving = true, error = null) }
            chatRepository.updateGroup(chatId, title = state.title.trim())
                .onSuccess { load() }
                .onFailure { e -> _ui.update { it.copy(isSaving = false, error = e.message) } }
        }
    }

    fun updateAvatar(uri: Uri) {
        viewModelScope.launch {
            _ui.update { it.copy(isSaving = true, error = null) }
            authRepository.uploadAvatar(uri)
                .onSuccess { media ->
                    chatRepository.updateGroup(
                        chatId = chatId,
                        title = _ui.value.title.trim().ifBlank { _ui.value.chat?.title },
                        avatarMediaId = media.id
                    ).onSuccess { load() }
                        .onFailure { e -> _ui.update { it.copy(isSaving = false, error = e.message) } }
                }
                .onFailure { e -> _ui.update { it.copy(isSaving = false, error = e.message) } }
        }
    }

    fun addSelected() {
        val selected = _ui.value.selectedToAdd
        if (selected.isEmpty()) return
        viewModelScope.launch {
            _ui.update { it.copy(isSaving = true, error = null) }
            for (user in selected) {
                val result = chatRepository.addParticipant(chatId, user.id)
                if (result.isFailure) {
                    _ui.update { it.copy(isSaving = false, error = result.exceptionOrNull()?.message) }
                    return@launch
                }
            }
            load()
        }
    }
}
