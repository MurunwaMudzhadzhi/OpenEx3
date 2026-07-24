package com.openex.matching

import com.openex.ledger.Account
import com.openex.ledger.AccountRepository
import com.openex.ledger.LedgerService
import com.openex.order.OrderRepository
import com.openex.order.OrderSide
import com.openex.order.OrderStatus
import com.openex.order.OrderType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Import
import java.math.BigDecimal
import java.util.UUID

@DataJpaTest
@Import(MatchingEngine::class, LedgerService::class)
class MatchingEngineTest {

    @Autowired lateinit var matchingEngine: MatchingEngine
    @Autowired lateinit var orderRepository: OrderRepository
    @Autowired lateinit var tradeRepository: TradeRepository
    @Autowired lateinit var accountRepository: AccountRepository

    private lateinit var buyerId: UUID
    private lateinit var sellerId: UUID

    @BeforeEach
    fun setUp() {
        buyerId = UUID.randomUUID()
        sellerId = UUID.randomUUID()

        // Fund both sides so the ledger doesn't reject the trade
        accountRepository.save(Account(userId = buyerId, asset = "USD", balance = BigDecimal("100000.00000000")))
        accountRepository.save(Account(userId = sellerId, asset = "BTC", balance = BigDecimal("10.00000000")))
    }

    @Test
    fun `two crossing limit orders produce a trade and update balances`() {
        matchingEngine.submit(
            userId = sellerId,
            symbol = "BTC-USD",
            side = OrderSide.SELL,
            type = OrderType.LIMIT,
            price = BigDecimal("60000"),
            quantity = BigDecimal("1"),
        )

        val result = matchingEngine.submit(
            userId = buyerId,
            symbol = "BTC-USD",
            side = OrderSide.BUY,
            type = OrderType.LIMIT,
            price = BigDecimal("60000"),
            quantity = BigDecimal("1"),
        )

        assertEquals(1, result.trades.size)
        assertEquals(OrderStatus.FILLED, result.order.status)

        val buyerBtc = accountRepository.findByUserIdAndAsset(buyerId, "BTC")!!
        val sellerUsd = accountRepository.findByUserIdAndAsset(sellerId, "USD")!!

        assertEquals(0, BigDecimal("1").compareTo(buyerBtc.balance))
        assertEquals(0, BigDecimal("60000").compareTo(sellerUsd.balance))

        // The resting sell order should also now show as FILLED
        val allOrders = orderRepository.findAll()
        val sellOrder = allOrders.first { it.userId == sellerId }
        assertEquals(OrderStatus.FILLED, sellOrder.status)
    }

    @Test
    fun `unfilled limit order rests as OPEN`() {
        val result = matchingEngine.submit(
            userId = buyerId,
            symbol = "BTC-USD",
            side = OrderSide.BUY,
            type = OrderType.LIMIT,
            price = BigDecimal("100"),
            quantity = BigDecimal("1"),
        )

        assertEquals(0, result.trades.size)
        assertEquals(OrderStatus.OPEN, result.order.status)
    }

    @Test
    fun `market order with no resting liquidity is cancelled, not left open`() {
        val result = matchingEngine.submit(
            userId = buyerId,
            symbol = "BTC-USD",
            side = OrderSide.BUY,
            type = OrderType.MARKET,
            price = null,
            quantity = BigDecimal("1"),
        )

        assertEquals(0, result.trades.size)
        assertEquals(OrderStatus.CANCELLED, result.order.status)
    }
}
