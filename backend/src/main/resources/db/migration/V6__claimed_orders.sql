-- Recovering an order into a later session. Closing a tab used to orphan that session's
-- records (the session key lived in sessionStorage). Rather than rewriting ownership of
-- existing rows, a claim is an additive, auditable association: whoever holds the PayPal
-- order ID may attach that one order to their current session's receipts. The original
-- owner_hash is never changed, and the claim can be removed without touching the order.
CREATE TABLE claimed_orders (
  order_request_id VARCHAR(80) NOT NULL REFERENCES sandbox_orders(request_id),
  claimant_hash    VARCHAR(64) NOT NULL,
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (order_request_id, claimant_hash)
);
CREATE INDEX idx_claimed_orders_claimant ON claimed_orders (claimant_hash);
