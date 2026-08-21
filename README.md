# OpenEx 3.0

A simulated crypto/forex trading terminal: Kotlin/Spring Boot backend
(matching engine + double-entry ledger), a React/Vite trading UI, and a
Python/FastAPI AI assistant (Ollama + LangChain-style tool-calling) that
can answer questions about your wallet using live data from the backend.

Built as a 15-day capstone. This README reflects the finished state 
see `frontend/README.md`'s history for how the UI evolved, and git log
for the day-by-day backend build-out.

## Quickstart (single command, cold start)

```bash
docker compose up -d
```

This builds and starts all four backend services with health-check
gating, so nothing starts before its dependencies are actually ready:

| Service     | Port  | Depends on (healthy)      |
|-------------|-------|----------------------------|
| postgres    | 5432  |                           |
| backend     | 8080  | postgres                   |
| ollama      |      |                           |
| ai-agent    | 8000  | ollama                     |

Then, in a separate terminal, start the frontend:

```bash
cd frontend
npm install
npm run dev
```

Open the URL Vite prints (`http://localhost:5173`).

**First-time AI chat setup:** the `ai-agent` container needs a model
pulled into Ollama before `/chat` will work (this is a one-time step,
persisted in the `openex_ollama_data` volume):

```bash
docker exec openex-ollama ollama pull llama3.2:1b
```

Until that completes, `openex-ai-agent`'s healthcheck will correctly
report unhealthy  this is intentional (`/ready` checks that a model is
actually available, not just that the process is running).

## What's built

**Backend (Kotlin/Spring Boot)**
- Double-entry ledger (`LedgerService`)  every trade writes 4 balanced
  `ledger_entries` rows (debit+credit  base asset + quote asset),
  atomically, inside one `@Transactional` boundary. Rejects with
  `InsufficientBalanceException` and rolls back entirely if either side
  can't cover their leg.
- Matching engine (`OrderBook` + `MatchingEngine`)  price-time priority
  matching for LIMIT and MARKET orders, partial fills, per-symbol
  synchronized so two orders for the same symbol never match
  concurrently. Order book self-heals from the DB on startup and after
  any failed transaction.
- JWT auth  `/auth/register` and `/auth/login` issue tokens;
  `JwtAuthenticationFilter` validates them on every protected route.
- `POST /accounts/deposit`  simulated funding faucet; auto-creates the
  account if it doesn't exist yet.
- `POST /orders`  accepts LIMIT/MARKET orders, validates them, returns
  the resulting order + any trades.
- WebSocket broadcasting (`/ws`, STOMP-over-SockJS)  every order
  submission publishes an event; a listener broadcasts the updated order
  book (`/topic/orderbook/{symbol}`) and any trades
  (`/topic/trades/{symbol}`) to subscribed clients, firing only
  `AFTER_COMMIT` so a rolled-back order is never broadcast as if it
  happened.

**Frontend (React/Vite/TypeScript)**
- Full trading dashboard: live order book, trade feed, balances panel,
  order submission form (BUY/SELL  LIMIT/MARKET), open orders list,
  deposit panel (USD/BTC), live price chart.
- Connects to the backend's WebSocket layer for live order book/trade
  updates  no polling, no manual refresh needed.
- Floating VEX AI assistant chat widget.

**AI agent (Python/FastAPI + Ollama)**
- `GET /api/market-data`  simulated market tick feed.
- `POST /chat`  LLM-backed assistant that can call a tool to fetch the
  user's real wallet balance from the Kotlin backend and answer
  questions about it.
- `GET /ready`  reports healthy only once Ollama is reachable *and* the
  configured model (`llama3.2:1b`) is actually pulled.

## Known limitations (by design, for now)

- **No fund-hold on order placement.** Placing a LIMIT order does not
  reserve/lock any balance  funds only move when a trade actually
  executes. A user can technically rest multiple orders whose combined
  value exceeds their balance; the shortfall is only caught at fill time
  via `InsufficientBalanceException`. Out of scope for this capstone.
- **`ledger_entries` net-to-zero per trade** is enforced at the
  application layer, not as a DB constraint.
- **Idempotency-Key hashing** is based on the parsed request object, not
  raw bytes  two requests that deserialize identically are treated as
  the same request, even with different JSON whitespace/key order.
- **A genuinely concurrent duplicate request** (not a sequential retry)
  can still double-process; sequential retries are handled correctly.

## Running backend tests

```bash
docker run --rm -v ${PWD}:/app -w /app gradle:8.9-jdk21 gradle test --no-daemon
```

HTML report lands at `build/reports/tests/test/index.html`.