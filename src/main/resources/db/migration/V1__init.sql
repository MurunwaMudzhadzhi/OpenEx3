-- OpenEx 3.0 — Core schema
-- Money is always NUMERIC, never float/double. Every trade produces balanced
-- ledger entries (debit + credit sum to zero) tied to the same trade_id.

-- Needed for gen_random_uuid() below — must be created before it's used.
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE users (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE accounts (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID NOT NULL REFERENCES users(id),
    asset      VARCHAR(20) NOT NULL,           -- e.g. 'USD', 'BTC'
    balance    NUMERIC(28, 8) NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, asset),
    CONSTRAINT chk_balance_non_negative CHECK (balance >= 0)
);

CREATE TYPE order_side AS ENUM ('BUY', 'SELL');
CREATE TYPE order_type AS ENUM ('LIMIT', 'MARKET');
CREATE TYPE order_status AS ENUM ('OPEN', 'PARTIALLY_FILLED', 'FILLED', 'CANCELLED', 'REJECTED');

CREATE TABLE orders (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID NOT NULL REFERENCES users(id),
    symbol           VARCHAR(20) NOT NULL,     -- e.g. 'BTC-USD'
    side             order_side NOT NULL,
    type             order_type NOT NULL,
    price            NUMERIC(28, 8),           -- NULL for MARKET orders
    quantity         NUMERIC(28, 8) NOT NULL CHECK (quantity > 0),
    filled_quantity  NUMERIC(28, 8) NOT NULL DEFAULT 0,
    status           order_status NOT NULL DEFAULT 'OPEN',
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_limit_has_price CHECK (type <> 'LIMIT' OR price IS NOT NULL),
    CONSTRAINT chk_filled_not_over CHECK (filled_quantity <= quantity)
);

CREATE INDEX idx_orders_book ON orders (symbol, side, status, price, created_at);
CREATE INDEX idx_orders_user ON orders (user_id);

CREATE TABLE trades (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    symbol        VARCHAR(20) NOT NULL,
    buy_order_id  UUID NOT NULL REFERENCES orders(id),
    sell_order_id UUID NOT NULL REFERENCES orders(id),
    price         NUMERIC(28, 8) NOT NULL CHECK (price > 0),
    quantity      NUMERIC(28, 8) NOT NULL CHECK (quantity > 0),
    executed_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_trades_symbol ON trades (symbol, executed_at);

CREATE TYPE ledger_direction AS ENUM ('DEBIT', 'CREDIT');

CREATE TABLE ledger_entries (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id    UUID NOT NULL REFERENCES accounts(id),
    trade_id      UUID NOT NULL REFERENCES trades(id),
    direction     ledger_direction NOT NULL,
    amount        NUMERIC(28, 8) NOT NULL CHECK (amount > 0),
    balance_after NUMERIC(28, 8) NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_ledger_account ON ledger_entries (account_id, created_at);
CREATE INDEX idx_ledger_trade ON ledger_entries (trade_id);

-- Idempotency: one row per Idempotency-Key header seen on order submission.
-- Store the response so a retried request with the same key gets the same
-- response instead of being reprocessed.
CREATE TABLE idempotency_keys (
    key           VARCHAR(255) PRIMARY KEY,
    request_hash  VARCHAR(64) NOT NULL,
    response_body TEXT,
    status_code   INT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
