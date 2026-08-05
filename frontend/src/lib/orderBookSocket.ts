import { Client, type IMessage } from "@stomp/stompjs";
import SockJS from "sockjs-client";

export interface PriceLevel {
  price: string;
  quantity: string;
}

export interface OrderBookSnapshot {
  symbol: string;
  bids: PriceLevel[];
  asks: PriceLevel[];
}

export interface TradeBroadcast {
  tradeId: string;
  symbol: string;
  price: string;
  quantity: string;
  executedAt: string;
}

export interface OpenExSocketHandlers {
  onOrderBook: (snapshot: OrderBookSnapshot) => void;
  onTrade: (trade: TradeBroadcast) => void;
  onConnectionChange: (connected: boolean) => void;
}

/**
 * Connects to the backend's STOMP-over-SockJS endpoint and subscribes to
 * a single symbol's order book + trade topics.
 *
 * Returns a disconnect function — call it on component unmount to avoid
 * leaking the connection.
 */
export function connectToOrderBook(symbol: string, handlers: OpenExSocketHandlers): () => void {
  const client = new Client({
    webSocketFactory: () => new SockJS("/ws") as unknown as WebSocket,
    reconnectDelay: 3000, // auto-reconnect if the connection drops
    onConnect: () => {
      handlers.onConnectionChange(true);

      client.subscribe(`/topic/orderbook/${symbol}`, (message: IMessage) => {
        handlers.onOrderBook(JSON.parse(message.body) as OrderBookSnapshot);
      });

      client.subscribe(`/topic/trades/${symbol}`, (message: IMessage) => {
        handlers.onTrade(JSON.parse(message.body) as TradeBroadcast);
      });
    },
    onDisconnect: () => handlers.onConnectionChange(false),
    onWebSocketClose: () => handlers.onConnectionChange(false),
  });

  client.activate();

  return () => client.deactivate();
}
