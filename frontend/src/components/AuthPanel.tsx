import { useState } from "react";
import { login, register, type AuthSession } from "../lib/authApi";
import AuthBackground from "./AuthBackground";
import AuthPriceBackdrop from "./AuthPriceBackdrop";
import AuthPriceCard from "./AuthPriceCard";

interface AuthPanelProps {
  onAuthenticated: (session: AuthSession) => void;
}

type Mode = "login" | "register";

export default function AuthPanel({ onAuthenticated }: AuthPanelProps) {
  const [mode, setMode] = useState<Mode>("login");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [showAbout, setShowAbout] = useState(false);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      const session = mode === "login" ? await login(email, password) : await register(email, password);
      onAuthenticated(session);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Something went wrong");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div
      style={{
        position: "relative",
        minHeight: "100%",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        padding: 24,
        overflow: "hidden",
      }}
    >
      <AuthPriceBackdrop />
      <AuthBackground />

      <div
        style={{
          position: "relative",
          zIndex: 1,
          display: "flex",
          flexWrap: "wrap",
          gap: 20,
          alignItems: "flex-start",
          justifyContent: "center",
        }}
      >
        <div style={{ display: "flex", flexDirection: "column", gap: 12, width: 360 }}>
          <div className="panel" style={{ display: "flex", flexDirection: "column", gap: 16 }}>
            <div>
              <span style={{ fontSize: 18, fontWeight: 700, color: "var(--phosphor)", letterSpacing: "0.04em" }}>
                DARKPOOL // 3.0
              </span>
              <div className="eyebrow" style={{ marginTop: 4 }}>
                {mode === "login" ? "TERMINAL ACCESS" : "NEW OPERATOR REGISTRATION"}
              </div>
            </div>

            <div style={{ display: "flex", gap: 2 }}>
              <button
                type="button"
                onClick={() => { setMode("login"); setError(null); }}
                style={tabStyle(mode === "login")}
              >
                LOG IN
              </button>
              <button
                type="button"
                onClick={() => { setMode("register"); setError(null); }}
                style={tabStyle(mode === "register")}
              >
                REGISTER
              </button>
            </div>

            <form onSubmit={handleSubmit} style={{ display: "flex", flexDirection: "column", gap: 12 }}>
              <label style={{ display: "flex", flexDirection: "column", gap: 4 }}>
                <span className="eyebrow">EMAIL</span>
                <input
                  type="email"
                  required
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  style={inputStyle}
                  autoComplete="email"
                />
              </label>

              <label style={{ display: "flex", flexDirection: "column", gap: 4 }}>
                <span className="eyebrow">PASSWORD</span>
                <input
                  type="password"
                  required
                  minLength={mode === "register" ? 8 : undefined}
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  style={inputStyle}
                  autoComplete={mode === "login" ? "current-password" : "new-password"}
                />
                {mode === "register" && (
                  <span style={{ fontSize: 11, color: "var(--text-faint)" }}>Minimum 8 characters.</span>
                )}
              </label>

              {error && (
                <div style={{ color: "var(--danger)", fontSize: 13, border: "1px solid var(--danger)", padding: "6px 10px" }}>
                  {error}
                </div>
              )}

              <button type="submit" disabled={submitting} style={submitStyle}>
                {submitting ? "TRANSMITTING" : mode === "login" ? "LOG IN" : "CREATE ACCOUNT"}
              </button>
            </form>
          </div>

          <div className="panel" style={{ display: "flex", flexDirection: "column", gap: showAbout ? 10 : 0 }}>
            <button
              type="button"
              onClick={() => setShowAbout((v) => !v)}
              style={{
                display: "flex",
                justifyContent: "space-between",
                alignItems: "center",
                background: "transparent",
                border: "none",
                color: "var(--text-secondary)",
                fontFamily: "var(--font-mono)",
                fontSize: 11,
                letterSpacing: "0.08em",
                cursor: "pointer",
                padding: 0,
              }}
            >
              <span>ABOUT THIS TERMINAL</span>
              <span>{showAbout ? "\u2212" : "+"}</span>
            </button>

            {showAbout && (
              <div style={{ fontSize: 12.5, color: "var(--text-secondary)", lineHeight: 1.6 }}>
                <p style={{ margin: "0 0 8px" }}>
                  DarkPool is a simulated crypto/forex trading terminal built as a 15-day
                  capstone project  a real price-time-priority matching engine, a
                  double-entry ledger, and a WebSocket layer that streams the order book
                  and trade feed live, no polling.
                </p>
                <p style={{ margin: "0 0 8px" }}>
                  VEX, the assistant in the corner once you're in, runs on a local LLM
                  (Ollama) and can actually read your wallet balance through a tool call
                  to the backend  it isn't guessing.
                </p>
                <p style={{ margin: 0 }}>
                  Backend: Kotlin/Spring Boot. Frontend: React/Vite. AI: Python/FastAPI +
                  Ollama. Everything here is simulated  no real funds, no real exchange.
                </p>
              </div>
            )}
          </div>
        </div>

        <AuthPriceCard />
      </div>
    </div>
  );
}

function tabStyle(active: boolean): React.CSSProperties {
  return {
    flex: 1,
    padding: "8px 0",
    fontFamily: "var(--font-mono)",
    fontSize: 12,
    letterSpacing: "0.08em",
    background: active ? "var(--bg-panel-raised)" : "transparent",
    color: active ? "var(--phosphor)" : "var(--text-secondary)",
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

const submitStyle: React.CSSProperties = {
  marginTop: 4,
  padding: "10px 0",
  background: "var(--phosphor-dim)",
  color: "var(--bg-void)",
  fontFamily: "var(--font-mono)",
  fontWeight: 700,
  fontSize: 13,
  letterSpacing: "0.06em",
  border: "none",
  cursor: "pointer",
};
