CREATE TABLE ledger_entries (
  entry_id    VARCHAR(64) PRIMARY KEY,
  group_id    VARCHAR(64) NOT NULL,
  request_id  VARCHAR(80) NOT NULL REFERENCES sandbox_orders(request_id),
  account     VARCHAR(40) NOT NULL,
  direction   VARCHAR(6) NOT NULL CHECK (direction IN ('DEBIT','CREDIT')),
  amount      NUMERIC(12,2) NOT NULL CHECK (amount > 0),
  currency    CHAR(3) NOT NULL,
  memo        VARCHAR(200) NOT NULL,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_ledger_request ON ledger_entries (request_id, created_at);
CREATE INDEX idx_ledger_group ON ledger_entries (group_id);

CREATE TABLE outbox_events (
  event_id     VARCHAR(64) PRIMARY KEY,
  aggregate    VARCHAR(40) NOT NULL,
  aggregate_id VARCHAR(80) NOT NULL,
  type         VARCHAR(60) NOT NULL,
  payload      JSONB NOT NULL,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  published_at TIMESTAMPTZ
);
CREATE INDEX idx_outbox_unpublished ON outbox_events (created_at) WHERE published_at IS NULL;
