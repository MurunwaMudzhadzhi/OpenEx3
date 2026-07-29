package com.openex.ledger

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Import
import java.math.BigDecimal
import java.util.UUID

@DataJpaTest
@Import(LedgerService::class)
class LedgerServiceTest {

    @Autowired
    lateinit var accountRepository: AccountRepository

    @Autowired
    lateinit var ledgerEntryRepository: LedgerEntryRepository

    @Autowired
    lateinit var ledgerService: LedgerService

    private lateinit var buyerId: UUID
    private lateinit var sellerId: UUID

    private lateinit var buyerBaseAccount: Account   // buyer's BTC account, starts empty
    private lateinit var buyerQuoteAccount: Account   // buyer's USD account, funded
    private lateinit var sellerBaseAccount: Account   // seller's BTC account, funded
    private lateinit var sellerQuoteAccount: Account  // seller's USD account, starts empty

    @BeforeEach
    fun setUp() {
        buyerId = UUID.randomUUID()
        sellerId = UUID.randomUUID()

        buyerBaseAccount = accountRepository.save(
            Account(userId = buyerId, asset = "BTC", balance = BigDecimal.ZERO)
        )
        buyerQuoteAccount = accountRepository.save(
            Account(userId = buyerId, asset = "USD", balance = BigDecimal("100000.00000000"))
        )
        sellerBaseAccount = accountRepository.save(
            Account(userId = sellerId, asset = "BTC", balance = BigDecimal("1.00000000"))
        )
        sellerQuoteAccount = accountRepository.save(
            Account(userId = sellerId, asset = "USD", balance = BigDecimal.ZERO)
        )
    }

    @Test
    fun `recordTrade moves both asset legs and produces four balanced entries`() {
        val tradeId = UUID.randomUUID()
        val quantity = BigDecimal("0.50000000")
        val price = BigDecimal("60000.00000000")

        val entries = ledgerService.recordTrade(
            tradeId = tradeId,
            buyerBaseAccountId = buyerBaseAccount.id,
            buyerQuoteAccountId = buyerQuoteAccount.id,
            sellerBaseAccountId = sellerBaseAccount.id,
            sellerQuoteAccountId = sellerQuoteAccount.id,
            quantity = quantity,
            price = price,
        )

        assertEquals(4, entries.size)

        // Balances reflect the trade
        val updatedBuyerBase = accountRepository.findById(buyerBaseAccount.id).get()
        val updatedBuyerQuote = accountRepository.findById(buyerQuoteAccount.id).get()
        val updatedSellerBase = accountRepository.findById(sellerBaseAccount.id).get()
        val updatedSellerQuote = accountRepository.findById(sellerQuoteAccount.id).get()

        assertEquals(0, BigDecimal("0.50000000").compareTo(updatedBuyerBase.balance))
        assertEquals(0, BigDecimal("0.50000000").compareTo(updatedSellerBase.balance))

        val quoteAmount = quantity.multiply(price) // 30000.00000000
        assertEquals(0, BigDecimal("100000.00000000").subtract(quoteAmount).compareTo(updatedBuyerQuote.balance))
        assertEquals(0, quoteAmount.compareTo(updatedSellerQuote.balance))

        // Every entry for this trade nets to zero per direction pairing
        val tradeEntries = ledgerEntryRepository.findByTradeId(tradeId)
        val debitTotal = tradeEntries.filter { it.direction == LedgerDirection.DEBIT }
            .fold(BigDecimal.ZERO) { acc, e -> acc.add(e.amount) }
        val creditTotal = tradeEntries.filter { it.direction == LedgerDirection.CREDIT }
            .fold(BigDecimal.ZERO) { acc, e -> acc.add(e.amount) }

        assertEquals(0, debitTotal.compareTo(creditTotal))
    }

    @Test
    fun `recordTrade rejects and writes nothing when seller lacks the base asset`() {
        val tradeId = UUID.randomUUID()
        // Seller only has 1.0 BTC — ask for more than they have
        val quantity = BigDecimal("5.00000000")
        val price = BigDecimal("60000.00000000")

        assertThrows(InsufficientBalanceException::class.java) {
            ledgerService.recordTrade(
                tradeId = tradeId,
                buyerBaseAccountId = buyerBaseAccount.id,
                buyerQuoteAccountId = buyerQuoteAccount.id,
                sellerBaseAccountId = sellerBaseAccount.id,
                sellerQuoteAccountId = sellerQuoteAccount.id,
                quantity = quantity,
                price = price,
            )
        }

        // Nothing should have been written or changed — transaction rolled back
        assertEquals(0, ledgerEntryRepository.findByTradeId(tradeId).size)
        val unchangedSellerBase = accountRepository.findById(sellerBaseAccount.id).get()
        assertEquals(0, BigDecimal("1.00000000").compareTo(unchangedSellerBase.balance))
    }

    @Test
    fun `recordTrade rejects and writes nothing when buyer lacks quote funds`() {
        val tradeId = UUID.randomUUID()
        // Buyer has 100000 USD — price this trade well above that
        val quantity = BigDecimal("3.00000000")
        val price = BigDecimal("60000.00000000")

        assertThrows(InsufficientBalanceException::class.java) {
            ledgerService.recordTrade(
                tradeId = tradeId,
                buyerBaseAccountId = buyerBaseAccount.id,
                buyerQuoteAccountId = buyerQuoteAccount.id,
                sellerBaseAccountId = sellerBaseAccount.id,
                sellerQuoteAccountId = sellerQuoteAccount.id,
                quantity = quantity,
                price = price,
            )
        }

        assertEquals(0, ledgerEntryRepository.findByTradeId(tradeId).size)
        val unchangedBuyerQuote = accountRepository.findById(buyerQuoteAccount.id).get()
        assertEquals(0, BigDecimal("100000.00000000").compareTo(unchangedBuyerQuote.balance))
    }
}
