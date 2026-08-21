import { useState } from "react";
import { login, register, type AuthSession } from "../lib/authApi";

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
        minHeight: "100%",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        padding: 24,
      }}
    >
      <div className="panel" style={{ width: 360, display: "flex", flexDirection: "column", gap: 16 }}>
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
            {submitting ? "TRANSMITTINGâ€¦" : mode === "login" ? "LOG IN" : "CREATE ACCOUNT"}
          </button>
        </form>
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
