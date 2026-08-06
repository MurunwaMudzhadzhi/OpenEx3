package com.openex.auth

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.util.UUID

data class RegisterRequest(
    @field:NotBlank
    @field:Email
    val email: String,

    @field:NotBlank
    @field:Size(min = 8, message = "password must be at least 8 characters")
    val password: String,
)

data class LoginRequest(
    @field:NotBlank
    val email: String,

    @field:NotBlank
    val password: String,
)

data class AuthResponse(
    val token: String,
    val userId: UUID,
    val email: String,
    val expiresInSeconds: Long,
)

data class AuthErrorResponse(
    val error: String,
    val message: String?,
)
