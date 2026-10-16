# Run ScopePay locally

This is a private, sandbox-only hackathon prototype. No real service is sold, no escrow exists, and the AI draft is not a contract or a price quote.

## Free prerequisites

- Node 22 or newer and npm.
- Local MongoDB Community running. Example local URI: `mongodb://127.0.0.1:27017`. Do not use a paid hosted database.
- PayPal Developer sandbox app: public Client ID and server-side secret, plus a generated personal sandbox buyer.
- Optional Groq Free account with access to `openai/gpt-oss-20b`. Verify the console says Free and enable Zero Data Retention. Do not add a card, buy credits or upgrade. Actual free quotas are account-specific.

## Setup

```bash
npm ci
cp .env.example .env
```

Edit `.env` on your own machine. Set `PAYPAL_CLIENT_ID`, `PAYPAL_CLIENT_SECRET` and `MONGODB_URI`. The AI needs `GROQ_API_KEY`, `GROQ_FREE_PLAN_CONFIRMED=true`, `AI_SYNTHETIC_ONLY=true`. Only set the confirmation flag after checking Free status and ZDR. All other values can stay as in the example. Never commit the filled file or paste secrets into chat.

```bash
npm test
npm run build
npm run dev
```

Open **http://localhost:5173**, not 127.0.0.1: the exact origin gate defaults to localhost. The API binds only to local loopback port 8090. A green health response at http://127.0.0.1:8090/api/health means MongoDB connected, not that PayPal or AI are verified.

## Demo

1. Keep the example brief or enter a synthetic one. No names, real client briefs, confidential data or secrets.
2. Click Extract scope draft. Without a key/confirmation, AI is clearly disabled; there is no canned replacement.
3. Read every deliverable and its verbatim evidence, questions and conflicts. Bad interpretation? Reload and revise the brief; do not approve it. Schema-valid JSON is not proof of correctness.
4. Check the review box and Confirm demo review. The server stores this review tied to the browser session. This acknowledges a draft for a simulated demo only, including unresolved questions, not a real agreement.
5. Review the fixed **USD 10.00** test item. It is server-owned, not priced by AI. Click the separate PayPal sandbox checkout button.
6. In PayPal use the generated **sandbox buyer**, never your real account. Approve only the $10 sandbox test.
7. Wait for the UI to display COMPLETED and a capture ID. Independently inspect seller sandbox activity for the same capture/amount/currency.
8. Cancelled, declined, pending or uncertain outcomes are not paid. Check seller activity before any new checkout. Do not retry blindly.

Each draft/review is stored in local MongoDB. Browser sessionStorage owns access, so a new tab/session is not a user login and will not inherit the draft. Reload intentionally starts a fresh UI flow. There is no multi-user auth, public hosting or recovery dashboard.

## Synthetic AI evaluation

`npm run evaluate` sends at most six synthetic briefs from `evaluation/briefs.json` to the configured Groq account. It stops at the first error and saves ignored `evaluation/results.local.json`. Read every result against the included manual checklist before claiming a quality pass. A strict JSON schema never proves the model understood negation, omitted requirements or source intent. The server adapter also has a per-process 50-request ceiling, no automatic retry and no paid fallback. This is a cost guard, not a durable global quota.

## Troubleshooting

- `AI_NOT_CONFIGURED`: missing key or Free confirmation. Check setup, do not substitute fixture results.
- `AI_UNAVAILABLE`, `AI_TIMEOUT`, `AI_REFUSED`: no accepted draft. Check actual free quota/model availability. Do not upgrade to bypass this.
- `UNGROUNDED_DELIVERABLE` or `UNGROUNDED_CONFLICT`: evidence did not match the brief exactly. Output is rejected.
- `REVIEWED_SCOPE_REQUIRED`: extraction and explicit review must happen in the same browser session before order creation.
- `ORIGIN_NOT_ALLOWED`: open localhost:5173. Changing port requires matching `APP_ORIGIN`.
- `PAYPAL_AUTH_FAILED`: check sandbox credentials, not production keys.
- `PAYMENT_UNCONFIRMED`: check seller activity first. Never assume a timeout means a charge failed.

## Safety boundaries

Do not expose this local app on the internet. It has no real account authentication, durable rate-limiting, TLS or production access controls. No webhooks/reconciliation worker, escrow, payouts, legal acceptance, automatic price setting or real payment rails are implemented. Only a single demo buyer session should use it. Publication, license choice, video and hackathon submission are separate owner steps.
