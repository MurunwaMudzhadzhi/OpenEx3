-- Supports GET /orders (OrderController.listMyOpenOrders), which filters by
-- user + status and sorts by created_at descending. Without this, that
-- query degrades to a full table scan as order history grows.
--
-- Deliberately NOT using CREATE INDEX CONCURRENTLY here: it avoids locking
-- the table against writes during the build, which matters once orders
-- has live write traffic, but it also can't run inside a transaction and
-- in practice hung indefinitely against this Dockerized Postgres setup
-- (waiting on some connection/lock state) rather than failing loudly. This
-- table has no production write traffic yet, so a brief lock during
-- startup migration is an acceptable tradeoff for a build that actually
-- completes. Revisit CONCURRENTLY once this runs against a real cluster
-- with concurrent order flow.
CREATE INDEX idx_orders_user_status_created_at
    ON orders (user_id, status, created_at DESC);
