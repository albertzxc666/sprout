package com.transcard.domain.sync

sealed class SyncStatus {
    /** Не залогинен в аккаунт — синк выключен. */
    data object NotAuthenticated : SyncStatus()

    /** Залогинен, всё актуально. */
    data object Idle : SyncStatus()

    /** Идёт push на сервер. */
    data object Pushing : SyncStatus()

    /** Идёт pull с сервера. */
    data object Pulling : SyncStatus()

    /**
     * Ошибка. `recoverable=true` — автоматический retry имеет смысл (сетевые проблемы).
     * `recoverable=false` — нужно вмешательство пользователя (например, отозванный refresh).
     */
    data class Error(val message: String, val recoverable: Boolean) : SyncStatus()
}
