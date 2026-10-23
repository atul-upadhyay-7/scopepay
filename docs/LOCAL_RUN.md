# Run ScopePay locally

A private, sandbox-only hackathon prototype. No real service is sold, no escrow exists, and the AI draft is not a contract or a price quote. Everything below uses free software only.

Stack: Spring Boot 3.3 (Java 21) + PostgreSQL 17 backend, React + Vite + shadcn/ui (Tailwind v4) frontend. One laptop runs both.

## Step 1 - JDK 21

```bash
java -version
```

Need exactly a 21.0.x version. The build is verified on JDK 21; newer JDKs (25+) can break the database layer's bytecode generation, so use 21. On Fedora: `sudo dnf install java-21-openjdk-devel` - this installs side by side and does not replace a newer default JDK.

If your default `java` is not 21, find the 21 install and point the backend steps at it explicitly:

```bash
ls /usr/lib/jvm
export JAVA_HOME=/usr/lib/jvm/<the 21 folder from that list>
$JAVA_HOME/bin/java -version
```

Any JDK 21 vendor works (Fedora's openjdk package, Temurin, etc.) - only the version matters.

## Step 2 - Maven

```bash
mvn -version
```

If missing: `sudo dnf install maven`, then re-check.

## Step 3 - Node 22+ (for the client)

```bash
node -v
```

Need `v22` or newer. On Fedora: `sudo dnf install nodejs22` (or use nvm), then re-check.

## Step 4 - PostgreSQL in podman (free, local only)

```bash
podman run -d --name scopepay-db \
  -e POSTGRES_USER=scopepay \
  -e POSTGRES_PASSWORD=scopepay-local \
  -e POSTGRES_DB=scopepay \
  -p 127.0.0.1:5432:5432 \
  docker.io/library/postgres:17
```

Check it is up: `podman ps` shows `scopepay-db`. Later sessions: `podman start scopepay-db` (data persists in the container). This is a throwaway local database - do not point the app at any hosted database.

## Step 5 - Configure

```bash
cp .env.example .env
```

Edit `.env` locally. Required: `PAYPAL_CLIENT_ID` and `PAYPAL_CLIENT_SECRET` from your PayPal Developer **sandbox** app. For AI drafts: `GROQ_API_KEY`, plus `GROQ_FREE_PLAN_CONFIRMED=true` only after the console shows Free and Zero Data Retention is on. Do not add a card, buy credits or upgrade.

Optional: `PAYPAL_WEBHOOK_ID` (sandbox dashboard, Notifications > Webhooks) enables the live webhook endpoint; without it the endpoint stays closed (`WEBHOOK_NOT_CONFIGURED`). Leave `DEMO_TOOLS=false` for the real flow.

Never commit the filled `.env` or paste secrets into chat.

Load the variables into your shell before starting the backend:

```bash
set -a; source .env; set +a
```

## Step 6 - Backend tests (no services needed)

```bash
cd backend
mvn test
```

The full JUnit suite runs against an in-memory database with stubbed PayPal and AI: 112 tests, all must pass (one of them verifies the migrations against a real embedded PostgreSQL). This proves the safety code without touching the network.

## Step 7 - Start the backend

Same shell (env still loaded), from `backend/`:

```bash
mvn spring-boot:run
```

Check in another terminal: `curl http://127.0.0.1:8090/api/health` returns `{"status":"ok","environment":"sandbox","database":"connected"}`. The API binds to loopback only. Flyway creates the tables in your local PostgreSQL on first run.

## Step 8 - Start the client

New terminal:

```bash
cd client
npm ci
npm run dev
```

Open **http://localhost:5173** - not 127.0.0.1: the origin gate matches `APP_ORIGIN` exactly.

## The demo flow

1. Keep the example brief or paste a synthetic one. No real client data, names or secrets.
2. Extract a draft. Without a configured Groq key the AI reports itself disabled; there is no canned replacement.
3. Review the draft: every deliverable links to verbatim evidence from your brief. Check the acknowledgment box and save the review. The server requires this before any order.
4. Payment: one fixed USD 10.00 sandbox item, priced by the server, never the AI. Approve in PayPal's own sandbox checkout with a **sandbox buyer**, never your real account.
5. Receipts: open your order. The timeline shows every server-side event (created, captured, or held for review) and the double-entry ledger the server posted - both sides, netting to zero.
6. Try **Replay this request** on the order: it re-sends the same idempotency key and the server returns the original order (HTTP 200), proving a retry cannot double-charge.
7. Independently inspect the seller sandbox activity in the PayPal dashboard for the same capture id, amount and currency.

Cancelled, declined, pending or uncertain outcomes are never shown as paid. Check seller activity before any new checkout; do not retry blindly.

### Optional: webhooks

With `PAYPAL_WEBHOOK_ID` set, point a sandbox webhook at `http://127.0.0.1:8090/api/webhooks/paypal` for `PAYMENT.CAPTURE.COMPLETED`. The server verifies each transmission with PayPal's verify endpoint before recording `WEBHOOK_CONFIRMED` on the matching order; anything unverifiable is rejected and logged. For a local demo without a public URL, `DEMO_TOOLS=true` adds a **Simulate webhook** button on the order dialog - with real credentials PayPal correctly refuses the synthetic signature, which is the demonstration.

### Optional: observability

- Health: http://127.0.0.1:8090/actuator/health
- Metrics: http://127.0.0.1:8090/actuator/metrics (try `/actuator/metrics/scopepay.orders.placed`)
- Prometheus scrape: http://127.0.0.1:8090/actuator/prometheus
- Swagger UI: http://127.0.0.1:8090/swagger-ui/index.html
- Every response carries an `X-Correlation-Id` header; backend logs are structured JSON with that id on every line.
- Coverage report after `mvn test`: `backend/target/site/jacoco/index.html`.

### Optional: Redis rate limit (default off)

Needs a local Redis. Free podman option: `podman run -d --name scopepay-redis -p 127.0.0.1:6379:6379 docker.io/library/redis:7-alpine`. Then set `SCOPEPAY_RATELIMIT_ENABLED=true` in `.env` and restart the backend. Mutating API calls are limited per session per minute (default 60, `SCOPEPAY_RATELIMIT_PER_MINUTE`). If Redis stops, the API keeps working (fail-open, counted in metrics). Stop anytime: `podman rm -f scopepay-redis` and remove the flag.

### Optional: Kafka outbox relay (default off)

Needs a local Kafka (KRaft single node is fine). Free podman option: `podman run -d --name scopepay-kafka -p 127.0.0.1:9092:9092 docker.io/apache/kafka:3.7.0`. Then set `SCOPEPAY_KAFKA_RELAY_ENABLED=true` in `.env` and restart the backend. Committed outbox events are published to the `scopepay.outbox` topic in order and marked `published_at`. If Kafka stops, events stay in Postgres and publish when it returns; with the flag off the outbox table is the whole contract. Stop anytime: `podman rm -f scopepay-kafka` and remove the flag.

### Optional: failure drills

With `DEMO_TOOLS=true`, a Demo controls card on the Payment page simulates PayPal failures (unclear capture, wrong amount, create outage). The server's real safety code handles each one; nothing can mark a payment as paid.

## Troubleshooting

- `Flyway`/`connection refused`: is `podman ps` showing scopepay-db? Restart with `podman start scopepay-db`.
- `port is already allocated` on the podman run: something else owns 5432. Check with `ss -ltnp`, leave it alone, and use host port 5433 instead: rerun with `-p 127.0.0.1:5433:5432` and set `DATABASE_URL=jdbc:postgresql://127.0.0.1:5433/scopepay` in your `.env`.
- `AI_NOT_CONFIGURED`: missing Groq key or Free confirmation. Check step 5; do not substitute fixture results.
- `AI_UNAVAILABLE`, `AI_TIMEOUT`, `AI_REFUSED`: no accepted draft. Check actual free quota; do not upgrade to bypass.
- `UNGROUNDED_DELIVERABLE` / `UNGROUNDED_CONFLICT`: evidence was not verbatim from the brief; the draft was rejected.
- `REVIEWED_SCOPE_REQUIRED`: extract and save the review in the same browser session before ordering.
- `ORIGIN_NOT_ALLOWED`: open localhost:5173, or make `APP_ORIGIN` match the port you use.
- `PAYPAL_AUTH_FAILED`: check sandbox credentials, not production keys.
- `PAYMENT_UNCONFIRMED`: check seller activity first. Never assume a timeout means a charge failed.

## Optional: backend container image

`podman build -f Containerfile -t scopepay-backend .` from the repo root builds a backend-only image (tests skipped inside the build; run them first). The client still runs with `npm run dev`. This is a convenience for judges, not a required path.

## Safety boundaries

Do not expose this app on the internet. It has no real accounts, durable rate limiting, TLS or production access controls. No escrow, payouts, legal acceptance, automatic pricing or live payment rails. Publication, the demo video and hackathon submission are separate owner steps.
