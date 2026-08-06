import type { OrderBookSnapshot, PriceLevel } from "../lib/orderBookSocket";

interface OrderBookPanelProps {
  symbol: string;
  snapshot: OrderBookSnapshot | null;
}

const ROWS = 8;

function toNum(v: string): number {
  const n = Number(v);
  return Number.isFinite(n) ? n : 0;
}

function DepthRow({
  level,
  maxQty,
  side,
}: {
  level: PriceLevel;
  maxQty: number;
  side: "bid" | "ask";
}) {
  const qty = toNum(level.quantity);
  const pct = maxQty > 0 ? Math.min(100, (qty / maxQty) * 100) : 0;
  const color = side === "bid" ? "var(--phosphor)" : "var(--amber)";

  return (
    <div style={{ position: "relative", display: "flex", justifyContent: "space-between", padding: "3px 8px", fontSize: 13 }}>
      <div
        aria-hidden
        style={{
          position: "absolute",
          top: 0,
          bottom: 0,
          [side === "bid" ? "right" : "left"]: 0,
          width: `${pct}%`,
          background: side === "bid" ? "rgba(74, 222, 154, 0.12)" : "rgba(224, 163, 64, 0.12)",
        }}
      />
      <span className="tabular" style={{ position: "relative", color, fontWeight: 600 }}>
        {level.price}
      </span>
      <span className="tabular" style={{ position: "relative", color: "var(--text-secondary)" }}>
        {level.quantity}
      </span>
    </div>
  );
}

export default function OrderBookPanel({ symbol, snapshot }: OrderBookPanelProps) {
  const bids = (snapshot?.bids ?? []).slice(0, ROWS);
  const asks = (snapshot?.asks ?? []).slice(0, ROWS).reverse();

  const maxQty = Math.max(
    1,
    ...bids.map((b) => toNum(b.quantity)),
    ...asks.map((a) => toNum(a.quantity))
  );

  const bestBid = bids.length ? toNum(bids[0].price) : null;
  const bestAsk = asks.length ? toNum(asks[asks.length - 1].price) : null;
  const spread = bestBid !== null && bestAsk !== null ? bestAsk - bestBid : null;

  return (
    <section className="panel" style={{ display: "flex", flexDirection: "column", gap: 8, minWidth: 320 }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "baseline" }}>
        <span className="eyebrow">ORDER BOOK — {symbol}</span>
        <span className="eyebrow">DEPTH</span>
      </div>

      {!snapshot ? (
        <p style={{ color: "var(--text-faint)", fontSize: 13, padding: "12px 8px" }}>
          No book data yet — awaiting first order for {symbol}.
        </p>
      ) : (
        <>
          <div>
            {asks.map((level) => (
              <DepthRow key={`ask-${level.price}`} level={level} maxQty={maxQty} side="ask" />
            ))}
          </div>

          <div
            style={{
              display: "flex",
              justifyContent: "center",
              alignItems: "center",
              gap: 8,
              padding: "6px 0",
              borderTop: "1px dashed var(--line-bright)",
              borderBottom: "1px dashed var(--line-bright)",
            }}
          >
            <span style={{ color: "var(--text-faint)" }}>◈</span>
            <span className="tabular eyebrow" style={{ color: "var(--text-secondary)" }}>
              SPREAD {spread !== null ? spread.toFixed(2) : "—"}
            </span>
            <span style={{ color: "var(--text-faint)" }}>◈</span>
          </div>

          <div>
            {bids.map((level) => (
              <DepthRow key={`bid-${level.price}`} level={level} maxQty={maxQty} side="bid" />
            ))}
          </div>
        </>
      )}
    </section>
  );
}
