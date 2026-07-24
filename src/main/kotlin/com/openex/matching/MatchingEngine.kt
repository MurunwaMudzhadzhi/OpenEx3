package com.openex.matching

import com.openex.ledger.AccountRepository
import com.openex.ledger.LedgerService
import com.openex.order.Order
import com.openex.order.OrderRepository
import com.openex.order.OrderStatus
import com.openex.order.OrderType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Result returned to the caller (the order API) after submitting an order.
 */
data class SubmissionResult(
    val order: Order,
    val trades: List<Trade>,
)

@Service
class MatchingEngine(
    private val orderRepository: OrderRepository,
    private val tradeRepository: TradeRepository,
    private val accountRepository: AccountRepository,
    private val ledgerService: LedgerService,
) {
    // One OrderBook per symbol, created on first use.
    private val books = ConcurrentHashMap<String, OrderBook>()

    // One lock object per symbol — matching for a given symbol must never
    // run concurrently, since the in-memory book isn't thread-safe on its
    // own. Different symbols can match in parallel.
    private val symbolLocks = ConcurrentHashMap<String, Any>()

    /**
     * Submits a new order: persists it, matches it against the book, and
     * records any resulting trades through the ledger. All in one
     * transaction — if the ledger rejects a fill partway through, the
     * whole submission rolls back rather than leaving a half-matched order.
     */
    @Transactional
    fun submit(
        userId: UUID,
        symbol: String,
        side: com.openex.order.OrderSide,
        type: OrderType,
        price: BigDecimal?,
        quantity: BigDecimal,
    ): SubmissionResult {
        require(type == OrderType.MARKET || price != null) { "LIMIT orders require a price" }
        require(quantity > BigDecimal.ZERO) { "quantity must be positive" }

        val order = orderRepository.save(
            Order(
                userId = userId,
                symbol = symbol,
                side = side,
                type = type,
                price = price,
                quantity = quantity,
            )
        )

        val lock = symbolLocks.computeIfAbsent(symbol) { Any() }
        val trades = synchronized(lock) {
            val book = books.computeIfAbsent(symbol) { OrderBook(symbol) }

            val incoming = IncomingOrder(
                orderId = order.id,
                userId = userId,
                side = side,
                type = type,
                price = price,
                quantity = quantity,
            )

            val result = book.submit(incoming)
            applyFills(order, symbol, result.fills)
        }

        finalizeStatus(order)
        orderRepository.save(order)

        return SubmissionResult(order = order, trades = trades)
    }

    /**
     * Converts book fills into persisted Trade rows + ledger entries, and
     * updates both the incoming order and each resting counter-order's
     * filled_quantity/status.
     */
    private fun applyFills(incomingOrder: Order, symbol: String, fills: List<Fill>): List<Trade> {
        val (baseAsset, quoteAsset) = parseSymbol(symbol)
        val trades = mutableListOf<Trade>()

        for (fill in fills) {
            val counterOrder = orderRepository.findById(fill.restingOrderId)
                .orElseThrow { IllegalStateException("Resting order ${fill.restingOrderId} not found") }

            val buyOrder = if (incomingOrder.side == com.openex.order.OrderSide.BUY) incomingOrder else counterOrder
            val sellOrder = if (incomingOrder.side == com.openex.order.OrderSide.SELL) incomingOrder else counterOrder

            val buyerBaseAccount = getOrCreateAccount(buyOrder.userId, baseAsset)
            val buyerQuoteAccount = getOrCreateAccount(buyOrder.userId, quoteAsset)
            val sellerBaseAccount = getOrCreateAccount(sellOrder.userId, baseAsset)
            val sellerQuoteAccount = getOrCreateAccount(sellOrder.userId, quoteAsset)

            val trade = Trade(
                symbol = symbol,
                buyOrderId = buyOrder.id,
                sellOrderId = sellOrder.id,
                price = fill.price,
                quantity = fill.quantity,
            )

            ledgerService.recordTrade(
                tradeId = trade.id,
                buyerBaseAccountId = buyerBaseAccount.id,
                buyerQuoteAccountId = buyerQuoteAccount.id,
                sellerBaseAccountId = sellerBaseAccount.id,
                sellerQuoteAccountId = sellerQuoteAccount.id,
                quantity = fill.quantity,
                price = fill.price,
            )

            tradeRepository.save(trade)
            trades += trade

            incomingOrder.filledQuantity = incomingOrder.filledQuantity.add(fill.quantity)
            counterOrder.filledQuantity = counterOrder.filledQuantity.add(fill.quantity)
            finalizeStatus(counterOrder)
            orderRepository.save(counterOrder)
        }

        return trades
    }

    private fun finalizeStatus(order: Order) {
        order.status = when {
            order.filledQuantity >= order.quantity -> OrderStatus.FILLED
            order.filledQuantity > BigDecimal.ZERO -> OrderStatus.PARTIALLY_FILLED
            order.type == OrderType.MARKET -> OrderStatus.CANCELLED // unfilled market orders don't rest
            else -> OrderStatus.OPEN
        }
    }

    private fun getOrCreateAccount(userId: UUID, asset: String) =
        accountRepository.findByUserIdAndAsset(userId, asset)
            ?: accountRepository.save(com.openex.ledger.Account(userId = userId, asset = asset))

    private fun parseSymbol(symbol: String): Pair<String, String> {
        val parts = symbol.split("-")
        require(parts.size == 2) { "symbol must be in BASE-QUOTE format, e.g. BTC-USD" }
        return parts[0] to parts[1]
    }
}
