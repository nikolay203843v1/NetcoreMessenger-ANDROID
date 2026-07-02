package com.netcoremessenger.feature.auth.onboarding

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.netcoremessenger.core.data.repository.AuthRepository
import com.netcoremessenger.core.util.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class UsernameStatus { IDLE, CHECKING, AVAILABLE, TAKEN, INVALID_FORMAT }
enum class AuthStep { SIGN_IN, PROFILE_SETUP, DONE }

data class OnboardingUiState(
    val step: AuthStep = AuthStep.SIGN_IN,
    val avatarUri: Uri? = null,
    val existingAvatarMediaId: Long? = null,
    val displayName: String = "",
    val username: String = "",
    val bio: String = "",
    val usernameStatus: UsernameStatus = UsernameStatus.IDLE,
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            if (!authRepository.restoreSession()) {
                _uiState.update { it.copy(isLoading = false, step = AuthStep.SIGN_IN) }
                return@launch
            }
            // Уже залогинены — пробуем подтянуть профиль
            authRepository.getMe()
                .onSuccess { user ->
                    val hasProfile = !user.displayName.isNullOrBlank() && user.displayName != "Unknown"
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            displayName = user.displayName ?: "",
                            username = user.username ?: "",
                            bio = user.bio ?: "",
                            existingAvatarMediaId = user.avatarMediaId,
                            step = if (hasProfile) AuthStep.DONE else AuthStep.PROFILE_SETUP
                        )
                    }
                    if (hasProfile) {
                        authRepository.connectWebSocket()
                    }
                }
                .onFailure {
                    _uiState.update { it.copy(isLoading = false, step = AuthStep.SIGN_IN) }
                }
        }
    }

    fun googleLogin(idToken: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            authRepository.googleLogin(idToken)
                .onSuccess {
                    authRepository.getMe()
                        .onSuccess { user ->
                            // Считаем профиль заполненным, если есть username
                            // (display_name сервер сам выставляет из Firebase при создании)
                            val hasProfile = !user.displayName.isNullOrBlank() && user.displayName != "Unknown"
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    displayName = user.displayName ?: "",
                                    username = user.username ?: "",
                                    bio = user.bio ?: "",
                                    existingAvatarMediaId = user.avatarMediaId,
                                    step = if (hasProfile) AuthStep.DONE else AuthStep.PROFILE_SETUP
                                )
                            }
                            if (hasProfile) {
                                authRepository.connectWebSocket()
                            }
                        }
                        .onFailure {
                            _uiState.update { it.copy(isLoading = false, step = AuthStep.PROFILE_SETUP) }
                        }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = e.message ?: "Google sign-in failed"
                        )
                    }
                }
        }
    }

    fun onDisplayNameChanged(name: String) {
        if (name.length <= Constants.MAX_DISPLAY_NAME_LENGTH) {
            _uiState.update { it.copy(displayName = name, error = null) }
        }
    }

    fun onUsernameChanged(username: String) {
        val sanitized = username.replace(Regex("[^a-zA-Z0-9_]"), "").lowercase()
        if (sanitized.length <= Constants.MAX_USERNAME_LENGTH) {
            _uiState.update { it.copy(username = sanitized, error = null, usernameStatus = UsernameStatus.IDLE) }
            if (sanitized.length >= 3) {
                checkUsername(sanitized)
            }
        }
    }

    fun onBioChanged(bio: String) {
        if (bio.length <= Constants.MAX_BIO_LENGTH) {
            _uiState.update { it.copy(bio = bio, error = null) }
        }
    }

    fun onAvatarSelected(uri: Uri?) {
        _uiState.update { it.copy(avatarUri = uri) }
    }

    private fun checkUsername(username: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(usernameStatus = UsernameStatus.CHECKING) }
            authRepository.checkUsername(username)
                .onSuccess { available ->
                    _uiState.update {
                        it.copy(
                            usernameStatus = if (available) UsernameStatus.AVAILABLE else UsernameStatus.TAKEN
                        )
                    }
                }
                .onFailure {
                    _uiState.update { it.copy(usernameStatus = UsernameStatus.IDLE) }
                }
        }
    }

    fun onStartMessaging() {
        val state = _uiState.value
        if (state.displayName.isBlank()) {
            _uiState.update { it.copy(error = "Display name is required") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            // 1) Если выбрали новое фото — заливаем
            var avatarMediaId: Long? = state.existingAvatarMediaId
            state.avatarUri?.let { uri ->
                val res = authRepository.uploadAvatar(uri)
                res.onSuccess { avatarMediaId = it.id }
                    .onFailure { e ->
                        _uiState.update {
                            it.copy(isLoading = false, error = "Avatar upload failed: ${e.message}")
                        }
                        return@launch
                    }
            }

            // 2) Обновляем профиль
            authRepository.updateProfile(
                displayName = state.displayName,
                username = state.username.ifEmpty { null },
                bio = state.bio.ifEmpty { null },
                avatarMediaId = avatarMediaId
            )
                .onSuccess {
                    authRepository.connectWebSocket()
                    _uiState.update { it.copy(isLoading = false, step = AuthStep.DONE) }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = e.message ?: "Failed to update profile"
                        )
                    }
                }
        }
    }

    fun onSkip() {
        // Профиль уже создан firebase-логином — просто завершаем онбординг
        viewModelScope.launch {
            authRepository.connectWebSocket()
        }
        _uiState.update { it.copy(step = AuthStep.DONE) }
    }

    fun onGoogleSignInError(message: String) {
        _uiState.update { it.copy(isLoading = false, error = message) }
    }

    fun onErrorDismissed() {
        _uiState.update { it.copy(error = null) }
    }
}
