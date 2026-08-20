import { useState } from "react";
import { depositFunds } from "../lib/accountApi";

interface DepositPanelProps {
  token: string;
  onDeposited: () => void;
}

const ASSETS = ["USD", "BTC"];

export default function DepositPanel({ token, onDeposited }: DepositPanelProps) {
  const [asset, setAsset] = useState("USD");
  const [amount, setAmount] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function handleDeposit() {
    const parsed = Number(amount);
    if (!amount || Number.isNaN(parsed) || parsed <= 0) {
      setError("Enter a positive amount");
      return;
    }

    setSubmitting(true);
    setError(null);

    depositFunds(token, asset, amount)
      .then(() => {
        setAmount("");
        onDeposited();
      })
      .catch((err) => {
        setError(err instanceof Error ? err.message : "Deposit failed");
      })
      .finally(() => setSubmitting(false));
  }

  return (
    <section className="panel" style={{ display: "flex", flexDirection: "column", gap: 8, minWidth: 200 }}>
      <span className="eyebrow">DEPOSIT (SIMULATED)</span>

      <div style={{ display: "flex", gap: 6 }}>
        {ASSETS.map((a) => (
          <button
            key={a}
            onClick={() => setAsset(a)}
            style={{
              flex: 1,
              padding: "6px 0",
              background: asset === a ? "rgba(57,255,136,0.15)" : "transparent",
              border: asset === a ? "1px solid var(--accent, #39ff88)" : "1px solid var(--border, #333)",
              borderRadius: 6,
              color: "var(--text-primary)",
              cursor: "pointer",
              fontSize: 13,
              fontWeight: 600,
            }}
          >
            {a}
          </button>
        ))}
      </div>

      <input
        value={amount}
        onChange={(e) => setAmount(e.target.value)}
        onKeyDown={(e) => e.key === "Enter" && handleDeposit()}
        placeholder="0.00"
        style={{
          background: "rgba(255,255,255,0.05)",
          border: "1px solid var(--border, #333)",
          borderRadius: 6,
          padding: "6px 8px",
          color: "var(--text-primary)",
          fontSize: 14,
        }}
      />

      {error && <p style={{ color: "var(--danger)", fontSize: 12 }}>{error}</p>}

      <button
        onClick={handleDeposit}
        disabled={submitting}
        style={{
          background: "var(--accent, #39ff88)",
          border: "none",
          borderRadius: 6,
          padding: "8px 0",
          cursor: submitting ? "default" : "pointer",
          fontWeight: 600,
        }}
      >
        {submitting ? "Depositing..." : `Deposit ${asset}`}
      </button>
    </section>
  );
}
