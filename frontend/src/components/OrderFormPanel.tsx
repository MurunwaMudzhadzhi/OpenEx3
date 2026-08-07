import { useState } from "react";
import { submitOrder, type OrderSide, type OrderType } from "../lib/orderApi";

interface OrderFormPanelProps {
  symbol: string;
  token: string;
}

export default function OrderFormPanel({ symbol, token }: OrderFormPanelProps) {
  const [side, setSide] = useState<OrderSide>("BUY");
  const [type, setType] = useState<OrderType>("LIMIT");
  const [price, setPrice] = useState("");
  const [quantity, setQuantity] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [lastFilled, setLastFilled] = useState<string | null>(null);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setLastFilled(null);
    setSubmitting(true);

    try {
      const result = await submitOrder(token, {
        symbol,
        side,
        type,
        price: type === "LIMIT" ? price : undefined,
        quantity,
      });

      setLastFilled(`${result.status} — filled ${result.filledQuantity} / ${result.quantity}`);
      setQuantity("");
      if (type === "LIMIT") setPrice("");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Order submission failed");
    } finally {
      setSubmitting(false);
    }
  }

  const sideColor = side === "BUY" ? "var(--phosphor)" : "var(--danger)";

  return (
    <section className="panel" style={{ display: "flex", flexDirection: "column", gap: 12, minWidth: 260 }}>
      <span className="eyebrow">SUBMIT ORDER — {symbol}</span>

      <div style={{ display: "flex", gap: 2 }}>
        <button type="button" onClick={() => setSide("BUY")} style={sideTabStyle(side === "BUY", "var(--phosphor)")}>
          BUY
        </button>
        <button type="button" onClick={() => setSide("SELL")} style={sideTabStyle(side === "SELL", "var(--danger)")}>
          SELL
        </button>
      </div>

      <div style={{ display: "flex", gap: 2 }}>
        <button type="button" onClick={() => setType("LIMIT")} style={typeTabStyle(type === "LIMIT")}>
          LIMIT
        </button>
        <button type="button" onClick={() => setType("MARKET")} style={typeTabStyle(type === "MARKET")}>
          MARKET
        </button>
      </div>

      <form onSubmit={handleSubmit} style={{ display: "flex", flexDirection: "column", gap: 10 }}>
        {type === "LIMIT" && (
          <label style={{ display: "flex", flexDirection: "column", gap: 4 }}>
            <span className="eyebrow">PRICE</span>
            <input
              type="number"
              step="0.00000001"
              min="0.00000001"
              required
              value={price}
              onChange={(e) => setPrice(e.target.value)}
              style={inputStyle}
              placeholder="0.00"
            />
          </label>
        )}

        <label style={{ display: "flex", flexDirection: "column", gap: 4 }}>
          <span className="eyebrow">QUANTITY</span>
          <input
            type="number"
            step="0.00000001"
            min="0.00000001"
            required
            value={quantity}
            onChange={(e) => setQuantity(e.target.value)}
            style={inputStyle}
            placeholder="0.00"
          />
        </label>

        {error && (
          <div style={{ color: "var(--danger)", fontSize: 13, border: "1px solid var(--danger)", padding: "6px 10px" }}>
            {error}
          </div>
        )}

        {lastFilled && (
          <div style={{ color: "var(--phosphor)", fontSize: 13, border: "1px solid var(--phosphor-dim)", padding: "6px 10px" }}>
            {lastFilled}
          </div>
        )}

        <button
          type="submit"
          disabled={submitting}
          style={{
            marginTop: 2,
            padding: "10px 0",
            background: sideColor,
            color: "var(--bg-void)",
            fontFamily: "var(--font-mono)",
            fontWeight: 700,
            fontSize: 13,
            letterSpacing: "0.06em",
            border: "none",
            cursor: "pointer",
          }}
        >
          {submitting ? "SUBMITTING…" : `${side} ${type}`}
        </button>
      </form>
    </section>
  );
}

function sideTabStyle(active: boolean, activeColor: string): React.CSSProperties {
  return {
    flex: 1,
    padding: "8px 0",
    fontFamily: "var(--font-mono)",
    fontSize: 12,
    fontWeight: 700,
    letterSpacing: "0.08em",
    background: active ? "var(--bg-panel-raised)" : "transparent",
    color: active ? activeColor : "var(--text-secondary)",
    border: `1px solid ${active ? activeColor : "var(--line-bright)"}`,
    cursor: "pointer",
  };
}

function typeTabStyle(active: boolean): React.CSSProperties {
  return {
    flex: 1,
    padding: "6px 0",
    fontFamily: "var(--font-mono)",
    fontSize: 11,
    letterSpacing: "0.06em",
    background: active ? "var(--bg-panel-raised)" : "transparent",
    color: active ? "var(--amber)" : "var(--text-secondary)",
    border: "1px solid var(--line-bright)",
    cursor: "pointer",
  };
}

const inputStyle: React.CSSProperties = {
  background: "var(--bg-void)",
  border: "1px solid var(--line-bright)",
  color: "var(--text-primary)",
  fontFamily: "var(--font-mono)",
  fontSize: 14,
  padding: "8px 10px",
};
