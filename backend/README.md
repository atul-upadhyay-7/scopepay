# ScopePay backend (Spring Boot)

Java 21, Spring Boot 3.3, Spring Data JPA, PostgreSQL with Flyway migrations. Sandbox only: there is no live PayPal mode in the code.

## Environment

| Variable | Required | Meaning |
| --- | --- | --- |
| `DATABASE_URL` | yes | JDBC PostgreSQL URL, e.g. `jdbc:postgresql://127.0.0.1:5432/scopepay` |
| `DATABASE_USER` / `DATABASE_PASSWORD` | yes | Local database credentials. Never commit real values. |
| `APP_ORIGIN` | yes | Exact browser origin allowed through the session gate, e.g. `http://localhost:5173` |
| `PAYPAL_CLIENT_ID` / `PAYPAL_CLIENT_SECRET` | yes | PayPal Developer **sandbox** app credentials |
| `PAYPAL_WEBHOOK_ID` | for webhooks | Webhook ID from the PayPal Developer dashboard (Notifications > Webhooks). Without it, `/api/webhooks/paypal` answers `WEBHOOK_NOT_CONFIGURED` and processes nothing. |
| `GROQ_API_KEY` | for AI drafts | Groq Free key; see the repo README for the free-plan rules |
| `GROQ_FREE_PLAN_CONFIRMED` | no | Must be `true` (after checking Free status + ZDR) for live AI drafts |
| `REQUIRE_REVIEWED_SCOPE` | no | Default `true`: an order needs a saved human review first |
| `DEMO_TOOLS` | no | Default `false`. Enables `/api/demo/*` (webhook simulator, failure injection). Never set in a shared or production environment. |

## Run

```bash
mvn test          # full JUnit suite (H2 in-memory; no external services needed)
mvn spring-boot:run
```

Tests stub PayPal and the AI provider; only `mvn spring-boot:run` against real env values talks to the sandbox.

## Money-safety invariants (covered by tests)

- Server-owned price: the AI never sets an amount; any client-supplied amount is rejected.
- Idempotent order create: the same idempotency key returns the original order, never a second PayPal order.
- Fail-closed capture: an unclear or mismatched PayPal answer never becomes "paid".
- State machine: only `CREATING -> CREATED -> COMPLETED | PENDING_REVIEW -> COMPLETED`; anything else throws.
- Ledger: a completed capture posts a balanced double-entry group in the same transaction; an unbalanced group rolls back.
- Outbox: every state change writes its audit event in the same transaction; receipts render this trail.
- Webhooks: untrusted until PayPal's verify endpoint says SUCCESS; failures are recorded as `WEBHOOK_REJECTED` and change nothing. A verified webhook only appends `WEBHOOK_CONFIRMED`; it never moves order state.
