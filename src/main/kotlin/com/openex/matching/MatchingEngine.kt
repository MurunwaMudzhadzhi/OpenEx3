package com.openex.matching

import com.openex.ledger.AccountRepository
import com.openex.ledger.LedgerService
import com.openex.order.Order
import com.openex.order.OrderRepository
import com.openex.order.OrderStatus
import com.openex.order.OrderType
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
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
    transactionManager: PlatformTransactionManager,
) {
    // One OrderBook per symbol, created on first use.
    private val books = ConcurrentHashMap<String, OrderBook>()

    // One lock object per symbol  matching for a given symbol must never
    // run concurrently, since the in-memory book isn't thread-safe on its
    // own. Different symbols can match in parallel.
    private val symbolLocks = ConcurrentHashMap<String, Any>()

    // Manages the transaction manually (rather than via @Transactional)
    // so the whole DB transaction  not just the in-memory match  can
    // run inside the symbol lock. @Transactional is proxy-based: it
    // commits *after* the annotated method returns, which is always
    // after any synchronized block inside that method has already
    // released. That gap let a second order for the same symbol match
    // against a resting order whose DB row hadn't committed yet.
    private val transactionTemplate = TransactionTemplate(transactionManager)

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
     * records any resulting trades through the ledger.
     *
     * The symbol lock is held for the ENTIRE transaction  book
     * resolution, the DB save, matching, applyFills, and the commit
     * itself  not just the in-memory match. This guarantees that by the
     * time a second order for the same symbol can acquire the lock, every
     * DB row the first order touched is already committed and visible.
     * Holding the lock across a DB round-trip does serialize submissions
     * for the same symbol, but different symbols still match in parallel,
     * and correctness here matters more than that extra latency.
     *
     * If the transaction fails for any reason (ledger rejection, DB
     * error, etc.), the book for that symbol is evicted so it gets
     * rebuilt from persisted state on the next order  the in-memory
     * book can never end up holding a mutation from a transaction that
     * didn't actually commit. See [rebuildBook].
     */
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

        val result: SubmissionResult = synchronized(lock) {
            try {
                transactionTemplate.execute<SubmissionResult> {
                    // Resolve (and, if needed, rebuild) the book BEFORE
                    // this order is persisted, so rebuildBook() can never
                    // see it.
                    val book = books.computeIfAbsent(symbol) { rebuildBook(symbol) }

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

                    val incoming = IncomingOrder(
                        orderId = order.id,
                        userId = userId,
                        side = side,
                        type = type,
                        price = price,
                        quantity = quantity,
                    )

                    val matchResult = book.submit(incoming)
                    val trades = applyFills(order, symbol, matchResult.fills)

                    finalizeStatus(order)
                    orderRepository.save(order)

                    SubmissionResult(order = order, trades = trades)
                } ?: error("transactionTemplate.execute returned null")
            } catch (e: Exception) {
                // The book may now hold state that won't be reflected in
                // the DB, since this transaction didn't commit (e.g. the
                // ledger rejected a fill, or the commit itself failed).
                // Discard it  the next order for this symbol rebuilds
                // cleanly from persisted state instead of trusting
                // possibly-corrupted in-memory data.
                books.remove(symbol)
                throw e
            }
        }

        // Fired for every submission, matched or not  the book state
        // changed either way (a new resting order, a filled counter-order,
        // or both). Safe to fire after the synchronized block: the
        // transaction has already committed by this point.
        eventPublisher.publishEvent(OrderProcessedEvent(symbol = symbol, trades = result.trades))

        return result
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