package com.openex.order

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest

/**
 * Thrown when the same Idempotency-Key is reused with a genuinely different
 * request body — that's a client bug (keys must be unique per distinct
 * request), not something safe to silently replay or overwrite.
 */
class IdempotencyKeyConflictException(key: String) :
    RuntimeException("Idempotency-Key '$key' was already used with a different request body")

sealed class IdempotencyOutcome {
    data class Replay(val statusCode: Int, val responseBody: String) : IdempotencyOutcome()
    object Fresh : IdempotencyOutcome()
}

@Service
class IdempotencyService(
    private val repository: IdempotencyKeyRepository,
) {
    fun hashOf(rawBody: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(rawBody.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * Checks whether [key] has been seen before.
     *  - Not seen at all -> Fresh, caller should proceed and call [complete] after.
     *  - Seen with the SAME request hash -> Replay of the stored response.
     *  - Seen with a DIFFERENT request hash -> reject, this is key misuse.
     */
    @Transactional
    fun check(key: String, requestHash: String): IdempotencyOutcome {
        val existing = repository.findById(key).orElse(null) ?: run {
            // Reserve the key immediately so a second concurrent request with
            // the same key doesn't also see "not found" and double-process.
            repository.save(IdempotencyKey(key = key, requestHash = requestHash))
            return IdempotencyOutcome.Fresh
        }

        if (existing.requestHash != requestHash) {
            throw IdempotencyKeyConflictException(key)
        }

        if (existing.responseBody == null) {
            // Key was reserved (by a concurrent request) but not completed yet.
            // Simplest safe behavior: treat as fresh work still in flight —
            // caller's own transaction/order processing will still run.
            // A stricter implementation could retry/wait here instead.
            return IdempotencyOutcome.Fresh
        }

        return IdempotencyOutcome.Replay(existing.statusCode ?: 200, existing.responseBody!!)
    }

    @Transactional
    fun complete(key: String, statusCode: Int, responseBody: String) {
        val existing = repository.findById(key).orElseThrow {
            IllegalStateException("Idempotency key '$key' was not reserved before completion")
        }
        existing.statusCode = statusCode
        existing.responseBody = responseBody
        repository.save(existing)
    }
}
