package com.transcard.data.remote

import com.transcard.config.Config
import com.transcard.data.remote.dto.AuthResponse
import com.transcard.data.remote.dto.CredentialsRequest
import com.transcard.data.remote.dto.LogoutRequest
import com.transcard.data.remote.dto.RefreshRequest
import com.transcard.data.remote.dto.RefreshResponse
import com.transcard.data.storage.TokenStorage
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Обёртка над Ktor HttpClient для sprout-server.
 *
 * Два HttpClient'а:
 *   - [unauthed] — без Auth plugin. Используется для /auth/register, /auth/login и для refresh-call'а изнутри Auth plugin (чтобы не было рекурсии).
 *   - [authed] — с Bearer Auth plugin. Автоматически подставляет access-токен и обновляет его через refresh при 401.
 *
 * Когда refresh окончательно фейлится (401/403/сеть) — token storage очищается, и AuthRepository увидит, что пользователь больше не залогинен.
 */
class SproutApi(
    private val tokenStorage: TokenStorage,
    httpClientFactory: () -> HttpClient,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    private val baseUrl = Config.API_BASE_URL.trimEnd('/')

    /** HTTP клиент без Auth plugin. Используется для auth-эндпоинтов и для refresh-call. */
    val unauthed: HttpClient = httpClientFactory().config {
        install(ContentNegotiation) { json(json) }
        install(Logging) { level = LogLevel.INFO }
        defaultRequest {
            url(baseUrl)
            contentType(ContentType.Application.Json)
        }
        expectSuccess = false
    }

    /** HTTP клиент с Bearer Auth plugin. */
    val authed: HttpClient = httpClientFactory().config {
        install(ContentNegotiation) { json(json) }
        install(Logging) { level = LogLevel.INFO }
        defaultRequest {
            url(baseUrl)
            contentType(ContentType.Application.Json)
        }
        expectSuccess = false
        install(Auth) {
            bearer {
                loadTokens {
                    val a = tokenStorage.accessToken
                    val r = tokenStorage.refreshToken
                    if (a != null && r != null) BearerTokens(a, r) else null
                }
                refreshTokens {
                    val current = tokenStorage.refreshToken ?: return@refreshTokens null
                    try {
                        val resp = unauthed.post("$baseUrl/api/v1/auth/refresh") {
                            setBody(RefreshRequest(current))
                            markAsRefreshTokenRequest()
                        }
                        if (resp.status.isSuccess()) {
                            val body = resp.body<RefreshResponse>()
                            tokenStorage.accessToken = body.accessToken
                            tokenStorage.refreshToken = body.refreshToken
                            BearerTokens(body.accessToken, body.refreshToken)
                        } else {
                            // refresh окончательно умер — выкидываем пользователя.
                            tokenStorage.clear()
                            null
                        }
                    } catch (_: Throwable) {
                        null
                    }
                }
            }
        }
    }

    // ---------- auth endpoints (через unauthed) ----------

    suspend fun register(email: String, password: String): AuthResponse {
        val resp = unauthed.post("$baseUrl/api/v1/auth/register") {
            setBody(CredentialsRequest(email, password))
        }
        return resp.parseOrThrow()
    }

    suspend fun login(email: String, password: String): AuthResponse {
        val resp = unauthed.post("$baseUrl/api/v1/auth/login") {
            setBody(CredentialsRequest(email, password))
        }
        return resp.parseOrThrow()
    }

    suspend fun logout(refreshToken: String) {
        val resp = unauthed.post("$baseUrl/api/v1/auth/logout") {
            setBody(LogoutRequest(refreshToken))
        }
        if (!resp.status.isSuccess() && resp.status != HttpStatusCode.NoContent) {
            // logout best-effort: чистим локально даже если сервер не ответил OK
        }
    }

    private suspend inline fun <reified T> HttpResponse.parseOrThrow(): T {
        if (status.isSuccess()) return body()
        val text = runCatching { bodyAsText() }.getOrDefault("")
        throw SproutApiException(status.value, text)
    }
}

class SproutApiException(val statusCode: Int, val body: String) :
    RuntimeException("sprout-server error: $statusCode $body")
