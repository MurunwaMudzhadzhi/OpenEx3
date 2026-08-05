# OpenEx 3.0 — Frontend

React + Vite + TypeScript. This is Week 2's frontend piece, living
alongside the Kotlin backend in the same repo (`frontend/` vs. the
backend's `src/` at repo root — kept as siblings rather than restructuring
the already-merged backend into its own subfolder).

## Day 2 scope — WebSocket connectivity check

This is **not** the real dashboard yet. It's a minimal, unstyled proof
that the frontend can connect to the backend's WebSocket layer (built in
Week 2 Day 1) and receive live order book snapshots and trade events.

Day 3 replaces `App.tsx` with the actual dashboard UI — bid/ask tables,
proper styling — reusing the same `src/lib/orderBookSocket.ts` connection
module without changes.

## Running it

**1. Start the backend first** (from the repo root):
```bash
docker compose up --build
```

**2. In this `frontend/` folder, install and run:**
```bash
npm install
npm run dev
```

Open the URL Vite prints (usually `http://localhost:5173`). You should
see `Status: CONNECTED`. The dev server proxies `/ws` and `/orders` to
`localhost:8080`, so there's no CORS configuration needed during local
dev.

**3. To see it actually update**, submit an order against the backend
(see the main README's `POST /orders` section) for symbol `BTC-USD` —
the snapshot and trade log on the page update live, no refresh.

## Known items

- `npm audit` flags a moderate-severity esbuild advisory (dev server only,
  not a production risk) tied to the Vite 5.x line. Fixing it requires a
  breaking upgrade to Vite 8 — deferred rather than risking destabilizing
  Day 2's scaffold; worth revisiting before this goes anywhere near a real
  deployment.
- No production Docker service yet — `docker-compose.yml` doesn't include
  the frontend. That's Day 4's integration work.
