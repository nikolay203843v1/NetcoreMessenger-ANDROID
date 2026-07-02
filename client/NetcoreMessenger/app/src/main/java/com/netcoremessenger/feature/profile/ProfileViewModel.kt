package com.netcoremessenger.feature.profile

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.netcoremessenger.core.data.remote.dto.ProfilePhotoDto
import com.netcoremessenger.core.data.remote.api.UsersApi
import com.netcoremessenger.core.data.remote.dto.UserDto
import com.netcoremessenger.core.data.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val user: UserDto? = null,
    val profilePhotos: List<ProfilePhotoDto> = emptyList(),
    val isLoading: Boolean = true,
    val openingChat: Boolean = false,
    val error: String? = null,
    val openedChatId: Long? = null
) {
    val photoMediaIds: List<Long>
        get() = profilePhotos.map { it.mediaId }
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val usersApi: UsersApi,
    private val chatRepository: ChatRepository,
    handle: SavedStateHandle
) : ViewModel() {

    private val userId: Long = handle["userId"]!!

    private val _ui = MutableStateFlow(ProfileUiState())
    val ui: StateFlow<ProfileUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { usersApi.getUser(userId) }
                .onSuccess { u ->
                    val photos = runCatching { usersApi.getUserProfilePhotos(userId) }.getOrDefault(emptyList())
                    val resolvedPhotos = photos.ifEmpty {
                        u.avatarMediaId?.let {
                            listOf(ProfilePhotoDto(id = -it, mediaId = it, position = 0, createdAt = null))
                        } ?: emptyList()
                    }
                    _ui.update { it.copy(user = u, profilePhotos = resolvedPhotos, isLoading = false) }
                }
                .onFailure { e -> _ui.update { it.copy(isLoading = false, error = e.message) } }
        }
    }

    fun openChat() {
        val u = _ui.value.user ?: return
        viewModelScope.launch {
            _ui.update { it.copy(openingChat = true) }
            chatRepository.openPrivateChat(u.id)
                .onSuccess { id -> _ui.update { it.copy(openingChat = false, openedChatId = id) } }
                .onFailure { e -> _ui.update { it.copy(openingChat = false, error = e.message) } }
        }
    }
}
