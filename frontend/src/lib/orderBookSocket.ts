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
 * One-shot fetch of the current order book. Called on every connect
 * (including reconnects) — broadcasts only carry state changes going
 * forward, so without this a client would see an empty/stale book until
 * the next trade happens anywhere in the market.
 */
async function fetchOrderBookSnapshot(symbol: string): Promise<OrderBookSnapshot> {
  const res = await fetch(`/orderbook/${symbol}`);
  if (!res.ok) {
    throw new Error(`Failed to fetch order book snapshot for ${symbol}`);
  }
  return res.json() as Promise<OrderBookSnapshot>;
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

      // Fetch current state immediately — see fetchOrderBookSnapshot's doc
      // comment. This runs on the initial connect and every reconnect.
      fetchOrderBookSnapshot(symbol)
        .then((snapshot) => {
          if (!disposed) handlers.onOrderBook(snapshot);
        })
        .catch(() => {
          // Non-fatal — the next broadcast will still arrive and update
          // the UI; this just means a brief stale/empty view until then.
        });

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