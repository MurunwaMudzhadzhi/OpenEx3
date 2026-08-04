package com.openex.ws

import com.openex.matching.MatchingEngine
import com.openex.matching.OrderProcessedEvent
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * Bridges MatchingEngine's internal events to WebSocket broadcasts.
 *
 * MatchingEngine.submit() manages its own transaction manually (via
 * TransactionTemplate, not @Transactional) so the symbol lock can span the
 * entire DB transaction — see the comment on MatchingEngine.transactionTemplate
 * for why. That means OrderProcessedEvent is published AFTER the
 * transaction has already committed and closed, not from within an active
 * one.
 *
 * fallbackExecution = true is required because of that: by default,
 * @TransactionalEventListener silently drops an event if there's no active
 * transaction synchronization at publish time. Here there never will be —
 * the commit has already happened by the time we publish — so
 * fallbackExecution just tells Spring "run it immediately, synchronously,
 * right now" instead of waiting for a commit hook that will never fire.
 * The AFTER_COMMIT phase is still meaningful documentation of intent (only
 * broadcast state that's genuinely persisted), even though in practice
 * every invocation now takes the fallback path.
 */
@Component
class OrderBookBroadcaster(
    private val matchingEngine: MatchingEngine,
    private val messagingTemplate: SimpMessagingTemplate,
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    fun onOrderProcessed(event: OrderProcessedEvent) {
        val snapshot = matchingEngine.getOrderBookSnapshot(event.symbol)
        messagingTemplate.convertAndSend("/topic/orderbook/${event.symbol}", snapshot)

        event.trades.forEach { trade ->
            messagingTemplate.convertAndSend(
                "/topic/trades/${event.symbol}",
                TradeBroadcast(
                    tradeId = trade.id,
                    symbol = trade.symbol,
                    price = trade.price,
                    quantity = trade.quantity,
                    executedAt = trade.executedAt,
                )
            )
        }
    }
}
