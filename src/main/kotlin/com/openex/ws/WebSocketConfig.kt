package com.openex.ws

import org.springframework.context.annotation.Configuration
import org.springframework.messaging.simp.config.MessageBrokerRegistry
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker
import org.springframework.web.socket.config.annotation.StompEndpointRegistry
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer

/**
 * Enables STOMP messaging over WebSocket.
 *
 * Clients connect to /ws (with SockJS fallback for environments that block
 * raw WebSockets) and subscribe to topics under /topic — e.g.
 * /topic/orderbook/BTC-USD for book snapshots, /topic/trades/BTC-USD for
 * the live trade feed. The server never receives messages FROM clients on
 * /app in this iteration — order submission still goes through the
 * existing REST API (POST /orders). This keeps a clean separation: REST
 * for commands (submit an order), WebSocket for one-way live state
 * (watch the book).
 */
@Configuration
@EnableWebSocketMessageBroker
class WebSocketConfig : WebSocketMessageBrokerConfigurer {

    override fun configureMessageBroker(registry: MessageBrokerRegistry) {
        registry.enableSimpleBroker("/topic")
        registry.setApplicationDestinationPrefixes("/app")
    }

    override fun registerStompEndpoints(registry: StompEndpointRegistry) {
        registry
            .addEndpoint("/ws")
            .setAllowedOriginPatterns("*") // tightened once the frontend's real origin is known
            .withSockJS()
    }
}
