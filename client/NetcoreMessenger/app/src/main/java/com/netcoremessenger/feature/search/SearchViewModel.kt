package com.netcoremessenger.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.netcoremessenger.core.data.remote.api.UsersApi
import com.netcoremessenger.core.data.remote.dto.UserDto
import com.netcoremessenger.core.data.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val isLoading: Boolean = false,
    val results: List<UserDto> = emptyList(),
    val error: String? = null,
    val openingChatForUserId: Long? = null
)

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val usersApi: UsersApi,
    private val chatRepository: ChatRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    fun onQueryChanged(q: String) {
        _uiState.update { it.copy(query = q, error = null) }
        searchJob?.cancel()
        if (q.isBlank()) {
            _uiState.update { it.copy(results = emptyList(), isLoading = false) }
            return
        }
        val cleaned = q.trim().removePrefix("@")
        if (cleaned.isEmpty()) {
            _uiState.update { it.copy(results = emptyList(), isLoading = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(300)
            _uiState.update { it.copy(isLoading = true) }
            runCatching { usersApi.searchUsers(cleaned) }
                .onSuccess { list ->
                    _uiState.update { it.copy(isLoading = false, results = list) }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(isLoading = false, error = e.message ?: "Search failed")
                    }
                }
        }
    }

    /**
     * Создаёт (или подтягивает существующий) приватный чат с пользователем.
     * Через onChatReady возвращает chatId — экран сам перейдёт.
     */
    fun openChatWith(user: UserDto, onChatReady: (Long) -> Unit) {
        _uiState.update { it.copy(openingChatForUserId = user.id) }
        viewModelScope.launch {
            chatRepository.openPrivateChat(user.id)
                .onSuccess { chatId ->
                    _uiState.update { it.copy(openingChatForUserId = null) }
                    onChatReady(chatId)
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(openingChatForUserId = null, error = e.message ?: "Failed to open chat")
                    }
                }
        }
    }
}
