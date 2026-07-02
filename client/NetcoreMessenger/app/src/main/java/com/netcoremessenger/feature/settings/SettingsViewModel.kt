package com.netcoremessenger.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.netcoremessenger.core.data.repository.AuthRepository
import com.netcoremessenger.core.data.store.TokenDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val displayName: String = "",
    val username: String = "",
    val phoneNumber: String = "",
    val avatarUrl: String? = null,
    val themeMode: String = "system",
    val accentStyle: String = "indigo",
    val fontStyle: String = "default",
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val tokenDataStore: TokenDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadProfile()
        observeThemeMode()
        observeAccentStyle()
        observeFontStyle()
    }

    fun loadProfile() {
        viewModelScope.launch {
            // 1. Try to load cached profile first
            val cached = authRepository.getCachedMe()
            if (cached != null) {
                val avatarUrl = cached.avatarMediaId?.let {
                    "${com.netcoremessenger.core.util.Constants.BASE_URL}/api/v1/media/$it"
                }
                _uiState.update {
                    it.copy(
                        displayName = cached.displayName ?: "User",
                        username = cached.username?.removePrefix("@") ?: "",
                        phoneNumber = cached.phone ?: "",
                        avatarUrl = avatarUrl,
                        isLoading = false
                    )
                }
            } else {
                _uiState.update { it.copy(isLoading = true) }
            }

            // 2. Fetch fresh profile from network
            authRepository.getMe()
                .onSuccess { userDto ->
                    val avatarUrl = userDto.avatarMediaId?.let {
                        "${com.netcoremessenger.core.util.Constants.BASE_URL}/api/v1/media/$it"
                    }
                    _uiState.update {
                        it.copy(
                            displayName = userDto.displayName ?: "User",
                            username = userDto.username?.removePrefix("@") ?: "",
                            phoneNumber = userDto.phone ?: "",
                            avatarUrl = avatarUrl,
                            isLoading = false
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = error.localizedMessage
                        )
                    }
                }
        }
    }

    private fun observeThemeMode() {
        viewModelScope.launch {
            tokenDataStore.themeMode.collect { mode ->
                _uiState.update { it.copy(themeMode = mode) }
            }
        }
    }

    private fun observeAccentStyle() {
        viewModelScope.launch {
            tokenDataStore.accentStyle.collect { style ->
                _uiState.update { it.copy(accentStyle = style) }
            }
        }
    }

    private fun observeFontStyle() {
        viewModelScope.launch {
            tokenDataStore.fontStyle.collect { style ->
                _uiState.update { it.copy(fontStyle = style) }
            }
        }
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch {
            tokenDataStore.saveThemeMode(mode)
        }
    }

    fun setAccentStyle(style: String) {
        viewModelScope.launch {
            tokenDataStore.saveAccentStyle(style)
        }
    }

    fun setFontStyle(style: String) {
        viewModelScope.launch {
            tokenDataStore.saveFontStyle(style)
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
        }
    }

    fun onErrorDismissed() {
        _uiState.update { it.copy(error = null) }
    }
}
