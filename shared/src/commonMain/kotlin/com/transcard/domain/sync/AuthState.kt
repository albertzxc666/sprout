package com.transcard.domain.sync

data class AuthState(
    val userId: String?,
    val email: String?,
) {
    val isAuthenticated: Boolean get() = userId != null

    companion object {
        val Anonymous = AuthState(userId = null, email = null)
    }
}
