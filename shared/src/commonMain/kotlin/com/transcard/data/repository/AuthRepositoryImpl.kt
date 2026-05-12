package com.transcard.data.repository

import com.transcard.data.remote.SproutApi
import com.transcard.data.remote.SproutApiException
import com.transcard.data.storage.TokenStorage
import com.transcard.domain.repository.AuthRepository
import com.transcard.domain.sync.AuthState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class AuthRepositoryImpl(
    private val api: SproutApi,
    private val tokenStorage: TokenStorage,
) : AuthRepository {

    private val state = MutableStateFlow(currentState())

    override fun observeAuthState(): Flow<AuthState> = state.asStateFlow()

    override suspend fun register(email: String, password: String) {
        try {
            val resp = api.register(email, password)
            tokenStorage.saveSession(resp.userId, email, resp.accessToken, resp.refreshToken)
            state.value = AuthState(userId = resp.userId, email = email)
        } catch (e: SproutApiException) {
            throw mapAuthError(e)
        }
    }

    override suspend fun login(email: String, password: String) {
        try {
            val resp = api.login(email, password)
            tokenStorage.saveSession(resp.userId, email, resp.accessToken, resp.refreshToken)
            state.value = AuthState(userId = resp.userId, email = email)
        } catch (e: SproutApiException) {
            throw mapAuthError(e)
        }
    }

    override suspend fun logout() {
        val refresh = tokenStorage.refreshToken
        if (refresh != null) {
            runCatching { api.logout(refresh) }
        }
        tokenStorage.clear()
        state.value = AuthState.Anonymous
    }

    private fun currentState(): AuthState =
        if (tokenStorage.isAuthenticated()) {
            AuthState(userId = tokenStorage.userId, email = tokenStorage.email)
        } else {
            AuthState.Anonymous
        }

    private fun mapAuthError(e: SproutApiException): Throwable = when (e.statusCode) {
        400 -> IllegalArgumentException("Невалидные данные")
        401 -> IllegalStateException("Неверный email или пароль")
        409 -> IllegalStateException("Email уже занят")
        429 -> IllegalStateException("Слишком много попыток, подождите минуту")
        in 500..599 -> IllegalStateException("Сервер недоступен, попробуйте позже")
        else -> e
    }
}
