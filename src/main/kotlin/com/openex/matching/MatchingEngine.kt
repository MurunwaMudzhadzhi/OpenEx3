package com.openex.matching

import com.openex.ledger.AccountRepository
import com.openex.ledger.LedgerService
import com.openex.order.Order
import com.openex.order.OrderRepository
import com.openex.order.OrderStatus
import com.openex.order.OrderType
import org.springframework.context.ApplicationEventPublisher
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

/** Read-only snapshot of a symbol's book, safe to serialize and broadcast. */
data class OrderBookSnapshot(
    val symbol: String,
    val bids: List<PriceLevel>,
    val asks: List<PriceLevel>,
)

@Service
class MatchingEngine(
    private val orderRepository: OrderRepository,
    private val tradeRepository: TradeRepository,
    private val accountRepository: AccountRepository,
    private val ledgerService: LedgerService,
    private val eventPublisher: ApplicationEventPublisher,
) {
    // One OrderBook per symbol, created on first use.
    private val books = ConcurrentHashMap<String, OrderBook>()

    // One lock object per symbol  matching for a given symbol must never
    // run concurrently, since the in-memory book isn't thread-safe on its
    // own. Different symbols can match in parallel.
    private val symbolLocks = ConcurrentHashMap<String, Any>()

    /**
     * Test-only: clears all in-memory book state. MatchingEngine is a
     * singleton Spring bean, so its in-memory `books` map survives across
     * test methods even though @DataJpaTest/@SpringBootTest roll back the
     * DB after each test. Without this, a resting order created in one
     * test can leak into a later test's book and cause spurious
     * "resting order not found" failures. Call from @BeforeEach.
     */
    fun resetForTesting() {
        books.clear()
    }

    /**
     * Submits a new order: persists it, matches it against the book, and
     * records any resulting trades through the ledger. All in one
     * transaction  if the ledger rejects a fill partway through, the
     * whole submission rolls back rather than leaving a half-matched order.
     *
     * If anything fails after the in-memory book has already been mutated
     * (e.g. the ledger rejects a fill), the book for that symbol is evicted
     * and will be rebuilt from the database on the next order  so the
     * in-memory book can never end up referencing an order that isn't
     * actually persisted. See [rebuildBook].
     *
     * Book resolution (including any [rebuildBook]) happens BEFORE the new
     * order is persisted, so rebuildBook() can never query the DB and find
     * the order currently being submitted. Getting this ordering wrong is
     * what caused a symbol's very first order to be seeded into its own
     * book twice  once by rebuildBook(), once by book.submit()  silently
     * double-counting resting quantity.
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

        val lock = symbolLocks.computeIfAbsent(symbol) { Any() }
        val order: Order
        val trades = synchronized(lock) {
            // Resolve (and, if needed, rebuild) the book BEFORE this order
            // is persisted, so rebuildBook() can never see it. Previously
            // the save happened outside this lock entirely, ahead of the
            // book resolution  meaning a brand-new symbol's very first
            // order got seeded into its own book twice.
            val book = books.computeIfAbsent(symbol) { rebuildBook(symbol) }

            order = orderRepository.save(
                Order(
                    userId = userId,
                    symbol = symbol,
                    side = side,
                    type = type,
                    price = price,
                    quantity = quantity,
                )
            )

            val incoming = IncomingOrder(
                orderId = order.id,
                userId = userId,
                side = side,
                type = type,
                price = price,
                quantity = quantity,
            )

            try {
                val result = book.submit(incoming)
                applyFills(order, symbol, result.fills)
            } catch (e: Exception) {
                // The book may now hold state that won't be reflected in the
                // DB once this transaction rolls back (e.g. a resting order
                // that consumed part of a counter-order that will no longer
                // exist). Discard it  the next order for this symbol
                // rebuilds cleanly from persisted state instead of trusting
                // possibly-corrupted in-memory data.
                books.remove(symbol)
                throw e
            }
        }

        finalizeStatus(order)
        orderRepository.save(order)

        // Fired for every submission, matched or not  the book state
        // changed either way (a new resting order, a filled counter-order,
        // or both). The listener only acts once this transaction commits.
        eventPublisher.publishEvent(OrderProcessedEvent(symbol = symbol, trades = trades))

        return SubmissionResult(order = order, trades = trades)
    }

    /**
     * Thread-safe read of the current aggregated book state for [symbol].
     * Used by the WebSocket broadcaster to build the snapshot pushed to
     * clients  never exposes individual resting orders, only price/
     * quantity aggregates.
     */
    fun getOrderBookSnapshot(symbol: String, depth: Int = 20): OrderBookSnapshot {
        val lock = symbolLocks.computeIfAbsent(symbol) { Any() }
        return synchronized(lock) {
            val book = books.computeIfAbsent(symbol) { rebuildBook(symbol) }
            OrderBookSnapshot(
                symbol = symbol,
                bids = book.bidLevels(depth),
                asks = book.askLevels(depth),
            )
        }
    }

    /**
     * Reconstructs a symbol's order book from persisted OPEN/PARTIALLY_FILLED
     * LIMIT orders, oldest first (to preserve time priority). Called both
     * for a symbol's first use after startup, and to self-heal after a
     * failure that may have left the in-memory book inconsistent.
     */
    private fun rebuildBook(symbol: String): OrderBook {
        val book = OrderBook(symbol)
        orderRepository.findBySymbolAndStatusIn(
            symbol,
            listOf(OrderStatus.OPEN, OrderStatus.PARTIALLY_FILLED)
        )
            .filter { it.type == OrderType.LIMIT && it.remainingQuantity > BigDecimal.ZERO }
            .sortedBy { it.createdAt }
            .forEach { book.seedResting(it.id, it.userId, it.side, it.price!!, it.remainingQuantity) }
        return book
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