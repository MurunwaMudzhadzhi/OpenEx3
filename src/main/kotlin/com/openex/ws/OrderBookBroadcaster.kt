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
 * Uses AFTER_COMMIT specifically: OrderProcessedEvent is published from
 * inside MatchingEngine.submit()'s @Transactional method, before Spring
 * knows whether that transaction will actually succeed. If we broadcast
 * immediately on the event (not waiting for commit), a rolled-back
 * transaction would still have pushed phantom state to every connected
 * client — the same class of consistency bug the order book self-healing
 * fix addressed for the in-memory book itself. AFTER_COMMIT guarantees we
 * only ever broadcast state that's genuinely persisted.
 */
@Component
class OrderBookBroadcaster(
    private val matchingEngine: MatchingEngine,
    private val messagingTemplate: SimpMessagingTemplate,
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
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
