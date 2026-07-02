package com.netcoremessenger.feature.group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.netcoremessenger.core.data.remote.dto.UserDto
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

data class CreateGroupUiState(
    val groupName: String = "",
    val contacts: List<UserDto> = emptyList(),
    val selected: List<UserDto> = emptyList(),
    val isLoadingContacts: Boolean = true,
    val isCreating: Boolean = false,
    val createdChatId: Long? = null,
    val error: String? = null
)

@HiltViewModel
class CreateGroupViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val tokenDataStore: TokenDataStore
) : ViewModel() {

    private val _ui = MutableStateFlow(CreateGroupUiState())
    val ui: StateFlow<CreateGroupUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            val currentUserId = tokenDataStore.userId.first() ?: return@launch
            chatRepository.fetchChats(currentUserId)
            chatRepository.observeChats(currentUserId).collect { chats ->
                val contacts = chats
                    .filter { it.type == "private" && it.otherUserId != null }
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
                        selected = it.selected.filter { selected -> contacts.any { c -> c.id == selected.id } },
                        isLoadingContacts = false
                    )
                }
            }
        }
    }

    fun toggleSelect(user: UserDto) {
        _ui.update {
            val sel = it.selected.toMutableList()
            if (sel.any { u -> u.id == user.id }) sel.removeAll { u -> u.id == user.id }
            else sel.add(user)
            it.copy(selected = sel)
        }
    }

    fun onGroupNameChanged(name: String) {
        if (name.length <= 64) _ui.update { it.copy(groupName = name) }
    }

    fun create() {
        val state = _ui.value
        if (state.groupName.isBlank()) {
            _ui.update { it.copy(error = "Укажите название группы") }
            return
        }
        if (state.selected.isEmpty()) {
            _ui.update { it.copy(error = "Выберите хотя бы одного участника") }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(isCreating = true, error = null) }
            chatRepository.createChat(
                participantIds = state.selected.map { it.id },
                title = state.groupName.trim()
            ).onSuccess { chatId ->
                _ui.update { it.copy(isCreating = false, createdChatId = chatId) }
            }.onFailure { e ->
                _ui.update { it.copy(isCreating = false, error = e.message ?: "Не удалось создать группу") }
            }
        }
    }
}
