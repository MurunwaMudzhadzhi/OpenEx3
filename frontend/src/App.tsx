import { useEffect, useState } from "react";
import {
  connectToOrderBook,
  type OrderBookSnapshot,
  type TradeBroadcast,
} from "./lib/orderBookSocket";
import { loadSession, clearSession, type AuthSession } from "./lib/authApi";
import Header from "./components/Header";
import OrderBookPanel from "./components/OrderBookPanel";
import TradeFeedPanel from "./components/TradeFeedPanel";
import AuthPanel from "./components/AuthPanel";
import OrderFormPanel from "./components/OrderFormPanel";
import BalancesPanel from "./components/BalancesPanel";
import OpenOrdersPanel from "./components/OpenOrdersPanel";

const SYMBOL = "BTC-USD";
const MAX_LOG_ENTRIES = 20;

export default function App() {
  const [session, setSession] = useState<AuthSession | null>(() => loadSession());
  const [connected, setConnected] = useState(false);
  const [snapshot, setSnapshot] = useState<OrderBookSnapshot | null>(null);
  const [tradeLog, setTradeLog] = useState<TradeBroadcast[]>([]);
  const [refreshSignal, setRefreshSignal] = useState(0);

  useEffect(() => {
    if (!session) return;

    const disconnect = connectToOrderBook(SYMBOL, {
      onConnectionChange: setConnected,
      onOrderBook: setSnapshot,
      onTrade: (trade) =>
        setTradeLog((prev) => [trade, ...prev].slice(0, MAX_LOG_ENTRIES)),
    });

    return disconnect;
  }, [session]);

  function handleLogout() {
    clearSession();
    setSession(null);
    setSnapshot(null);
    setTradeLog([]);
    setConnected(false);
  }

  if (!session) {
    return (
      <div style={{ minHeight: "100%" }}>
        <div className="scanlines" />
        <AuthPanel onAuthenticated={setSession} />
      </div>
    );
  }

  return (
    <div style={{ minHeight: "100%", display: "flex", flexDirection: "column" }}>
      <div className="scanlines" />
      <Header symbol={SYMBOL} connected={connected} email={session.email} onLogout={handleLogout} />

      <main
        style={{
          display: "flex",
          flexWrap: "wrap",
          gap: 20,
          padding: 24,
        }}
      >
        <OrderBookPanel symbol={SYMBOL} snapshot={snapshot} />
        <TradeFeedPanel trades={tradeLog} />
        <OrderFormPanel
          symbol={SYMBOL}
          token={session.token}
          onSubmitted={() => setRefreshSignal((n) => n + 1)}
        />
        <BalancesPanel token={session.token} refreshSignal={refreshSignal} />
        <OpenOrdersPanel token={session.token} refreshSignal={refreshSignal} />
      </main>
    </div>
  );
}
