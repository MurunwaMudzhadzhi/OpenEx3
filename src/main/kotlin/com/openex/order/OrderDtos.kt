package com.openex.order

import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class OrderRequest(
    @field:NotNull
    val symbol: String?,

    @field:NotNull
    val side: OrderSide?,

    @field:NotNull
    val type: OrderType?,

    // required for LIMIT, must be null/omitted for MARKET — validated in the controller
    @field:DecimalMin(value = "0.00000001", inclusive = true)
    val price: BigDecimal?,

    @field:NotNull
    @field:DecimalMin(value = "0.00000001", inclusive = true)
    val quantity: BigDecimal?,
)

data class TradeSummary(
    val tradeId: UUID,
    val price: BigDecimal,
    val quantity: BigDecimal,
    val executedAt: Instant,
)

data class OrderResponse(
    val orderId: UUID,
    val symbol: String,
    val side: OrderSide,
    val type: OrderType,
    val price: BigDecimal?,
    val quantity: BigDecimal,
    val filledQuantity: BigDecimal,
    val status: OrderStatus,
    val trades: List<TradeSummary>,
)

data class ErrorResponse(
    val error: String,
    val message: String?,
)
