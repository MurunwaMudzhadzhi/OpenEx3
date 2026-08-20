package com.openex.ledger

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.UUID

/**
 * Writes the ledger side-effects of a single matched trade.
 *
 * A trade moves two assets in opposite directions between two accounts:
 *   - the base asset (e.g. BTC) flows seller -> buyer
 *   - the quote asset (e.g. USD) flows buyer -> seller, at price * quantity
 *
 * That's 4 ledger_entries rows (debit+credit per asset leg), all written
 * atomically. If either account can't cover its leg, the whole trade is
 * rejected and nothing is written â€” the matching engine should catch
 * InsufficientBalanceException and treat the order as unfillable rather
 * than letting the ledger go negative.
 *
 * This intentionally does NOT depend on Order/Trade JPA entities yet â€”
 * those land with the matching engine (Day 3-5). The matching engine will
 * call recordTrade() once a trade is decided; this service only owns the
 * money-movement guarantee.
 */
@Service
class LedgerService(
    private val accountRepository: AccountRepository,
    private val ledgerEntryRepository: LedgerEntryRepository,
) {

    @Transactional
    fun recordTrade(
        tradeId: UUID,
        buyerBaseAccountId: UUID,
        buyerQuoteAccountId: UUID,
        sellerBaseAccountId: UUID,
        sellerQuoteAccountId: UUID,
        quantity: BigDecimal,
        price: BigDecimal,
    ): List<LedgerEntry> {
        require(quantity > BigDecimal.ZERO) { "quantity must be positive" }
        require(price > BigDecimal.ZERO) { "price must be positive" }

        // quantity and price can each carry up to 8 fractional digits, so
        // their raw product can carry up to 16 â€” more than the NUMERIC(28,8)
        // columns actually store. Round explicitly here so the LedgerEntry
        // objects returned to the caller match what Postgres persists,
        // rather than silently drifting from the DB's own rounding.
        val quoteAmount = quantity.multiply(price)
            .setScale(8, java.math.RoundingMode.HALF_UP)

        val entries = mutableListOf<LedgerEntry>()

        // Base asset leg: seller -> buyer
        entries += debit(sellerBaseAccountId, tradeId, quantity)
        entries += credit(buyerBaseAccountId, tradeId, quantity)

        // Quote asset leg: buyer -> seller
        entries += debit(buyerQuoteAccountId, tradeId, quoteAmount)
        entries += credit(sellerQuoteAccountId, tradeId, quoteAmount)

        return ledgerEntryRepository.saveAll(entries)
    }

    private fun debit(accountId: UUID, tradeId: UUID, amount: BigDecimal): LedgerEntry {
        val account = accountRepository.findById(accountId)
            .orElseThrow { IllegalArgumentException("Account $accountId not found") }

        if (account.balance < amount) {
            throw InsufficientBalanceException(accountId, amount, account.balance)
        }

        account.balance = account.balance.subtract(amount)
        accountRepository.save(account)

        return LedgerEntry(
            accountId = accountId,
            tradeId = tradeId,
            direction = LedgerDirection.DEBIT,
            amount = amount,
            balanceAfter = account.balance,
        )
    }

    private fun credit(accountId: UUID, tradeId: UUID, amount: BigDecimal): LedgerEntry {
        val account = accountRepository.findById(accountId)
            .orElseThrow { IllegalArgumentException("Account $accountId not found") }

        account.balance = account.balance.add(amount)
        accountRepository.save(account)

        return LedgerEntry(
            accountId = accountId,
            tradeId = tradeId,
            direction = LedgerDirection.CREDIT,
            amount = amount,
            balanceAfter = account.balance,
        )
    }

    /**
     * Credits simulated funds into a user's own account - the "faucet"
     * deposit from the Day 3 spec. Unlike recordTrade, this is a single
     * one-sided credit: money entering the simulated system from nowhere,
     * not moving between two accounts, so there's no matching debit leg.
     * A synthetic tradeId (random UUID) is used so the deposit still shows
     * up in the same ledger_entries audit trail as trades, distinguishable
     * by there being only one entry for that id instead of the usual four.
     */
    @Transactional
    fun deposit(userId: UUID, asset: String, amount: BigDecimal): LedgerEntry {
        require(amount > BigDecimal.ZERO) { "deposit amount must be positive" }

        val account = accountRepository.findByUserIdAndAsset(userId, asset)
            ?: accountRepository.save(Account(userId = userId, asset = asset))

        return credit(account.id, UUID.randomUUID(), amount)
    }
}
