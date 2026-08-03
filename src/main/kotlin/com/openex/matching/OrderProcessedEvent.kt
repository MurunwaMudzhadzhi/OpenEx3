package com.openex.matching

/**
 * Published whenever MatchingEngine.submit() completes — successfully or
 * with trades, doesn't matter which. Listeners (the WebSocket broadcaster)
 * should use @TransactionalEventListener(phase = AFTER_COMMIT) so they only
 * fire once the DB transaction has actually committed. Broadcasting before
 * that point would risk pushing state to clients that later gets rolled
 * back — the same class of bug the order book self-healing fix addressed.
 */
data class OrderProcessedEvent(
    val symbol: String,
    val trades: List<Trade>,
)
