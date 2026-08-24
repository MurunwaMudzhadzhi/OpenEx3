import { useEffect, useRef, useState } from "react";
import {
  Chart,
  LineController,
  LineElement,
  PointElement,
  LinearScale,
  CategoryScale,
  Tooltip,
} from "chart.js";

Chart.register(LineController, LineElement, PointElement, LinearScale, CategoryScale, Tooltip);

interface PricePoint {
  timestamp: number;
  price: number;
}

const POLL_INTERVAL_MS = 60000;
const MAX_POINTS = 180;

export default function RealPriceChartPanel() {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const chartRef = useRef<Chart | null>(null);
  const [history, setHistory] = useState<PricePoint[]>([]);
  const [change24h, setChange24h] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);

  // Seed with real recent history once on mount.
  useEffect(() => {
    let cancelled = false;
    fetch("https://api.coingecko.com/api/v3/coins/bitcoin/market_chart?vs_currency=usd&days=1")
      .then((res) => {
        if (!res.ok) throw new Error("seed fetch failed");
        return res.json();
      })
      .then((data: { prices: [number, number][] }) => {
        if (cancelled || !data?.prices) return;
        const seeded = data.prices
          .slice(-MAX_POINTS)
          .map(([timestamp, price]) => ({ timestamp, price }));
        setHistory(seeded);
        setError(null);
      })
      .catch(() => {
        if (!cancelled) setError("unavailable");
      });
    return () => {
      cancelled = true;
    };
  }, []);

  // Keep it live: poll current price and append a point.
  useEffect(() => {
    let cancelled = false;

    function poll() {
      fetch("https://api.coingecko.com/api/v3/simple/price?ids=bitcoin&vs_currencies=usd&include_24hr_change=true")
        .then((res) => {
          if (!res.ok) throw new Error("poll failed");
          return res.json();
        })
        .then((data) => {
          if (cancelled || !data?.bitcoin) return;
          setChange24h(data.bitcoin.usd_24h_change);
          setHistory((prev) => {
            const next = [...prev, { timestamp: Date.now(), price: data.bitcoin.usd }];
            return next.slice(-MAX_POINTS);
          });
          setError(null);
        })
        .catch(() => {
          if (!cancelled) setError("unavailable");
        });
    }

    const interval = setInterval(poll, POLL_INTERVAL_MS);
    return () => {
      cancelled = true;
      clearInterval(interval);
    };
  }, []);

  useEffect(() => {
    if (history.length === 0 || !canvasRef.current) return;

    const labels = history.map((point) =>
      new Date(point.timestamp).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })
    );
    const prices = history.map((point) => point.price);

    if (chartRef.current) {
      chartRef.current.data.labels = labels;
      chartRef.current.data.datasets[0].data = prices;
      chartRef.current.update("none");
      return;
    }

    chartRef.current = new Chart(canvasRef.current, {
      type: "line",
      data: {
        labels,
        datasets: [
          {
            label: "BTC-USD (real)",
            data: prices,
            borderColor: "#e0a340",
            backgroundColor: "rgba(224, 163, 64, 0.08)",
            fill: true,
            pointRadius: 0,
            borderWidth: 1.5,
            tension: 0.2,
          },
        ],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        animation: false,
        scales: {
          x: { ticks: { color: "#8a93a6", maxTicksLimit: 6 }, grid: { display: false } },
          y: { ticks: { color: "#8a93a6" }, grid: { color: "rgba(255,255,255,0.05)" } },
        },
        plugins: { legend: { display: false } },
      },
    });
  }, [history]);

  useEffect(() => {
    return () => {
      chartRef.current?.destroy();
      chartRef.current = null;
    };
  }, []);

  const current = history.length > 0 ? history[history.length - 1].price : null;
  const up = change24h === null || change24h >= 0;

  return (
    <section className="panel" style={{ display: "flex", flexDirection: "column", gap: 8, minWidth: 420, flex: 2 }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "baseline" }}>
        <span className="eyebrow">BTC-USD LIVE (REAL)</span>
        <div style={{ display: "flex", gap: 10, alignItems: "baseline" }}>
          {current !== null && (
            <span className="tabular" style={{ fontSize: 14, fontWeight: 600, color: "var(--text-primary)" }}>
              ${current.toLocaleString(undefined, { maximumFractionDigits: 0 })}
            </span>
          )}
          {change24h !== null && (
            <span className="tabular" style={{ fontSize: 12, color: up ? "var(--phosphor)" : "var(--danger)" }}>
              {up ? "+" : ""}{change24h.toFixed(2)}%
            </span>
          )}
        </div>
      </div>

      {error && <p style={{ color: "var(--danger)", fontSize: 13 }}>{error}</p>}

      <div style={{ position: "relative", height: 150 }}>
        <canvas ref={canvasRef} />
      </div>
    </section>
  );
}
