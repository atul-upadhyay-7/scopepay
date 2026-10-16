# ScopePay: sandbox payment core

Private PayPal AI Hackathon work in progress. Working name only: the product concept is not frozen. There is a **provider-independent AI extraction skeleton, not a configured AI model**, no real-money transactions, and a completed backend/browser sandbox proof (not the full React SDK path).

## What exists

- React/Vite checkout review UI; Express API; MongoDB-backed sandbox order records.
- PayPal sandbox Orders v2 adapter: create an order, let the buyer explicitly approve in the PayPal UI, then capture on the server.
- Server-owned demo item: USD 10.00. Browser-supplied amounts are rejected.
- Hashed browser-session capability, strict origin check, unique idempotency keys, stable capture request IDs and stored completion receipts.
- Declined, cancelled and uncertain outcomes are not shown as successful payments.

This is a local development prototype, **not production-ready**. The browser-session capability is not a user-account system. Do not expose this local server on the internet without real authentication, rate limits, TLS, access controls and further security review. No escrow, automatic payment or payout features exist.

## Requirements

Node.js 22+, npm, and a running local MongoDB. A PayPal Developer account, sandbox app credentials, and sandbox personal buyer + business seller accounts are needed for a real sandbox checkout test. No live keys or paid service is required.

## Setup

```bash
npm ci
cp .env.example .env
```

Edit `.env` locally. Use **sandbox** Client ID/Secret from PayPal Developer Dashboard and your local MongoDB URI. Do not send secrets in chat, put them in screenshots, or commit them. The Client ID is public; the secret stays server-side.

Start MongoDB using your existing local installation. If you do not have one, stop and choose a local setup rather than signing up for a paid database.

```bash
npm test
npm run build
npm run dev
```

Open http://localhost:5173 (use `localhost`, matching APP_ORIGIN). API listens on http://127.0.0.1:8090. `npm start` starts only the API; `npm run build` builds the client but does not deploy or serve it. The API refuses missing credentials and non-sandbox PAYPAL_ENV.

```bash
curl http://127.0.0.1:8090/api/health
```

Expected: `{"status":"ok","environment":"sandbox","database":"connected"}`.

## Sandbox verification checklist

1. Confirm SDK loads and checkout is eligible in your browser.
2. Review the USD 10.00 test item; click the sandbox checkout button.
3. Approve using the generated personal **sandbox** buyer, not your real account.
4. Confirm UI says completed with a capture ID.
5. Inspect the business **sandbox** seller activity and confirm the same captured order/amount/currency. Record only non-secret IDs and outcome in `docs/VERIFICATION.md`.
6. Exercise cancel, declined payment and repeated requests. Do not mark a pending/unconfirmed result paid. Keep test account negative-testing mode off when recording a success demo.

The server records order reservations before calling PayPal. If creation fails or times out, a retry with that key will return `CREATE_PENDING_REVIEW` rather than risk a fresh duplicate. For an uncertain capture, inspect sandbox activity first. Full automated reconciliation/webhooks are not implemented. Do not automatically create a replacement order to bypass an uncertain result.

## Current verification

See `docs/VERIFICATION.md`. Unit/API tests use fake PayPal responses, not live sandbox calls. Backend create -> browser approval -> backend capture and seller receipt were verified Oct 16; full React SDK checkout remains unverified. See the exact proof boundary in `docs/VERIFICATION.md`.

## AI workflow skeleton

`POST /api/scopes/extract` accepts only a brief. Without an injected model provider it returns `503 AI_NOT_CONFIGURED`; no fixture is presented as live AI. The extraction module defines a schema and validates deliverable evidence against exact text from the brief. Missing questions and conflicts remain drafts for human review. Output cannot set payment/approval fields. Timeout, refusal and malformed-output paths are tested. This is not semantic correctness proof: exact evidence can still be misinterpreted. The real provider, user review UI and evaluated model behavior remain to be built. No paid API call was made.


## October 20 lean-build target (proposal)

- Oct 16: choose the concept and prove real sandbox checkout.
- Oct 17: implement one useful AI workflow, evaluated on synthetic examples.
- Oct 18: connect AI output, explicit human review and checkout in one working flow.
- Oct 19: error handling, fresh-run tests, privacy/security fixes, mobile checks.
- Oct 20: freeze a working lean demo; only then polish video/docs/submission.

This target depends on concept approval, available time and credentials. It is not a completed submission. Final hackathon entry needs meaningful AI + PayPal, public open-source repository with an approved license, English description, a public YouTube demo under 3 minutes and judge-run access through Dec 15. Keep this repository private until the owner approves publication. No open-source license has been selected yet.

## Sources

- https://paypalaihackathon.devpost.com/rules
- https://paypalaihackathon.devpost.com/resources
- https://developer.paypal.com/guides/node-express
- https://github.com/paypaldev/getting-started-with-paypal-sandbox
