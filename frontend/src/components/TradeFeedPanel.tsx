import type { TradeBroadcast } from "../lib/orderBookSocket";

interface TradeFeedPanelProps {
  trades: TradeBroadcast[];
}

export default function TradeFeedPanel({ trades }: TradeFeedPanelProps) {
  return (
    <section className="panel" style={{ display: "flex", flexDirection: "column", gap: 8, minWidth: 280 }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "baseline" }}>
        <span className="eyebrow">TRADE FEED</span>
        <span className="eyebrow">{trades.length} SHOWN</span>
      </div>

      <div style={{ maxHeight: 340, overflowY: "auto", display: "flex", flexDirection: "column" }}>
        {trades.length === 0 ? (
          <p style={{ color: "var(--text-faint)", fontSize: 13, padding: "12px 8px" }}>
            No executions yet.
          </p>
        ) : (
          trades.map((trade, i) => (
            <div
              key={trade.tradeId}
              style={{
                display: "flex",
                justifyContent: "space-between",
                padding: "4px 8px",
                fontSize: 13,
                borderBottom: "1px solid var(--line-dim)",
                animation: i === 0 ? "flash-in 600ms ease-out" : undefined,
              }}
            >
              <span className="tabular" style={{ color: "var(--amber)", fontWeight: 600 }}>
                {trade.price}
              </span>
              <span className="tabular" style={{ color: "var(--text-secondary)" }}>
                {trade.quantity}
              </span>
              <span className="tabular" style={{ color: "var(--text-faint)" }}>
                {new Date(trade.executedAt).toLocaleTimeString("en-GB", { hour12: false })}
              </span>
            </div>
          ))
        )}
      </div>

      <style>{`
        @keyframes flash-in {
          from { background: rgba(224, 163, 64, 0.25); }
          to { background: transparent; }
        }
      `}</style>
    </section>
  );
}
