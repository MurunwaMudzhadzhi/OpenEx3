import { useEffect, useRef, useState } from "react";

interface PricePoint {
  timestamp: number;
  price: number;
}

const POLL_INTERVAL_MS = 60000;
const MAX_POINTS = 60;

export default function AuthPriceCard() {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const [history, setHistory] = useState<PricePoint[]>([]);
  const [change24h, setChange24h] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;

    function poll() {
      fetch("https://api.coingecko.com/api/v3/simple/price?ids=bitcoin&vs_currencies=usd&include_24hr_change=true")
        .then((res) => (res.ok ? res.json() : Promise.reject()))
        .then((data) => {
          if (cancelled || !data?.bitcoin) return;
          setChange24h(data.bitcoin.usd_24h_change);
          setHistory((prev) => [...prev, { timestamp: Date.now(), price: data.bitcoin.usd }].slice(-MAX_POINTS));
          setError(null);
        })
        .catch(() => {
          if (!cancelled) setError("unavailable");
        });
    }

    poll();
    const interval = setInterval(poll, POLL_INTERVAL_MS);
    return () => {
      cancelled = true;
      clearInterval(interval);
    };
  }, []);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas || history.length < 2) return;
    const ctx = canvas.getContext("2d");
    if (!ctx) return;

    const width = (canvas.width = canvas.offsetWidth * devicePixelRatio);
    const height = (canvas.height = canvas.offsetHeight * devicePixelRatio);
    const prices = history.map((h) => h.price);
    const min = Math.min(...prices);
    const max = Math.max(...prices);
    const range = max - min || 1;
    const stepX = width / (prices.length - 1);

    ctx.clearRect(0, 0, width, height);
    ctx.beginPath();
    prices.forEach((price, i) => {
      const x = i * stepX;
      const y = height - ((price - min) / range) * (height * 0.8) - height * 0.1;
      if (i === 0) ctx.moveTo(x, y);
      else ctx.lineTo(x, y);
    });
    ctx.strokeStyle = "#e0a340";
    ctx.lineWidth = 1.5 * devicePixelRatio;
    ctx.stroke();
  }, [history]);

  const current = history.length > 0 ? history[history.length - 1].price : null;
  const up = change24h === null || change24h >= 0;

  return (
    <div className="panel" style={{ width: 240, display: "flex", flexDirection: "column", gap: 10 }}>
      <span className="eyebrow">BTC-USD  LIVE</span>

      {error ? (
        <span style={{ fontSize: 12, color: "var(--text-faint)" }}>unavailable</span>
      ) : (
        <>
          <div style={{ display: "flex", alignItems: "baseline", gap: 8 }}>
            <span className="tabular" style={{ fontSize: 20, fontWeight: 700, color: "var(--text-primary)" }}>
              {current !== null ? `$${current.toLocaleString(undefined, { maximumFractionDigits: 0 })}` : ""}
            </span>
            {change24h !== null && (
              <span className="tabular" style={{ fontSize: 12, color: up ? "var(--phosphor)" : "var(--danger)" }}>
                {up ? "+" : ""}{change24h.toFixed(2)}%
              </span>
            )}
          </div>
          <div style={{ height: 50 }}>
            <canvas ref={canvasRef} style={{ width: "100%", height: "100%" }} />
          </div>
          <span style={{ fontSize: 10, color: "var(--text-faint)" }}>real market data  not simulated</span>
        </>
      )}
    </div>
  );
}
