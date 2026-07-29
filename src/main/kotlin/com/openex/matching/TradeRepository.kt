package com.openex.matching

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface TradeRepository : JpaRepository<Trade, UUID> {
    fun findBySymbolOrderByExecutedAtDesc(symbol: String): List<Trade>
}
