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
 * rejected and nothing is written — the matching engine should catch
 * InsufficientBalanceException and treat the order as unfillable rather
 * than letting the ledger go negative.
 *
 * This intentionally does NOT depend on Order/Trade JPA entities yet —
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

        val quoteAmount = quantity.multiply(price)

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
}
