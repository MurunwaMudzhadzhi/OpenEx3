package com.openex.order

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface OrderRepository : JpaRepository<Order, UUID> {
    fun findByUserId(userId: UUID): List<Order>

    fun findBySymbolAndStatusIn(symbol: String, statuses: List<OrderStatus>): List<Order>
}
