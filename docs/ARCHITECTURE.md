# Architecture and trust boundaries

ScopePay is a single-user local demo. It uses real AI inference and PayPal sandbox payments, but has no production authentication or public deployment configuration.

## Data flow

```
Synthetic brief            AI draft                 Review               Sandbox payment
+-----------+   POST    +------------+  Groq    +-----------+  save   +-------------------+
| React/Vite| --------> | Express    | ------> | validated | ------> | server gate:      |
| browser   |           | API        | schema  | draft in  |         | reviewed + same   |
| (5173)    | <-------- | (8090)     |         | MongoDB   |         | session required  |
+-----------+  evidence +------------+         +-----------+         +-------------------+
                                                                           | SDK v6 approval
                                                                           v
                                          receipt checks  <---------  PayPal Orders v2
                                          status/amount/currency      create + capture
```

1. React/Vite sends a synthetic brief to the loopback Express API. A browser session capability identifies this demo session; it is not a user account.
2. `server/scope/groq.js` sends the brief to one fixed Groq model, `openai/gpt-oss-20b`. The model returns a structured draft, not commands or payment decisions.
3. `server/scope/extract.js` validates exact keys, lengths and verbatim evidence spans. A valid shape and matching text do not prove semantic correctness. Refusal, timeout and invalid output stop the draft flow.
4. MongoDB stores the draft. The user checks evidence, open questions and conflicts, then explicitly saves a demo review. The review is not a contract and may include unresolved questions.
5. `server/app.js` requires that reviewed draft and the same session before creating a test order. The amount comes from the fixed server catalog: USD 10.00, never from the brief or model.
6. PayPal SDK v6 opens a separate sandbox buyer approval flow. The server uses Orders v2 to create and capture the approved order.
7. The server checks completed order/capture states and exact amount/currency, then stores the receipt. Pending or uncertain outcomes require manual seller-side inspection, not blind retries.

## Components

- `client/src/main.jsx`: brief, evidence-linked review, explicit acknowledgment and checkout UI.
- `server/index.js`: configuration, MongoDB connection and loopback startup.
- `server/app.js`: API routes, origin/session checks, review gate and payment state.
- `server/paypal.js`: sandbox token, create and capture requests with idempotency identifiers.
- `server/store.js`: MongoDB scopes and orders, including unique request/order indexes.
- `server/scope/`: AI adapter and provider-independent validation.
- `tests/`: unit/API/adapter tests. `evaluation/`: synthetic live-AI cases and reviewed results.

## What crosses each boundary

Browser -> API: synthetic brief, session capability, review and order identifiers. API -> Groq: synthetic brief, extraction instructions and schema. API -> PayPal: fixed demo order/capture data. API -> local MongoDB: draft, review and payment state. Server secrets stay outside the client bundle and repository.

Only synthetic content is permitted. The configuration flag is an operator policy, not a content detector. Do not record secrets, credential screens or real client information.

## Not implemented

Real user accounts, production auth, durable global rate limits, TLS, webhooks/reconciliation, escrow, payouts, legal acceptance, negotiated pricing, production payments and public hosting. The 50-request AI ceiling resets with the process. A lost response can leave an uncertain payment outcome; inspect seller activity before starting another order.

See LOCAL_RUN.md for setup and VERIFICATION.md for bounded test evidence. A new machine still needs its own clean-run check. The local demo must not be internet-exposed as a substitute for a secured hosted judge build.
