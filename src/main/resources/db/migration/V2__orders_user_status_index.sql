-- Supports GET /orders (OrderController.listMyOpenOrders), which filters by
-- user + status and sorts by created_at descending. Without this, that
-- query degrades to a full table scan as order history grows.
CREATE INDEX idx_orders_user_status_created_at
    ON orders (user_id, status, created_at DESC);
