package com.openex.ledger

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface AccountRepository : JpaRepository<Account, UUID> {
    fun findByUserIdAndAsset(userId: UUID, asset: String): Account?
    fun findByUserId(userId: UUID): List<Account>
}

interface LedgerEntryRepository : JpaRepository<LedgerEntry, UUID> {
    fun findByTradeId(tradeId: UUID): List<LedgerEntry>
    fun findByAccountIdOrderByCreatedAtDesc(accountId: UUID): List<LedgerEntry>
}
