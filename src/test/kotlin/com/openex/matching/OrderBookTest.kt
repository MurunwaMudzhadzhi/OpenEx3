package com.openex.matching

import com.openex.order.OrderSide
import com.openex.order.OrderType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.UUID

class OrderBookTest {

    private fun order(
        side: OrderSide,
        type: OrderType = OrderType.LIMIT,
        price: BigDecimal? = null,
        quantity: String,
    ) = IncomingOrder(
        orderId = UUID.randomUUID(),
        userId = UUID.randomUUID(),
        side = side,
        type = type,
        price = price,
        quantity = BigDecimal(quantity),
    )

    @Test
    fun `resting limit order with no match just rests in the book`() {
        val book = OrderBook("BTC-USD")

        val result = book.submit(order(OrderSide.BUY, price = BigDecimal("100"), quantity = "1"))

        assertTrue(result.fills.isEmpty())
        assertEquals(0, BigDecimal("1").compareTo(result.remainingQuantity))
        assertEquals(0, BigDecimal("100").compareTo(book.bestBid()!!))
    }

    @Test
    fun `crossing limit orders match at the resting order's price`() {
        val book = OrderBook("BTC-USD")

        // Resting sell at 100
        book.submit(order(OrderSide.SELL, price = BigDecimal("100"), quantity = "1"))

        // Incoming buy at 105 crosses — should fill at the resting price (100), not 105
        val result = book.submit(order(OrderSide.BUY, price = BigDecimal("105"), quantity = "1"))

        assertEquals(1, result.fills.size)
        assertEquals(0, BigDecimal("100").compareTo(result.fills[0].price))
        assertEquals(0, BigDecimal.ZERO.compareTo(result.remainingQuantity))
    }

    @Test
    fun `price priority - best price fills first`() {
        val book = OrderBook("BTC-USD")

        // Two resting sells: 101 and 100. Best ask is 100.
        book.submit(order(OrderSide.SELL, price = BigDecimal("101"), quantity = "1"))
        val cheaperSellId = UUID.randomUUID()
        book.submit(
            IncomingOrder(cheaperSellId, UUID.randomUUID(), OrderSide.SELL, OrderType.LIMIT, BigDecimal("100"), BigDecimal("1"))
        )

        val result = book.submit(order(OrderSide.BUY, price = BigDecimal("101"), quantity = "1"))

        assertEquals(1, result.fills.size)
        assertEquals(0, BigDecimal("100").compareTo(result.fills[0].price))
        assertEquals(cheaperSellId, result.fills[0].restingOrderId)
    }

    @Test
    fun `time priority - earlier order at same price fills first`() {
        val book = OrderBook("BTC-USD")

        val firstId = UUID.randomUUID()
        val secondId = UUID.randomUUID()
        book.submit(IncomingOrder(firstId, UUID.randomUUID(), OrderSide.SELL, OrderType.LIMIT, BigDecimal("100"), BigDecimal("1")))
        book.submit(IncomingOrder(secondId, UUID.randomUUID(), OrderSide.SELL, OrderType.LIMIT, BigDecimal("100"), BigDecimal("1")))

        val result = book.submit(order(OrderSide.BUY, price = BigDecimal("100"), quantity = "1"))

        assertEquals(firstId, result.fills[0].restingOrderId)
    }

    @Test
    fun `partial fill leaves remainder resting`() {
        val book = OrderBook("BTC-USD")

        book.submit(order(OrderSide.SELL, price = BigDecimal("100"), quantity = "1"))
        val result = book.submit(order(OrderSide.BUY, price = BigDecimal("100"), quantity = "2.5"))

        assertEquals(1, result.fills.size)
        assertEquals(0, BigDecimal("1").compareTo(result.fills[0].quantity))
        assertEquals(0, BigDecimal("1.5").compareTo(result.remainingQuantity))
        assertEquals(0, BigDecimal("100").compareTo(book.bestBid()!!)) // remainder now resting as a bid
    }

    @Test
    fun `market order walks multiple price levels until filled`() {
        val book = OrderBook("BTC-USD")

        book.submit(order(OrderSide.SELL, price = BigDecimal("100"), quantity = "1"))
        book.submit(order(OrderSide.SELL, price = BigDecimal("101"), quantity = "1"))
        book.submit(order(OrderSide.SELL, price = BigDecimal("102"), quantity = "1"))

        val result = book.submit(order(OrderSide.BUY, type = OrderType.MARKET, quantity = "2.5"))

        assertEquals(3, result.fills.size)
        assertEquals(0, BigDecimal("1").compareTo(result.fills[0].quantity))
        assertEquals(0, BigDecimal("100").compareTo(result.fills[0].price))
        assertEquals(0, BigDecimal("1").compareTo(result.fills[1].quantity))
        assertEquals(0, BigDecimal("101").compareTo(result.fills[1].price))
        assertEquals(0, BigDecimal("0.5").compareTo(result.fills[2].quantity))
        assertEquals(0, BigDecimal("102").compareTo(result.fills[2].price))
        assertEquals(0, BigDecimal.ZERO.compareTo(result.remainingQuantity))
    }

    @Test
    fun `market order with no liquidity fills nothing and does not rest`() {
        val book = OrderBook("BTC-USD")

        val result = book.submit(order(OrderSide.BUY, type = OrderType.MARKET, quantity = "1"))

        assertTrue(result.fills.isEmpty())
        assertEquals(0, BigDecimal("1").compareTo(result.remainingQuantity))
        assertEquals(null, book.bestBid()) // market orders never rest, win or lose
    }

    @Test
    fun `limit order that does not cross does not match and rests instead`() {
        val book = OrderBook("BTC-USD")

        book.submit(order(OrderSide.SELL, price = BigDecimal("100"), quantity = "1"))

        // Buy at 90 doesn't cross a 100 ask
        val result = book.submit(order(OrderSide.BUY, price = BigDecimal("90"), quantity = "1"))

        assertTrue(result.fills.isEmpty())
        assertEquals(0, BigDecimal("90").compareTo(book.bestBid()!!))
        assertEquals(0, BigDecimal("100").compareTo(book.bestAsk()!!))
    }

    @Test
    fun `cancel removes a resting order from the book`() {
        val book = OrderBook("BTC-USD")
        val incoming = order(OrderSide.BUY, price = BigDecimal("100"), quantity = "1")
        book.submit(incoming)

        val removed = book.cancel(incoming.orderId)

        assertTrue(removed)
        assertEquals(null, book.bestBid())
    }
}
