package com.transcard.presentation.viewmodel

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.transcard.domain.repository.AuthRepository
import com.transcard.domain.repository.SnapshotHistoryEntry
import com.transcard.domain.repository.SyncRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Состояния пост-login-экрана.
 *
 *   Loading       → грузим историю снапшотов.
 *   Confirm       → у сервера есть свежий снапшот; ждём решения пользователя (3 кнопки).
 *   Restoring     → применяем серверный снапшот (заменяем локалку).
 *   LoggingOut    → отменили вход, чистим сессию.
 *   Error         → последняя сетевая операция упала; canRetry, latest для повторной попытки restore.
 *   Done          → пора закрыть экран.
 */
sealed interface PostLoginRestoreUiState {
    data object Loading : PostLoginRestoreUiState
    data class Confirm(val latest: SnapshotHistoryEntry) : PostLoginRestoreUiState
    data object Restoring : PostLoginRestoreUiState
    data object LoggingOut : PostLoginRestoreUiState
    data class Error(
        val message: String,
        val source: ErrorSource,
        val latest: SnapshotHistoryEntry?,
    ) : PostLoginRestoreUiState
    data object Done : PostLoginRestoreUiState
}

enum class ErrorSource { LoadHistory, Restore }

class PostLoginRestoreViewModel(
    private val authRepository: AuthRepository,
    private val syncRepository: SyncRepository,
) : ScreenModel {

    private val _state = MutableStateFlow<PostLoginRestoreUiState>(PostLoginRestoreUiState.Loading)
    val state: StateFlow<PostLoginRestoreUiState> = _state.asStateFlow()

    init {
        loadHistory()
    }

    private fun loadHistory() {
        _state.value = PostLoginRestoreUiState.Loading
        screenModelScope.launch {
            syncRepository.fetchHistory()
                .onSuccess { items ->
                    val latest = items.firstOrNull()
                    if (latest == null) {
                        // На сервере пусто — пометим локалку dirty, debounced push отправит её первым snapshot'ом.
                        syncRepository.markDirty()
                        _state.value = PostLoginRestoreUiState.Done
                    } else {
                        _state.value = PostLoginRestoreUiState.Confirm(latest)
                    }
                }
                .onFailure { e ->
                    _state.value = PostLoginRestoreUiState.Error(
                        message = e.message ?: "Не удалось получить данные с сервера",
                        source = ErrorSource.LoadHistory,
                        latest = null,
                    )
                }
        }
    }

    fun confirmRestore() {
        val latest = latestSnapshot() ?: return
        _state.value = PostLoginRestoreUiState.Restoring
        screenModelScope.launch {
            syncRepository.restoreSnapshot(latest.id)
                .onSuccess { _state.value = PostLoginRestoreUiState.Done }
                .onFailure { e ->
                    _state.value = PostLoginRestoreUiState.Error(
                        message = e.message ?: "Не удалось восстановить",
                        source = ErrorSource.Restore,
                        latest = latest,
                    )
                }
        }
    }

    /**
     * «Оставить локальные». Ставим pendingPush=1 — debounced push pошлёт локалку,
     * а если упадёт (offline), pullOnStart на следующем запуске сначала ретрайнет
     * push и не молча затрёт локалку pull'ом (см. SyncRepositoryImpl.pullOnStart).
     */
    fun keepLocal() {
        syncRepository.markDirty()
        _state.value = PostLoginRestoreUiState.Done
    }

    fun cancel() {
        _state.value = PostLoginRestoreUiState.LoggingOut
        screenModelScope.launch {
            authRepository.logout()
            _state.value = PostLoginRestoreUiState.Done
        }
    }

    fun retry() {
        val err = _state.value as? PostLoginRestoreUiState.Error ?: return
        when (err.source) {
            ErrorSource.LoadHistory -> loadHistory()
            ErrorSource.Restore -> {
                err.latest?.let { _state.value = PostLoginRestoreUiState.Confirm(it) }
                confirmRestore()
            }
        }
    }

    /** Вернуться к диалогу выбора из состояния Error.Restore. Для LoadHistory — отмена через cancel(). */
    fun dismissError() {
        val err = _state.value as? PostLoginRestoreUiState.Error ?: return
        _state.update {
            if (err.latest != null) PostLoginRestoreUiState.Confirm(err.latest) else err
        }
    }

    private fun latestSnapshot(): SnapshotHistoryEntry? = when (val s = _state.value) {
        is PostLoginRestoreUiState.Confirm -> s.latest
        is PostLoginRestoreUiState.Error -> s.latest
        else -> null
    }

}
