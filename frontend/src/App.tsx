import { useEffect, useState } from "react";
import {
  connectToOrderBook,
  type OrderBookSnapshot,
  type TradeBroadcast,
} from "./lib/orderBookSocket";

const SYMBOL = "BTC-USD";
const MAX_LOG_ENTRIES = 20;

export default function App() {
  const [connected, setConnected] = useState(false);
  const [snapshot, setSnapshot] = useState<OrderBookSnapshot | null>(null);
  const [tradeLog, setTradeLog] = useState<TradeBroadcast[]>([]);

  useEffect(() => {
    const disconnect = connectToOrderBook(SYMBOL, {
      onConnectionChange: setConnected,
      onOrderBook: setSnapshot,
      onTrade: (trade) =>
        setTradeLog((prev) => [trade, ...prev].slice(0, MAX_LOG_ENTRIES)),
    });

    return disconnect;
  }, []);

  return (
    <div style={{ fontFamily: "monospace", padding: "1.5rem", maxWidth: 720 }}>
      <h1>OpenEx 3.0 — WebSocket connectivity check</h1>
      <p>
        Symbol: <strong>{SYMBOL}</strong> &nbsp;|&nbsp; Status:{" "}
        <strong style={{ color: connected ? "green" : "crimson" }}>
          {connected ? "CONNECTED" : "DISCONNECTED"}
        </strong>
      </p>
      <p style={{ color: "#666", fontSize: "0.9rem" }}>
        This is a Day 2 connectivity check, not the real dashboard — it just
        proves the WebSocket pipe works end-to-end. Submit an order via the
        backend API to see this update live.
      </p>

      <h2>Order book snapshot</h2>
      {snapshot ? (
        <pre style={{ background: "#f4f4f4", padding: "1rem" }}>
          {JSON.stringify(snapshot, null, 2)}
        </pre>
      ) : (
        <p>No snapshot received yet — submit an order for {SYMBOL} to trigger one.</p>
      )}

      <h2>Trade log (most recent {MAX_LOG_ENTRIES})</h2>
      {tradeLog.length === 0 ? (
        <p>No trades yet.</p>
      ) : (
        <ul>
          {tradeLog.map((trade) => (
            <li key={trade.tradeId}>
              {trade.quantity} @ {trade.price} — {new Date(trade.executedAt).toLocaleTimeString()}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
