# OpenEx 3.0 — Backend

Kotlin + Spring Boot core: matching engine, double-entry ledger, order API.

## Quickstart

```bash
docker compose up --build
```

Postgres runs on `5432`, backend on `8080`. Flyway migrations in
`src/main/resources/db/migration` run automatically on startup.

## Day 1–2 checklist

- [x] Gradle project (Kotlin, Spring Boot 3.3, Java 21 toolchain)
- [x] Docker Compose: postgres + backend, with healthchecks
- [x] Flyway `V1__init.sql`: users, accounts, orders, trades,
      ledger_entries, idempotency_keys
- [x] `Account` / `LedgerEntry` entities + repositories (ledger service
      built on these next)
- [ ] Confirm `docker compose up --build` boots clean and Flyway applies
      V1 with no errors
- [ ] Write first test: insert a user + account via repository, assert
      balance defaults to 0 and the non-negative constraint rejects a
      bad update

## Schema notes

- All money columns are `NUMERIC(28,8)` — never float/double.
- `ledger_entries.trade_id` ties debit + credit rows together; the
  ledger service must enforce (in a transaction) that entries for a
  trade net to zero — this isn't yet a DB-level CHECK, since Postgres
  can't easily assert "the sum of *other rows*" in a column
  constraint. Consider a `AFTER INSERT` trigger later if you want a
  DB-level guarantee on top of the application-level check.
- `orders.status` progression: OPEN → PARTIALLY_FILLED → FILLED, or
  → CANCELLED / REJECTED.
- `idempotency_keys` stores the full response so a retried POST with
  the same key returns the original result instead of reprocessing.

## Next (Day 2–3)

Build `LedgerService.recordTrade(trade)` — atomic, balanced
debit/credit write — and test it in isolation before the matching
engine depends on it.
