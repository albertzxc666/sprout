package com.transcard.data.storage

import com.russhwolf.settings.Settings

/**
 * Безопасное хранилище токенов на основе multiplatform-settings.
 * Конкретная Settings-имплементация инжектится через DI и зависит от платформы:
 *   - Android: EncryptedSharedPreferences (androidx.security.crypto)
 *   - iOS: Keychain
 *   - Desktop: PropertiesSettings в ~/.transcard/secrets.properties (НЕ зашифровано, см. план)
 *
 * Ключи:
 *   sprout.access  — access JWT
 *   sprout.refresh — refresh token (raw)
 *   sprout.userId  — userId (для отображения и быстрой проверки логина)
 *   sprout.email   — email (для отображения "Вы вошли как ...")
 *
 * Все операции синхронные — Settings API не suspend.
 */
class TokenStorage(private val settings: Settings) {

    var accessToken: String?
        get() = settings.getStringOrNull(KEY_ACCESS)
        set(value) {
            if (value == null) settings.remove(KEY_ACCESS) else settings.putString(KEY_ACCESS, value)
        }

    var refreshToken: String?
        get() = settings.getStringOrNull(KEY_REFRESH)
        set(value) {
            if (value == null) settings.remove(KEY_REFRESH) else settings.putString(KEY_REFRESH, value)
        }

    var userId: String?
        get() = settings.getStringOrNull(KEY_USER_ID)
        set(value) {
            if (value == null) settings.remove(KEY_USER_ID) else settings.putString(KEY_USER_ID, value)
        }

    var email: String?
        get() = settings.getStringOrNull(KEY_EMAIL)
        set(value) {
            if (value == null) settings.remove(KEY_EMAIL) else settings.putString(KEY_EMAIL, value)
        }

    fun isAuthenticated(): Boolean = userId != null && refreshToken != null

    fun saveSession(userId: String, email: String, accessToken: String, refreshToken: String) {
        this.userId = userId
        this.email = email
        this.accessToken = accessToken
        this.refreshToken = refreshToken
    }

    fun clear() {
        accessToken = null
        refreshToken = null
        userId = null
        email = null
    }

    private companion object {
        const val KEY_ACCESS = "sprout.access"
        const val KEY_REFRESH = "sprout.refresh"
        const val KEY_USER_ID = "sprout.userId"
        const val KEY_EMAIL = "sprout.email"
    }
}
