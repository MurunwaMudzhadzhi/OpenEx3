import { useState } from "react";
import { sendChatMessage } from "../lib/chatApi";

interface ChatMessage {
  role: "user" | "assistant";
  text: string;
}

interface ChatPanelProps {
  token?: string;
}

export default function ChatPanel({ token }: ChatPanelProps) {
  const [open, setOpen] = useState(false);
  const [input, setInput] = useState("");
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [sending, setSending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function handleSend() {
    const text = input.trim();
    if (!text || sending) return;

    setMessages((prev) => [...prev, { role: "user", text }]);
    setInput("");
    setSending(true);
    setError(null);

    sendChatMessage(text, token)
      .then((res) => {
        setMessages((prev) => [...prev, { role: "assistant", text: res.reply }]);
      })
      .catch((err) => {
        setError(err instanceof Error ? err.message : "Failed to reach the assistant");
      })
      .finally(() => setSending(false));
  }

  if (!open) {
    return (
      <button
        onClick={() => setOpen(true)}
        className="panel"
        style={{
          position: "fixed",
          bottom: 24,
          right: 24,
          borderRadius: 999,
          padding: "12px 20px",
          cursor: "pointer",
          color: "var(--text-primary)",
          fontWeight: 600,
        }}
      >
         ASK VEX
      </button>
    );
  }

  return (
    <section
      className="panel"
      style={{
        position: "fixed",
        bottom: 24,
        right: 24,
        width: 340,
        maxHeight: 480,
        display: "flex",
        flexDirection: "column",
        gap: 8,
        zIndex: 50,
      }}
    >
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
        <span className="eyebrow">VEX  AI ASSISTANT</span>
        <button
          onClick={() => setOpen(false)}
          style={{ background: "none", border: "none", color: "var(--text-secondary)", cursor: "pointer" }}
        >
          
          {"\u2715"}
          
        </button>      </div>      <div style={{ flex: 1, overflowY: "auto", display: "flex", flexDirection: "column", gap: 6, minHeight: 200, maxHeight: 320 }}>
        {messages.length === 0 && (
          <p style={{ color: "var(--text-faint)", fontSize: 13 }}>
            Ask about order types, the order book, or your balances.
          </p>
        )}
        {messages.map((m, i) => (
          <div
            key={i}
            style={{
              alignSelf: m.role === "user" ? "flex-end" : "flex-start",
              background: m.role === "user" ? "rgba(57,255,136,0.1)" : "rgba(125,95,255,0.1)",
              color: "var(--text-primary)",
              borderRadius: 8,
              padding: "6px 10px",
              fontSize: 13,
              maxWidth: "85%",
            }}
          >
            {m.text}
          </div>
        ))}
        {sending && <p style={{ color: "var(--text-faint)", fontSize: 13 }}>Vex is typing</p>}
      </div>

      {error && <p style={{ color: "var(--danger)", fontSize: 12 }}>{error}</p>}

      <div style={{ display: "flex", gap: 6 }}>
        <input
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={(e) => e.key === "Enter" && handleSend()}
          placeholder="Ask Vex..."
          style={{
            flex: 1,
            background: "rgba(255,255,255,0.05)",
            border: "1px solid var(--border, #333)",
            borderRadius: 6,
            padding: "6px 8px",
            color: "var(--text-primary)",
            fontSize: 13,
          }}
        />
        <button
          onClick={handleSend}
          disabled={sending}
          style={{
            background: "var(--accent, #39ff88)",
            border: "none",
            borderRadius: 6,
            padding: "6px 12px",
            cursor: sending ? "default" : "pointer",
            fontWeight: 600,
          }}
        >
          Send
        </button>
      </div>
    </section>
  );
}


