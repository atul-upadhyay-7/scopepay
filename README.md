# ScopePay

A private PayPal AI hackathon prototype: turn a synthetic project brief into an evidence-linked scope draft, review it explicitly, then make a separate fixed-price sandbox payment. AI never prices work or approves payment.

## What works in the local build

- React/Vite interface for a synthetic brief, draft summary, deliverables with exact source evidence, questions and conflicts.
- Server-side extraction validation with clear disabled, timeout, refusal and invalid-output errors. No canned output masquerading as AI.
- Groq adapter for `openai/gpt-oss-20b`, strict JSON schema, separate system/brief messages, fixed endpoint, no tools or automatic retry, no paid fallback.
- Explicit review checkbox and server-saved review tied to the browser session in MongoDB.
- Server-enforced reviewed-scope gate before creating the fixed USD 10.00 demo order.
- PayPal sandbox SDK v6, Orders v2 create/capture, browser buyer approval, server-owned amount, session capability/origin checks, idempotency and stored completion receipt.
- Cancelled, declined, pending and uncertain outcomes are never marked completed.

This is a local prototype, not a production service, escrow product, legal agreement or automatic charging system. The review acknowledges a draft for a simulated demo only, including open questions. It does not settle a client's requirements or create an obligation. No real service is sold and no real money moves.

## Run

Node 22+, npm and local MongoDB Community are required. Use free services only.

```bash
npm ci
cp .env.example .env
# Edit .env locally with sandbox credentials and your local MongoDB URI.
npm test
npm run build
npm run dev
```

Open **http://localhost:5173**. API is loopback-only on port 8090. The built client is not automatically hosted by `npm start`; use `npm run dev` for this local demo. See [full setup, demo and troubleshooting](docs/LOCAL_RUN.md).

Groq is disabled unless an API key and `GROQ_FREE_PLAN_CONFIRMED=true` are configured, with `AI_SYNTHETIC_ONLY=true`. Verify the account is Free, the fixed model is accessible and Zero Data Retention is enabled first. Do not add a card, buy credits or upgrade. Free limits are account-specific. The adapter stops after 50 requests per process; this is not a durable global quota. Rate limits/errors fail explicitly, without a paid fallback.

Never commit a filled `.env`, put secrets in screenshots or paste them into chat. No keys or test-account passwords are included. Only use synthetic briefs: the operator flag does not automatically detect private content. Extracted drafts are stored in local MongoDB; do not put real client or personal data in them.

## Verification, honestly scoped

[Verification record](docs/VERIFICATION.md) distinguishes real sandbox proof, fake-provider tests and unresolved checks.

- 44 unit/API/adapter tests pass; production build passes.
- Real temporary MongoDB + React UI test passes draft extraction using an injected fake provider, review persistence, checkout gate and desktop/mobile layout.
- Fake-SDK UI scenarios cover completed, cancelled, checkout-error and AI-error outcomes. Those are not live payment proof.
- Actual backend -> browser approval -> backend capture and independent sandbox seller receipt were verified Oct 16 before the review UI extension.
- Current full UI flow passed with real Groq extraction, MongoDB review, SDK v6 create, sandbox buyer approval, server capture and independent seller receipt: order `32M04360X0766143V`, capture `0VC41183E2084414S`, USD 10.00, Completed.
- Groq account Free/$0 and Global ZDR verified; six synthetic cases manually reviewed, including safe refusals. See `evaluation/verified-synthetic-20261016.json`. This is bounded evidence, not a general accuracy guarantee.

`npm run evaluate` runs at most six synthetic cases and saves an ignored local results file. Inspect results against each case's semantic checklist. Exact evidence and strict JSON do not prove correct interpretation.

## Boundaries and remaining work

Keep the repository PRIVATE. Do not expose the app publicly: it lacks real user accounts, production authentication, durable rate limiting, TLS, deployment access controls and reconciliation/webhooks. A session token is a local demo capability, not a login. Uncertain payment outcomes need manual sandbox activity inspection; do not create replacement orders blindly.

The lean-demo target is October 20, 2026. Live AI evaluation and full current UI sandbox proof have passed; user laptop installation is a later handoff check. Public release, English submission description, public YouTube video under three minutes, and judge-run access are separate owner steps. MIT was selected by the owner on October 16, 2026. No hackathon entry has been submitted.

## Official references

- https://paypalaihackathon.devpost.com/rules
- https://paypalaihackathon.devpost.com/resources
- https://developer.paypal.com/guides/node-express
- https://console.groq.com/docs/structured-outputs
- https://console.groq.com/docs/your-data

## License

[MIT](LICENSE). Copyright (c) 2026 Atul Upadhyay. Third-party dependencies and services retain their own terms. The repository remains private until public release is separately approved.
