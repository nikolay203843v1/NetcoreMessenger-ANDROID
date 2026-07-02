package com.netcoremessenger.feature.profile

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.netcoremessenger.core.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EditProfileUiState(
    val displayName: String = "",
    val username: String = "",
    val bio: String = "",
    val currentAvatarMediaId: Long? = null,
    val pickedAvatarUri: Uri? = null,
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class EditProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _ui = MutableStateFlow(EditProfileUiState(isLoading = true))
    val ui: StateFlow<EditProfileUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.getMe().onSuccess { user ->
                _ui.update {
                    it.copy(
                        isLoading = false,
                        displayName = user.displayName ?: "",
                        username = user.username ?: "",
                        bio = user.bio ?: "",
                        currentAvatarMediaId = user.avatarMediaId
                    )
                }
            }.onFailure { e -> _ui.update { it.copy(isLoading = false, error = e.message) } }
        }
    }

    fun onName(name: String) = _ui.update { it.copy(displayName = name.take(64)) }
    fun onUsername(name: String) {
        val s = name.replace(Regex("[^a-zA-Z0-9_]"), "").lowercase().take(32)
        _ui.update { it.copy(username = s) }
    }
    fun onBio(b: String) = _ui.update { it.copy(bio = b.take(512)) }
    fun onAvatar(uri: Uri) = _ui.update { it.copy(pickedAvatarUri = uri) }

    fun save() {
        val s = _ui.value
        if (s.displayName.isBlank()) {
            _ui.update { it.copy(error = "Укажите имя") }; return
        }
        viewModelScope.launch {
            _ui.update { it.copy(isLoading = true, error = null) }
            var avatarId = s.currentAvatarMediaId
            s.pickedAvatarUri?.let { uri ->
                authRepository.uploadAvatar(uri).onSuccess { avatarId = it.id }
                    .onFailure { e ->
                        _ui.update { it.copy(isLoading = false, error = "Не удалось загрузить фото: ${e.message}") }
                        return@launch
                    }
            }
            authRepository.updateProfile(
                displayName = s.displayName,
                username = s.username.ifEmpty { null },
                bio = s.bio.ifEmpty { null },
                avatarMediaId = avatarId
            ).onSuccess { _ui.update { it.copy(isLoading = false, isSaved = true) } }
             .onFailure { e -> _ui.update { it.copy(isLoading = false, error = e.message) } }
        }
    }
}
