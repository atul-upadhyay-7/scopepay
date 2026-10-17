CREATE TABLE scope_drafts (
  scope_id    VARCHAR(64) PRIMARY KEY,
  owner_hash  VARCHAR(64) NOT NULL,
  state       VARCHAR(40) NOT NULL,
  summary     TEXT NOT NULL,
  deliverables JSONB NOT NULL,
  questions   JSONB NOT NULL,
  conflicts   JSONB NOT NULL,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  reviewed_at TIMESTAMPTZ
);
CREATE INDEX idx_scope_drafts_owner ON scope_drafts (owner_hash, created_at DESC);

CREATE TABLE sandbox_orders (
  request_id  VARCHAR(80) PRIMARY KEY,
  owner_hash  VARCHAR(64) NOT NULL,
  order_id    VARCHAR(40) UNIQUE,
  state       VARCHAR(40) NOT NULL,
  scope_id    VARCHAR(64),
  capture_id  VARCHAR(40),
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  completed_at TIMESTAMPTZ
);
CREATE INDEX idx_sandbox_orders_owner ON sandbox_orders (owner_hash, created_at DESC);
