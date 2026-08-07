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
 * The secret is read from openex.jwt.secret (set it via the JWT_SECRET env
 * var / docker-compose for anything beyond local dev). There is no insecure
 * fallback: if it's unset, blank, or left as the known placeholder value,
 * the app refuses to start rather than silently signing tokens with a
 * secret anyone reading this file could forge.
 */
@Component
class JwtService(
    @Value("\${openex.jwt.secret:dev-only-secret-change-me-before-shipping-anywhere-real}")
    secret: String,
    @Value("\${openex.jwt.expiration-seconds:86400}")
    val expirationSeconds: Long,
) {
    init {
        require(secret.isNotBlank() && secret != PLACEHOLDER_SECRET) {
            "openex.jwt.secret must be set to a real, high-entropy value " +
                "(env var JWT_SECRET) - refusing to start with a blank or " +
                "known placeholder secret, since that would let anyone " +
                "forge valid tokens."
        }
    }

    // SHA-256 the configured secret into a uniform 32-byte key rather than
    // truncating/padding it directly. Byte-level truncation silently drops
    // half the entropy of a long secret, and padding a short one with zero
    // bytes is a predictable, weak key - hashing avoids both failure modes
    // regardless of the input secret's length. This normalizes length only;
    // it does not manufacture entropy the configured secret doesn't have,
    // which is why the init block above still rejects weak/placeholder
    // secrets outright rather than relying on the hash to fix them.
    private val key: SecretKey = Keys.hmacShaKeyFor(
        MessageDigest.getInstance("SHA-256").digest(secret.toByteArray())
    )

    companion object {
        private const val PLACEHOLDER_SECRET = "dev-only-secret-change-me-before-shipping-anywhere-real"
    }

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
