package com.netcoremessenger.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.netcoremessenger.core.data.remote.dto.ProfilePhotoDto
import com.netcoremessenger.core.data.remote.dto.UserDto
import com.netcoremessenger.core.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MyProfileUiState(
    val user: UserDto? = null,
    val displayName: String = "",
    val username: String = "",
    val bio: String = "",
    val profilePhotos: List<ProfilePhotoDto> = emptyList(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val error: String? = null
) {
    val photoMediaIds: List<Long>
        get() = profilePhotos.map { it.mediaId }

    val isDirty: Boolean
        get() = user != null && (
            displayName != (user.displayName ?: "") ||
            username != (user.username ?: "") ||
            bio != (user.bio ?: "")
        )
}

@HiltViewModel
class MyProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _ui = MutableStateFlow(MyProfileUiState())
    val ui: StateFlow<MyProfileUiState> = _ui.asStateFlow()

    init { reload() }

    fun reload() {
        viewModelScope.launch {
            _ui.update { it.copy(isLoading = true) }
            authRepository.getMe()
                .onSuccess { user ->
                    val photos = authRepository.getMyProfilePhotos().getOrDefault(emptyList())
                    val resolvedPhotos = photos.ifEmpty {
                        user.avatarMediaId?.let {
                            listOf(ProfilePhotoDto(id = -it, mediaId = it, position = 0, createdAt = null))
                        } ?: emptyList()
                    }
                    _ui.update {
                        it.copy(
                            user = user,
                            displayName = user.displayName ?: "",
                            username = user.username ?: "",
                            bio = user.bio ?: "",
                            profilePhotos = resolvedPhotos,
                            isLoading = false,
                            isSaving = false
                        )
                    }
                }
                .onFailure { e -> _ui.update { it.copy(isLoading = false, error = e.message) } }
        }
    }

    fun onNameChanged(name: String) = _ui.update { it.copy(displayName = name.take(64), error = null) }
    fun onUsernameChanged(value: String) {
        val cleaned = value.replace(Regex("[^a-zA-Z0-9_]"), "").lowercase().take(32)
        _ui.update { it.copy(username = cleaned, error = null) }
    }
    fun onBioChanged(value: String) = _ui.update { it.copy(bio = value.take(512), error = null) }

    fun saveProfile() {
        val state = _ui.value
        if (state.displayName.isBlank()) {
            _ui.update { it.copy(error = "Укажите имя") }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(isSaving = true, error = null) }
            authRepository.updateProfile(
                displayName = state.displayName.trim(),
                username = state.username.ifBlank { null },
                bio = state.bio.ifBlank { null },
                avatarMediaId = state.user?.avatarMediaId
            ).onSuccess {
                reload()
            }.onFailure { e ->
                _ui.update { it.copy(isSaving = false, error = e.message) }
            }
        }
    }

    fun updateAvatar(uri: android.net.Uri) {
        viewModelScope.launch {
            _ui.update { it.copy(isSaving = true, error = null) }
            authRepository.uploadAvatar(uri)
                .onSuccess { mediaDto ->
                    authRepository.addProfilePhoto(mediaDto.id).onFailure { e ->
                        _ui.update { it.copy(isSaving = false, error = e.message ?: "Не удалось добавить фото") }
                        return@onSuccess
                    }
                    val state = _ui.value
                    authRepository.updateProfile(
                        displayName = state.displayName.trim().ifBlank { state.user?.displayName },
                        username = state.username.ifBlank { null },
                        bio = state.bio.ifBlank { null },
                        avatarMediaId = mediaDto.id
                    ).onSuccess {
                        reload()
                    }.onFailure { e ->
                        _ui.update { it.copy(isSaving = false, error = e.message) }
                    }
                }
                .onFailure { e ->
                    _ui.update { it.copy(isSaving = false, error = e.message) }
                }
        }
    }

    fun deletePhoto(photoId: Long) {
        if (photoId <= 0L) {
            _ui.update { it.copy(error = "Это фото еще не синхронизировано") }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(isSaving = true, error = null) }
            authRepository.deleteProfilePhoto(photoId)
                .onSuccess {
                    reload()
                }
                .onFailure { e ->
                    _ui.update { it.copy(isSaving = false, error = e.message ?: "Не удалось удалить фото") }
                }
        }
    }
}
