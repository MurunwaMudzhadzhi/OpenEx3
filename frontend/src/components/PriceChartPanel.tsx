import { useEffect, useRef, useState } from "react";
import {
  Chart,
  LineController,
  LineElement,
  PointElement,
  LinearScale,
  CategoryScale,
  Tooltip,
  Legend,
} from "chart.js";
import { fetchMarketData, type MarketDataResponse } from "../lib/marketApi";

Chart.register(LineController, LineElement, PointElement, LinearScale, CategoryScale, Tooltip, Legend);

const POLL_INTERVAL_MS = 5000;

export default function PriceChartPanel() {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const chartRef = useRef<Chart | null>(null);
  const [data, setData] = useState<MarketDataResponse | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;

    function poll() {
      fetchMarketData()
        .then((result) => {
          if (!cancelled) {
            setData(result);
            setError(null);
          }
        })
        .catch((err) => {
          if (!cancelled) setError(err instanceof Error ? err.message : "Failed to load market data");
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
    if (!data || !canvasRef.current) return;

    const labels = data.history.map((tick) =>
      new Date(tick.timestamp).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })
    );
    const prices = data.history.map((tick) => tick.price);
    const movingAverages = data.history.map((tick) => tick.moving_average);

    if (chartRef.current) {
      chartRef.current.data.labels = labels;
      chartRef.current.data.datasets[0].data = prices;
      chartRef.current.data.datasets[1].data = movingAverages;
      chartRef.current.update("none");
      return;
    }

    chartRef.current = new Chart(canvasRef.current, {
      type: "line",
      data: {
        labels,
        datasets: [
          {
            label: data.symbol,
            data: prices,
            borderColor: "#39ff88",
            backgroundColor: "transparent",
            pointRadius: 0,
            borderWidth: 1.5,
            tension: 0.15,
          },
          {
            label: "10-tick MA",
            data: movingAverages,
            borderColor: "#7d5fff",
            backgroundColor: "transparent",
            pointRadius: 0,
            borderWidth: 1,
            borderDash: [4, 3],
            tension: 0.15,
          },
        ],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        animation: false,
        scales: {
          x: { ticks: { color: "#8a93a6", maxTicksLimit: 8 }, grid: { color: "rgba(255,255,255,0.05)" } },
          y: { ticks: { color: "#8a93a6" }, grid: { color: "rgba(255,255,255,0.05)" } },
        },
        plugins: {
          legend: { labels: { color: "#8a93a6", boxWidth: 12, font: { size: 11 } } },
        },
      },
    });

    return () => {
      chartRef.current?.destroy();
      chartRef.current = null;
    };
  }, [data]);

  return (
    <section className="panel" style={{ display: "flex", flexDirection: "column", gap: 8, minWidth: 420, flex: 2 }}>
      <span className="eyebrow">
        {data ? `${data.symbol}  LIVE (SIMULATED)` : "MARKET CHART"}
      </span>

      {error && <p style={{ color: "var(--danger)", fontSize: 13 }}>{error}</p>}

      <div style={{ position: "relative", height: 260 }}>
        <canvas ref={canvasRef} />
      </div>

      {data && (
        <div style={{ display: "flex", justifyContent: "space-between", fontSize: 13 }}>
          <span style={{ color: "var(--text-secondary)" }}>Current</span>
          <span className="tabular" style={{ color: "var(--text-primary)", fontWeight: 600 }}>
            {data.current.price.toFixed(2)}
          </span>
        </div>
      )}
    </section>
  );
}
