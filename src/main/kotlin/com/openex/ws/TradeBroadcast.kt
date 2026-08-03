package com.openex.ws

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/** What's actually pushed to /topic/trades/{symbol} — one per executed trade. */
data class TradeBroadcast(
    val tradeId: UUID,
    val symbol: String,
    val price: BigDecimal,
    val quantity: BigDecimal,
    val executedAt: Instant,
)
