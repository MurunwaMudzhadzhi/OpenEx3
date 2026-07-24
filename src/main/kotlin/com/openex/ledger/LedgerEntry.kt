package com.openex.ledger

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

enum class LedgerDirection { DEBIT, CREDIT }

@Entity
@Table(name = "ledger_entries")
class LedgerEntry(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "account_id", nullable = false)
    val accountId: UUID,

    @Column(name = "trade_id", nullable = false)
    val tradeId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val direction: LedgerDirection,

    @Column(nullable = false, precision = 28, scale = 8)
    val amount: BigDecimal,

    @Column(name = "balance_after", nullable = false, precision = 28, scale = 8)
    val balanceAfter: BigDecimal,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
)
