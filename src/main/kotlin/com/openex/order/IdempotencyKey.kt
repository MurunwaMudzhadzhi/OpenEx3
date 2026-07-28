package com.openex.order

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "idempotency_keys")
class IdempotencyKey(
    @Id
    @Column(nullable = false, length = 255)
    val key: String,

    @Column(name = "request_hash", nullable = false, length = 64)
    val requestHash: String,

    @Column(name = "response_body", columnDefinition = "TEXT")
    var responseBody: String? = null,

    @Column(name = "status_code")
    var statusCode: Int? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
)
