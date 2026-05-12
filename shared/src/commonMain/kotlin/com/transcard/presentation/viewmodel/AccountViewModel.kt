package com.transcard.presentation.viewmodel

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.transcard.domain.repository.AuthRepository
import com.transcard.domain.repository.SnapshotHistoryEntry
import com.transcard.domain.repository.SyncRepository
import com.transcard.domain.sync.AuthState
import com.transcard.domain.sync.SyncStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HistoryUiState(
    val isLoading: Boolean = false,
    val items: List<SnapshotHistoryEntry> = emptyList(),
    val error: String? = null,
)

class AccountViewModel(
    private val authRepository: AuthRepository,
    private val syncRepository: SyncRepository,
) : ScreenModel {

    val authState: StateFlow<AuthState> = authRepository.observeAuthState()
        .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5_000), AuthState.Anonymous)

    val syncStatus: StateFlow<SyncStatus> = syncRepository.observeStatus()
        .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5_000), SyncStatus.NotAuthenticated)

    private val _history = MutableStateFlow(HistoryUiState())
    val history: StateFlow<HistoryUiState> = _history.asStateFlow()

    fun signOut() {
        screenModelScope.launch { authRepository.logout() }
    }

    fun syncNow() {
        screenModelScope.launch { syncRepository.pushNow() }
    }

    fun loadHistory() {
        _history.value = HistoryUiState(isLoading = true)
        screenModelScope.launch {
            syncRepository.fetchHistory()
                .onSuccess { _history.value = HistoryUiState(isLoading = false, items = it) }
                .onFailure { _history.value = HistoryUiState(isLoading = false, error = it.message ?: "Не удалось загрузить") }
        }
    }

    fun restoreSnapshot(snapshotId: String, onDone: () -> Unit) {
        screenModelScope.launch {
            syncRepository.restoreSnapshot(snapshotId)
                .onSuccess { onDone() }
        }
    }
}
