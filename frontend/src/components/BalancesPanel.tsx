import { useEffect, useState } from "react";
import { fetchMyBalances, type Balance } from "../lib/accountApi";

interface BalancesPanelProps {
  token: string;
  /** Bumped by the parent after a successful order fill, so balances refresh without polling. */
  refreshSignal: number;
}

export default function BalancesPanel({ token, refreshSignal }: BalancesPanelProps) {
  const [balances, setBalances] = useState<Balance[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    setError(null);

    fetchMyBalances(token)
      .then((result) => {
        if (!cancelled) setBalances(result);
      })
      .catch((err) => {
        if (!cancelled) setError(err instanceof Error ? err.message : "Failed to load balances");
      });

    return () => {
      cancelled = true;
    };
  }, [token, refreshSignal]);

  return (
    <section className="panel" style={{ display: "flex", flexDirection: "column", gap: 8, minWidth: 200 }}>
      <span className="eyebrow">MY BALANCES</span>

      {error && <p style={{ color: "var(--danger)", fontSize: 13 }}>{error}</p>}

      {!error && !balances && (
        <p style={{ color: "var(--text-faint)", fontSize: 13 }}>Loading…</p>
      )}

      {balances && balances.length === 0 && (
        <p style={{ color: "var(--text-faint)", fontSize: 13 }}>No funded accounts yet.</p>
      )}

      {balances?.map((b) => (
        <div key={b.asset} style={{ display: "flex", justifyContent: "space-between", fontSize: 14, padding: "3px 0" }}>
          <span style={{ color: "var(--text-secondary)" }}>{b.asset}</span>
          <span className="tabular" style={{ color: "var(--text-primary)", fontWeight: 600 }}>{b.balance}</span>
        </div>
      ))}
    </section>
  );
}
