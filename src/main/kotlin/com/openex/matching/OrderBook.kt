package com.openex.matching

import com.openex.order.OrderSide
import com.openex.order.OrderType
import java.math.BigDecimal
import java.util.UUID

/**
 * A resting order sitting in the book, waiting to be matched.
 * Immutable except for [remaining], which shrinks as it gets filled.
 */
data class RestingOrder(
    val orderId: UUID,
    val userId: UUID,
    val side: OrderSide,
    val price: BigDecimal, // resting orders always have a concrete price
    var remaining: BigDecimal,
    val sequence: Long, // arrival order, for time priority within the same price
)

/** An incoming order to be matched against the book. */
data class IncomingOrder(
    val orderId: UUID,
    val userId: UUID,
    val side: OrderSide,
    val type: OrderType,
    val price: BigDecimal?, // null for MARKET
    val quantity: BigDecimal,
)

/** The result of matching one counter-order against the incoming order. */
data class Fill(
    val restingOrderId: UUID,
    val restingUserId: UUID,
    val price: BigDecimal, // trades execute at the RESTING order's price
    val quantity: BigDecimal,
)

/** Outcome of submitting an order to the book. */
data class MatchResult(
    val fills: List<Fill>,
    val remainingQuantity: BigDecimal, // what's left of the incoming order after matching
)

/**
 * Single-symbol order book with price-time priority.
 *
 * Bids: highest price first, then earliest arrival first.
 * Asks: lowest price first, then earliest arrival first.
 *
 * NOT thread-safe on its own — callers (MatchingEngine) must synchronize
 * access per symbol, since two orders for the same symbol must never be
 * matched concurrently.
 */
class OrderBook(val symbol: String) {

    private var sequenceCounter = 0L

    // Bids sorted descending by price (best bid = highest price = first)
    private val bids = sortedMapOf<BigDecimal, ArrayDeque<RestingOrder>>(compareByDescending { it })

    // Asks sorted ascending by price (best ask = lowest price = first)
    private val asks = sortedMapOf<BigDecimal, ArrayDeque<RestingOrder>>(compareBy { it })

    /**
     * Matches [incoming] against the opposite side of the book, then — if
     * it's a LIMIT order with quantity left over — rests it in the book.
     * MARKET orders never rest: any unfilled remainder is simply dropped
     * (caller should mark the order PARTIALLY_FILLED/CANCELLED accordingly).
     */
    fun submit(incoming: IncomingOrder): MatchResult {
        require(incoming.type == OrderType.MARKET || incoming.price != null) {
            "LIMIT orders must have a price"
        }

        val counterBook = if (incoming.side == OrderSide.BUY) asks else bids
        val fills = mutableListOf<Fill>()
        var remaining = incoming.quantity

        while (remaining > BigDecimal.ZERO) {
            val bestEntry = counterBook.entries.firstOrNull() ?: break
            val bestPrice = bestEntry.key
            val queue = bestEntry.value

            // LIMIT orders only cross if the price is acceptable
            if (incoming.type == OrderType.LIMIT) {
                val limitPrice = incoming.price!!
                val crosses = if (incoming.side == OrderSide.BUY) {
                    limitPrice >= bestPrice
                } else {
                    limitPrice <= bestPrice
                }
                if (!crosses) break
            }

            val resting = queue.firstOrNull() ?: run {
                counterBook.remove(bestPrice)
                null
            } ?: continue

            val fillQty = minOf(remaining, resting.remaining)
            fills += Fill(
                restingOrderId = resting.orderId,
                restingUserId = resting.userId,
                price = bestPrice,
                quantity = fillQty,
            )

            remaining = remaining.subtract(fillQty)
            resting.remaining = resting.remaining.subtract(fillQty)

            if (resting.remaining == BigDecimal.ZERO) {
                queue.removeFirst()
                if (queue.isEmpty()) counterBook.remove(bestPrice)
            }
        }

        // Rest the leftover, LIMIT orders only
        if (remaining > BigDecimal.ZERO && incoming.type == OrderType.LIMIT) {
            val book = if (incoming.side == OrderSide.BUY) bids else asks
            val entry = RestingOrder(
                orderId = incoming.orderId,
                userId = incoming.userId,
                side = incoming.side,
                price = incoming.price!!,
                remaining = remaining,
                sequence = sequenceCounter++,
            )
            book.getOrPut(incoming.price) { ArrayDeque() }.addLast(entry)
        }

        return MatchResult(fills = fills, remainingQuantity = remaining)
    }

    /** Removes a resting order (e.g. on cancellation). Returns true if found and removed. */
    fun cancel(orderId: UUID): Boolean {
        for (book in listOf(bids, asks)) {
            for ((price, queue) in book) {
                val removed = queue.removeAll { it.orderId == orderId }
                if (removed) {
                    if (queue.isEmpty()) book.remove(price)
                    return true
                }
            }
        }
        return false
    }

    fun bestBid(): BigDecimal? = bids.keys.firstOrNull()
    fun bestAsk(): BigDecimal? = asks.keys.firstOrNull()
}
