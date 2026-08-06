package com.openex.auth

import com.openex.ledger.Account
import com.openex.ledger.AccountRepository
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal

/**
 * Registration seeds each new user with zero-balance USD and BTC accounts so
 * they can be credited/funded afterwards without a separate "create account"
 * step — this is a simulated exchange with one trading pair, so this covers
 * everything the matching engine currently needs.
 */
@RestController
class AuthController(
    private val userRepository: UserRepository,
    private val accountRepository: AccountRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtService: JwtService,
) {

    @PostMapping("/auth/register")
    @Transactional
    fun register(@Valid @RequestBody request: RegisterRequest): ResponseEntity<Any> {
        if (userRepository.existsByEmail(request.email)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(AuthErrorResponse("email_taken", "An account with this email already exists."))
        }

        val user = userRepository.save(
            User(email = request.email, passwordHash = passwordEncoder.encode(request.password))
        )

        accountRepository.save(Account(userId = user.id, asset = "USD", balance = BigDecimal.ZERO))
        accountRepository.save(Account(userId = user.id, asset = "BTC", balance = BigDecimal.ZERO))

        val token = jwtService.issueToken(user.id, user.email)
        return ResponseEntity.ok(AuthResponse(token, user.id, user.email, jwtService.expirationSeconds))
    }

    @PostMapping("/auth/login")
    fun login(@Valid @RequestBody request: LoginRequest): ResponseEntity<Any> {
        val user = userRepository.findByEmail(request.email)
        if (user == null || !passwordEncoder.matches(request.password, user.passwordHash)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(AuthErrorResponse("invalid_credentials", "Email or password is incorrect."))
        }

        val token = jwtService.issueToken(user.id, user.email)
        return ResponseEntity.ok(AuthResponse(token, user.id, user.email, jwtService.expirationSeconds))
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(e: MethodArgumentNotValidException): ResponseEntity<AuthErrorResponse> {
        val message = e.bindingResult.fieldErrors.joinToString("; ") { "${it.field}: ${it.defaultMessage}" }
        return ResponseEntity.badRequest().body(AuthErrorResponse("validation_error", message))
    }
}
