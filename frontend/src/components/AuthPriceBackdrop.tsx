import { useEffect, useRef } from "react";

/**
 * Large, dimmed BTC price line drawn across the full background, behind
 * the particle grid. Fetched once on mount  this is decorative, not
 * live-polled, so it doesn't add to the auth screen's network chatter.
 */
const PIXEL_RATIO = Math.min(window.devicePixelRatio || 1, 2);

export default function AuthPriceBackdrop() {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext("2d");
    if (!ctx) return;

    let cancelled = false;

    function draw(prices: number[]) {
      if (!canvas || !ctx) return;
      const width = (canvas.width = canvas.offsetWidth * PIXEL_RATIO);
      const height = (canvas.height = canvas.offsetHeight * PIXEL_RATIO);

      const min = Math.min(...prices);
      const max = Math.max(...prices);
      const range = max - min || 1;
      const stepX = width / (prices.length - 1);

      ctx.clearRect(0, 0, width, height);
      ctx.beginPath();
      prices.forEach((price, i) => {
        const x = i * stepX;
        const y = height - ((price - min) / range) * (height * 0.7) - height * 0.15;
        if (i === 0) ctx.moveTo(x, y);
        else ctx.lineTo(x, y);
      });

      ctx.strokeStyle = "rgba(224, 163, 64, 0.16)";
      ctx.lineWidth = 2 * PIXEL_RATIO;
      ctx.shadowColor = "rgba(224, 163, 64, 0.35)";
      ctx.shadowBlur = 18 * PIXEL_RATIO;
      ctx.stroke();

      // soft fill under the line
      ctx.lineTo(width, height);
      ctx.lineTo(0, height);
      ctx.closePath();
      ctx.shadowBlur = 0;
      ctx.fillStyle = "rgba(224, 163, 64, 0.03)";
      ctx.fill();
    }

    fetch("https://api.coingecko.com/api/v3/coins/bitcoin/market_chart?vs_currency=usd&days=1")
      .then((res) => (res.ok ? res.json() : Promise.reject()))
      .then((data: { prices: [number, number][] }) => {
        if (cancelled || !data?.prices) return;
        draw(data.prices.map(([, price]) => price));
      })
      .catch(() => {
        // decorative only  fail silently, particle grid still shows
      });

    function onResize() {
      // redraw isn't critical here; last-drawn frame just scales via CSS
    }
    window.addEventListener("resize", onResize);

    return () => {
      cancelled = true;
      window.removeEventListener("resize", onResize);
    };
  }, []);

  return (
    <canvas
      ref={canvasRef}
      style={{
        position: "absolute",
        inset: 0,
        width: "100%",
        height: "100%",
        pointerEvents: "none",
      }}
    />
  );
}
