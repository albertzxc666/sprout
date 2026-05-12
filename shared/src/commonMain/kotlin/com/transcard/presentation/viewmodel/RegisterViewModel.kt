package com.transcard.presentation.viewmodel

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.transcard.domain.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RegisterViewModel(
    private val authRepository: AuthRepository,
) : ScreenModel {

    private val _state = MutableStateFlow(AuthFormState())
    val state: StateFlow<AuthFormState> = _state.asStateFlow()

    fun onEmailChange(value: String) = _state.update { it.copy(email = value.trim(), error = null) }
    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, error = null) }

    fun submit() {
        val s = _state.value
        if (!s.canSubmit) return
        _state.update { it.copy(isLoading = true, error = null) }
        screenModelScope.launch {
            runCatching { authRepository.register(s.email, s.password) }
                .onSuccess { _state.update { it.copy(isLoading = false, success = true) } }
                .onFailure { e -> _state.update { it.copy(isLoading = false, error = e.message ?: "Ошибка регистрации") } }
        }
    }
}
