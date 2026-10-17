# Architecture and trust boundaries

ScopePay is a single-user local demo. It uses real AI inference and PayPal sandbox payments, but has no production authentication or public deployment configuration.

## Data flow

```
Synthetic brief              AI draft                  Review                 Sandbox payment
+-----------+   POST    +---------------+  Groq    +-----------+   save    +----------------------+
| React/Vite| --------> | Spring Boot   | ------> | validated | -------> | gate: reviewed + same  |
| shadcn/ui |           | API (8090)    | schema  | draft in  |          | session required       |
| (5173)    | <-------- |               |         | Postgres  |          +----------------------+
+-----------+  evidence +---------------+         +-----------+                  | SDK v6 approval
                                                                                 v
                            PostgreSQL: drafts, orders,           receipt checks  PayPal Orders v2
                            ledger_entries, outbox_events  <----  status/amount/  create + capture
                                                                    currency
                                                                                 |
                            /api/webhooks/paypal <---------- verified via PayPal verify endpoint
                            (rejected webhooks change nothing)
```

1. React/Vite sends a synthetic brief to the loopback Spring Boot API. A browser session capability identifies this demo session; it is not a user account.
2. `GroqScopeProvider` sends the brief to one fixed Groq model, `openai/gpt-oss-20b`. The model returns a structured draft, not commands or payment decisions.
3. `ScopeExtractor`/`ScopeValidator` check exact keys, lengths and verbatim evidence spans. A valid shape and matching text do not prove semantic correctness. Refusal, timeout and invalid output stop the draft flow.
4. PostgreSQL stores the draft. The user checks evidence, open questions and conflicts, then explicitly saves a demo review. The review is not a contract and may include unresolved questions.
5. `OrderController` requires that reviewed draft and the same session before creating a test order. The amount comes from the fixed server catalog: USD 10.00, never from the brief or model. Order create is idempotent on the client's idempotency key: a replay returns the original order, never a duplicate.
6. PayPal SDK v6 opens a separate sandbox buyer approval flow. The server uses Orders v2 to capture the approved order, then checks completed order/capture states and exact amount/currency. Pending or uncertain outcomes become `PENDING_REVIEW`, never "paid".
7. Every order state change goes through the explicit state machine (`OrderStates`); illegal transitions throw. A completed capture posts a balanced double-entry ledger group (`LedgerService`) and writes its outbox event (`OutboxService`) in the same transaction. The Receipts API renders this trail as the order's payment timeline.
8. `WebhookController` accepts PayPal webhooks without a browser session - verification is the gate. It requires the five transmission headers, allowlists the certificate URL host, and asks PayPal's verify endpoint before recording. Verified capture completions append `WEBHOOK_CONFIRMED` to the order's trail; anything else is `WEBHOOK_REJECTED`/`WEBHOOK_IGNORED`. A webhook never changes order state.

## Components

- `client/src/pages/`: Home, Brief, Review, Payment, Receipts (shadcn/ui: sidebar, cards, tabs, dialogs, tables, toasts).
- `backend/.../api/`: `ScopeController`, `OrderController`, `ConfigController` (health/config/receipts), `WebhookController`, `DemoController` (demo-only tools), `SessionGate` (origin + session capability filter).
- `backend/.../scope/`: `ScopeExtractor`, `ScopeValidator`, `GroqScopeProvider`.
- `backend/.../paypal/`: `PayPalClient` - sandbox token, order create/capture with idempotency identifiers, webhook signature verification.
- `backend/.../store/`: JPA entities and repositories for drafts and orders; `OrderStates` (state machine), `LedgerService` (double-entry), `OutboxService` (transactional outbox).
- `backend/src/main/resources/db/migration/`: Flyway V1 (drafts/orders) and V2 (ledger/outbox).
- `backend/src/test/`: 75 JUnit tests with stubbed PayPal/AI; `DemoMain` is a test-only local runner (H2 + scripted providers) used for screenshots.

## What crosses each boundary

Browser -> API: synthetic brief, session capability, review and order identifiers. API -> Groq: synthetic brief, extraction instructions and schema. API -> PayPal: fixed demo order/capture data, webhook verification payloads. PayPal -> API: webhook transmissions (verified before use). API -> local PostgreSQL: draft, review, order, ledger and outbox state. Server secrets stay outside the client bundle and repository.

Only synthetic content is permitted. The configuration flag is an operator policy, not a content detector. Do not record secrets, credential screens or real client information.

## Demo-only tooling

`DEMO_TOOLS=true` (never set in production properties) enables `/api/demo/*`: a webhook simulator that feeds a synthetic event through the real verification path, and failure-injection modes honored only by scripted PayPal stand-ins. `DemoMain` (test sources) sets it for local screenshot runs. Neither exists in the default runtime.

## Not implemented

Real user accounts, production auth, durable global rate limits, TLS, escrow, refunds/payouts, legal acceptance, negotiated pricing, production payments and public hosting. Uncertain payment outcomes require manual seller-side inspection, not blind retries.

See LOCAL_RUN.md for setup and VERIFICATION.md for bounded test evidence. The local demo must not be internet-exposed as a substitute for a secured hosted judge build.
