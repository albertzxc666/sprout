package com.transcard.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class CredentialsRequest(val email: String, val password: String)

@Serializable
data class AuthResponse(
    val userId: String,
    val accessToken: String,
    val refreshToken: String,
)

@Serializable
data class RefreshRequest(val refreshToken: String)

@Serializable
data class RefreshResponse(
    val accessToken: String,
    val refreshToken: String,
)

@Serializable
data class LogoutRequest(val refreshToken: String)
