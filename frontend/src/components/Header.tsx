import { useEffect, useState } from "react";

interface HeaderProps {
  symbol: string;
  connected: boolean;
  email: string;
  onLogout: () => void;
}

export default function Header({ symbol, connected, email, onLogout }: HeaderProps) {
  const [now, setNow] = useState(() => new Date());

  useEffect(() => {
    const id = setInterval(() => setNow(new Date()), 1000);
    return () => clearInterval(id);
  }, []);

  return (
    <header
      style={{
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        padding: "14px 20px",
        borderBottom: "1px solid var(--line-bright)",
        background: "var(--bg-panel-raised)",
      }}
    >
      <div style={{ display: "flex", alignItems: "baseline", gap: 14 }}>
        <span style={{ fontSize: 18, fontWeight: 700, letterSpacing: "0.04em", color: "var(--phosphor)" }}>
          OPENEX // 3.0
        </span>
        <span className="eyebrow">DECENTRALIZED TRADING TERMINAL</span>
      </div>

      <div style={{ display: "flex", alignItems: "center", gap: 24 }}>
        <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
          <span className="eyebrow">SYMBOL</span>
          <span style={{ color: "var(--amber)", fontWeight: 700 }}>{symbol}</span>
        </div>

        <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
          <span
            aria-hidden
            style={{
              width: 8,
              height: 8,
              borderRadius: "50%",
              background: connected ? "var(--phosphor)" : "var(--danger)",
              boxShadow: connected ? "0 0 6px var(--phosphor)" : "0 0 6px var(--danger)",
            }}
          />
          <span style={{ color: connected ? "var(--phosphor)" : "var(--danger)", fontWeight: 600 }}>
            {connected ? "LINK ESTABLISHED" : "LINK DOWN"}
          </span>
        </div>

        <span className="tabular" style={{ color: "var(--text-secondary)" }}>
          {now.toLocaleTimeString("en-GB", { hour12: false })}
        </span>

        <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
          <span className="eyebrow" style={{ color: "var(--text-secondary)" }}>
            {email}
          </span>
          <button
            type="button"
            onClick={onLogout}
            style={{
              background: "transparent",
              border: "1px solid var(--line-bright)",
              color: "var(--text-secondary)",
              fontFamily: "var(--font-mono)",
              fontSize: 11,
              letterSpacing: "0.06em",
              padding: "4px 10px",
              cursor: "pointer",
            }}
          >
            LOG OUT
          </button>
        </div>
      </div>
    </header>
  );
}
