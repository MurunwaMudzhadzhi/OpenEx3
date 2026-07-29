package com.openex.order

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "orders")
class Order(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    @Column(nullable = false)
    val symbol: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    val side: OrderSide,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    val type: OrderType,

    // null for MARKET orders
    @Column(precision = 28, scale = 8)
    var price: BigDecimal? = null,

    @Column(nullable = false, precision = 28, scale = 8)
    val quantity: BigDecimal,

    @Column(name = "filled_quantity", nullable = false, precision = 28, scale = 8)
    var filledQuantity: BigDecimal = BigDecimal.ZERO,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: OrderStatus = OrderStatus.OPEN,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
) {
    val remainingQuantity: BigDecimal
        get() = quantity.subtract(filledQuantity)
}
