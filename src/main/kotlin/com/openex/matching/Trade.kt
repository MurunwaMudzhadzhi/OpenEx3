package com.openex.matching

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "trades")
class Trade(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(nullable = false)
    val symbol: String,

    @Column(name = "buy_order_id", nullable = false)
    val buyOrderId: UUID,

    @Column(name = "sell_order_id", nullable = false)
    val sellOrderId: UUID,

    @Column(nullable = false, precision = 28, scale = 8)
    val price: BigDecimal,

    @Column(nullable = false, precision = 28, scale = 8)
    val quantity: BigDecimal,

    @Column(name = "executed_at", nullable = false)
    val executedAt: Instant = Instant.now(),
)
