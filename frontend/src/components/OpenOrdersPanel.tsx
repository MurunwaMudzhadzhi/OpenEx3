import { useEffect, useState } from "react";
import { fetchMyOpenOrders, type OpenOrder } from "../lib/accountApi";

interface OpenOrdersPanelProps {
  token: string;
  /** Bumped by the parent after a successful submission, so the list refreshes without polling. */
  refreshSignal: number;
}

export default function OpenOrdersPanel({ token, refreshSignal }: OpenOrdersPanelProps) {
  const [orders, setOrders] = useState<OpenOrder[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;

    fetchMyOpenOrders(token)
      .then((result) => {
        if (!cancelled) setOrders(result);
      })
      .catch((err) => {
        if (!cancelled) setError(err instanceof Error ? err.message : "Failed to load open orders");
      });

    return () => {
      cancelled = true;
    };
  }, [token, refreshSignal]);

  return (
    <section className="panel" style={{ display: "flex", flexDirection: "column", gap: 8, minWidth: 280 }}>
      <span className="eyebrow">MY OPEN ORDERS</span>

      {error && <p style={{ color: "var(--danger)", fontSize: 13 }}>{error}</p>}

      {!error && !orders && (
        <p style={{ color: "var(--text-faint)", fontSize: 13 }}>Loading…</p>
      )}

      {orders && orders.length === 0 && (
        <p style={{ color: "var(--text-faint)", fontSize: 13 }}>No open orders.</p>
      )}

      {orders?.map((o) => {
        const sideColor = o.side === "BUY" ? "var(--phosphor)" : "var(--danger)";
        return (
          <div
            key={o.orderId}
            style={{
              display: "flex",
              justifyContent: "space-between",
              fontSize: 13,
              padding: "4px 0",
              borderBottom: "1px solid var(--line-dim)",
            }}
          >
            <span style={{ color: sideColor, fontWeight: 700 }}>{o.side}</span>
            <span style={{ color: "var(--text-secondary)" }}>{o.type}</span>
            <span className="tabular" style={{ color: "var(--amber)" }}>{o.price ?? "MKT"}</span>
            <span className="tabular" style={{ color: "var(--text-secondary)" }}>
              {o.filledQuantity}/{o.quantity}
            </span>
          </div>
        );
      })}
    </section>
  );
}
