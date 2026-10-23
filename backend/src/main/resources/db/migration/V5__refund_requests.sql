CREATE TABLE refund_requests (
  refund_request_id VARCHAR(64) PRIMARY KEY,
  owner_hash        VARCHAR(64) NOT NULL,
  order_request_id  VARCHAR(80) NOT NULL REFERENCES sandbox_orders(request_id),
  capture_id        VARCHAR(40) NOT NULL,
  amount            NUMERIC(12,2) NOT NULL,
  currency          VARCHAR(3) NOT NULL,
  reason            TEXT NOT NULL,
  delivery_text     TEXT NOT NULL,
  comparison        JSONB NOT NULL,
  state             VARCHAR(40) NOT NULL,
  paypal_refund_id  VARCHAR(40),
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  approved_at       TIMESTAMPTZ,
  completed_at      TIMESTAMPTZ
);
CREATE INDEX idx_refund_requests_owner ON refund_requests (owner_hash, created_at DESC);
CREATE INDEX idx_refund_requests_order ON refund_requests (order_request_id);
