# OpenEx 3.0 — Backend

Kotlin + Spring Boot core: matching engine, double-entry ledger, order
API with idempotency handling, and CI. This is the Core Execution Matrix
piece of OpenEx 3.0. WebSocket streaming, the React UI, and the Python AI
service are not part of this package yet (see Roadmap below).

## Quickstart

```bash
docker compose up --build
```

Postgres runs on `5432`, backend on `8080`. Flyway migrations in
`src/main/resources/db/migration` run automatically on startup.
`/actuator/health` is open (no auth) for local dev — see
`SecurityConfig.kt`, which is explicitly a temporary dev-only stand-in
for real JWT auth.

## Running tests

There's no Gradle wrapper committed yet, so run tests inside the same
Gradle/JDK image the Docker build uses — no local Java/Gradle install
needed:

```bash
docker run --rm -v ${PWD}:/app -w /app gradle:8.9-jdk21 gradle test --no-daemon
```

HTML report lands at `build/reports/tests/test/index.html`.

## What's built (Day 1–5)

**Day 1–2 — Foundation**
- Gradle project (Kotlin, Spring Boot 3.3, Java 21 toolchain)
- Docker Compose: postgres + backend, both with healthchecks
- Flyway `V1__init.sql`: `users`, `accounts`, `orders`, `trades`,
  `ledger_entries`, `idempotency_keys` — all money columns
  `NUMERIC(28,8)`, never float/double
- Dev-only `SecurityConfig` so `/actuator/health` etc. aren't blocked
  behind a rotating password while there's no real auth yet

**Day 2–3 — Double-entry ledger**
- `LedgerService.recordTrade(...)` — atomic, `@Transactional` write of
  4 balanced ledger entries per trade (debit+credit × base asset leg +
  quote asset leg)
- Throws `InsufficientBalanceException` and rolls back the whole
  transaction if either side can't cover their leg — no partial writes,
  no negative balances
- Tests: balanced entries, correct resulting balances, rejection with
  zero side-effects on insufficient funds (both directions)

**Day 3–5 — Matching engine**
- `OrderBook` — pure Kotlin, no Spring dependency: price-time priority
  matching for LIMIT and MARKET orders, partial fills, multi-level
  MARKET order walking, cancellation
- `MatchingEngine` — Spring service wiring the book to order/trade
  persistence and `LedgerService`, per-symbol synchronized so two
  orders for the same symbol never match concurrently, whole
  submission wrapped in one transaction
- Tests: 9 pure `OrderBook` unit tests (price/time priority, partial
  fills, no-liquidity market orders, cancellation) + 3 integration
  tests proving the full order → match → ledger → balance path

**Day 5–6 — Order API + Idempotency**
- `POST /orders` — accepts an order, validates it (LIMIT requires a price,
  MARKET must not have one, quantity/price must be positive), calls
  `MatchingEngine`, returns the resulting order + any trades
- `Idempotency-Key` header is required on every request. A retry with the
  same key and same request body replays the stored response instead of
  resubmitting the order. Reusing a key with a *different* body is
  rejected with `409` — that's client misuse, not something to silently
  overwrite
- Errors are structured JSON (`insufficient_balance`, `invalid_request`,
  `validation_error`, `idempotency_key_conflict`) with appropriate status
  codes, and are cached under the idempotency key too, so a retried
  failing request gets the same error back rather than being reprocessed
- Tests: happy path, duplicate-key replay (no second order created),
  key-reused-with-different-body rejection, and validation failures

**Day 6–7 — CI**
- `.github/workflows/ci.yml` — runs `gradle test` on every PR into `main`
  and on every push to `main`. Uploads the HTML test report as a build
  artifact regardless of pass/fail, so a failure is easy to inspect from
  the Actions tab without re-running locally.
- Pairs with GitHub branch protection on `main` (require PR + approval +
  this CI check to pass before merge is allowed)

## Known limitations (by design, for now)

- **Order book self-heals from the database.** On first use per symbol
  (including after a restart), and after any failure that could leave
  in-memory state inconsistent with the DB (e.g. a ledger rejection
  mid-match), the book is rebuilt from persisted `OPEN`/`PARTIALLY_FILLED`
  orders rather than trusting stale in-memory state. This was a real bug
  caught during manual testing — a rolled-back DB transaction had left a
  phantom resting order in memory that no longer existed in the database.
- **`ledger_entries` net-to-zero per trade** is enforced at the
  application layer (inside `LedgerService`), not as a DB constraint —
  Postgres can't easily express "sum of sibling rows = 0" in a column
  CHECK. Worth a DB-level trigger later as a second guarantee.

- **Idempotency-Key hashing is based on the parsed request object, not raw
  bytes.** Two requests that deserialize identically (even with different
  JSON whitespace/key order) are treated as the same request. This avoids
  needing a request-body-caching filter and is the right tradeoff here.
- **No JWT auth yet.** `/orders` currently trusts whatever `userId` the
  client sends in the request body — there's no token validating that the
  caller actually is that user. `SecurityConfig` is explicitly a
  temporary stand-in (see that file's comments).
- **A genuinely concurrent duplicate request (not a sequential retry) can
  still double-process.** If two requests with the same `Idempotency-Key`
  arrive close enough together, the second can see the key as "reserved
  but not yet completed" and proceed to also submit the order, rather
  than waiting for the first to finish. Sequential retries (the common
  case — a client resending after a timeout) are handled correctly; true
  in-flight concurrency is not. A stricter fix (short polling for
  completion, or rejecting the second request with a retryable status)
  is a reasonable follow-up if this becomes a real-world concern.

## Roadmap (not yet built)

- **Day 5–6 remainder**: real JWT auth (replacing the dev-only
  `SecurityConfig` and the trust-the-body `userId`)
- **Week 2**: WebSocket order book streaming + React trading UI
- **Week 3**: Python market simulator + Ollama/LangChain wallet
  assistant
