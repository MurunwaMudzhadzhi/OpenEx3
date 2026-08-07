package com.openex.auth

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.time.Instant
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey

/**
 * Issues and validates JWTs for the auth flow.
 *
 * The secret is read from JWT_SECRET (set it in docker-compose / your env for
 * anything beyond local dev  the fallback below is only safe because it's
 * obviously a placeholder, not because it's actually secret).
 */
@Component
class JwtService(
    @Value("\${openex.jwt.secret:dev-only-secret-change-me-before-shipping-anywhere-real}")
    secret: String,
    @Value("\${openex.jwt.expiration-seconds:86400}")
    val expirationSeconds: Long,
) {
    // SHA-256 the configured secret into a uniform 32-byte key rather than
    // truncating/padding it directly. Byte-level truncation silently drops
    // half the entropy of a long secret, and padding a short one with zero
    // bytes is a predictable, weak key  hashing avoids both failure modes
    // regardless of the input secret's length.
    private val key: SecretKey = Keys.hmacShaKeyFor(
        MessageDigest.getInstance("SHA-256").digest(secret.toByteArray())
    )

    fun issueToken(userId: UUID, email: String): String {
        val now = Instant.now()
        return Jwts.builder()
            .subject(userId.toString())
            .claim("email", email)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(expirationSeconds)))
            .signWith(key)
            .compact()
    }

    /** Returns the userId embedded in a valid token, or null if invalid/expired. */
    fun validateAndGetUserId(token: String): UUID? =
        try {
            val claims = Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token)
                .payload
            UUID.fromString(claims.subject)
        } catch (e: Exception) {
            null
        }
}
