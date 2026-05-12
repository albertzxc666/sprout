package com.transcard.domain.sync

/**
 * Состояние синхронизации. Сделано data class'ом, а не sealed-иерархией, для простого Swift-интеропа
 * (Kotlin Native экспортирует sealed sub-classes с непредсказуемыми именами вроде `SyncStatusIdle`
 * либо вложенных — это ломает кросс-платформенный код в SwiftUI).
 */
data class SyncStatus(
    val kind: SyncStatusKind,
    val errorMessage: String? = null,
    val errorRecoverable: Boolean = false,
) {
    val isIdle: Boolean get() = kind == SyncStatusKind.Idle
    val isBusy: Boolean get() = kind == SyncStatusKind.Pushing || kind == SyncStatusKind.Pulling
    val isError: Boolean get() = kind == SyncStatusKind.Error

    companion object {
        val NotAuthenticated = SyncStatus(SyncStatusKind.NotAuthenticated)
        val Idle = SyncStatus(SyncStatusKind.Idle)
        val Pushing = SyncStatus(SyncStatusKind.Pushing)
        val Pulling = SyncStatus(SyncStatusKind.Pulling)
        fun error(message: String, recoverable: Boolean): SyncStatus =
            SyncStatus(SyncStatusKind.Error, errorMessage = message, errorRecoverable = recoverable)
    }
}

enum class SyncStatusKind {
    NotAuthenticated, Idle, Pushing, Pulling, Error,
}
