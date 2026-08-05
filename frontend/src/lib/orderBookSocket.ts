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
  // Guards against React StrictMode's extra mount/unmount/remount cycle in
  // development: without this, a stale callback from the first (disposed)
  // client can fire after a second client has already connected, incorrectly
  // flipping connection state back to false.
  let disposed = false;

  const client = new Client({
    webSocketFactory: () => new SockJS("/ws") as unknown as WebSocket,
    reconnectDelay: 3000, // auto-reconnect if the connection drops
    onConnect: () => {
      if (disposed) return;
      handlers.onConnectionChange(true);

      client.subscribe(`/topic/orderbook/${symbol}`, (message: IMessage) => {
        if (disposed) return;
        handlers.onOrderBook(JSON.parse(message.body) as OrderBookSnapshot);
      });

      client.subscribe(`/topic/trades/${symbol}`, (message: IMessage) => {
        if (disposed) return;
        handlers.onTrade(JSON.parse(message.body) as TradeBroadcast);
      });
    },
    onDisconnect: () => {
      if (disposed) return;
      handlers.onConnectionChange(false);
    },
    onWebSocketClose: () => {
      if (disposed) return;
      handlers.onConnectionChange(false);
    },
  });

  client.activate();

  return () => {
    disposed = true;
    client.deactivate();
  };
}