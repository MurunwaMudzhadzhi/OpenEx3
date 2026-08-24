import { useEffect, useRef } from "react";

/**
 * Subtle animated grid/particle background for the auth screen.
 * Plain canvas + requestAnimationFrame, no dependencies. Sits absolutely
 * positioned behind the login panel; the panel itself just needs
 * position: relative + a higher z-index from its parent.
 */
const PIXEL_RATIO = Math.min(window.devicePixelRatio || 1, 2);

export default function AuthBackground() {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext("2d");
    if (!ctx) return;

    let width = 0;
    let height = 0;
    let animationFrame: number;

    function resize() {
      width = canvas!.width = canvas!.offsetWidth * PIXEL_RATIO;
      height = canvas!.height = canvas!.offsetHeight * PIXEL_RATIO;
    }
    resize();
    window.addEventListener("resize", resize);

    const PARTICLE_COUNT = 55;
    const LINK_DISTANCE = 130 * PIXEL_RATIO;

    type Particle = { x: number; y: number; vx: number; vy: number };
    const particles: Particle[] = Array.from({ length: PARTICLE_COUNT }, () => ({
      x: Math.random() * width,
      y: Math.random() * height,
      vx: (Math.random() - 0.5) * 0.25 * PIXEL_RATIO,
      vy: (Math.random() - 0.5) * 0.25 * PIXEL_RATIO,
    }));

    function tick() {
      ctx!.clearRect(0, 0, width, height);

      // faint background grid
      ctx!.strokeStyle = "rgba(57, 255, 136, 0.035)";
      ctx!.lineWidth = 1;
      const gridSize = 42 * PIXEL_RATIO;
      for (let x = 0; x < width; x += gridSize) {
        ctx!.beginPath();
        ctx!.moveTo(x, 0);
        ctx!.lineTo(x, height);
        ctx!.stroke();
      }
      for (let y = 0; y < height; y += gridSize) {
        ctx!.beginPath();
        ctx!.moveTo(0, y);
        ctx!.lineTo(width, y);
        ctx!.stroke();
      }

      // particles + connecting lines
      for (const particle of particles) {
        particle.x += particle.vx;
        particle.y += particle.vy;
        if (particle.x < 0 || particle.x > width) particle.vx *= -1;
        if (particle.y < 0 || particle.y > height) particle.vy *= -1;

        ctx!.fillStyle = "rgba(57, 255, 136, 0.55)";
        ctx!.beginPath();
        ctx!.arc(particle.x, particle.y, 1.4 * PIXEL_RATIO, 0, Math.PI * 2);
        ctx!.fill();
      }

      for (let i = 0; i < particles.length; i++) {
        for (let j = i + 1; j < particles.length; j++) {
          const a = particles[i];
          const b = particles[j];
          const dx = a.x - b.x;
          const dy = a.y - b.y;
          const dist = Math.sqrt(dx * dx + dy * dy);
          if (dist < LINK_DISTANCE) {
            ctx!.strokeStyle = `rgba(57, 255, 136, ${0.12 * (1 - dist / LINK_DISTANCE)})`;
            ctx!.lineWidth = 1;
            ctx!.beginPath();
            ctx!.moveTo(a.x, a.y);
            ctx!.lineTo(b.x, b.y);
            ctx!.stroke();
          }
        }
      }

      animationFrame = requestAnimationFrame(tick);
    }
    tick();

    return () => {
      cancelAnimationFrame(animationFrame);
      window.removeEventListener("resize", resize);
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
