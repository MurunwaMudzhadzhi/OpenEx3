package com.openex.ledger

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "accounts")
class Account(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    @Column(nullable = false)
    val asset: String,

    @Column(nullable = false, precision = 28, scale = 8)
    var balance: BigDecimal = BigDecimal.ZERO,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
)
