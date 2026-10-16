# Verification record

As of October 15, 2026, initial core source bundle.

## Passed

- `npm ci`/dependency install: 0 vulnerabilities reported at install time (not a security guarantee).
- `npm test`: 15/15 pass; fake PayPal responses and isolated in-memory store.
- `npm run build`: React production build passes.
- Separate MongoDB 8.2.6 temporary-process integration: connection, unique request index, order reservation, duplicate rejection, updates and lookup pass. This is a real local test database, not a hosted deployment or the user's local database.
- Browser render in desktop and 390px mobile view: setup-needed state shown, checkout disabled without client ID, no horizontal overflow. Screens visually inspected.

## Not yet passed / blockers

- Real PayPal sandbox SDK eligible state, buyer approval and capture: NOT RUN. PayPal Developer redirected to sign-in; no PayPal login or sandbox credentials were found among saved vault entry names. The initial device check cleared to a normal login form; the remaining blocker is account access, not a persistent bot wall.
- Actual complete create/approve/capture UI path, cancel/decline UI paths and repeated capture at PayPal: NOT RUN. Test fakes are not payment proof.
- User laptop install, persistent local MongoDB configuration: NOT RUN.
- Product concept selection, AI, model evaluation, user authentication, rate limiting, production hardening, hosting, license selection, video and submission: NOT DONE.
- GitHub repository was created private; this source bundle is not itself evidence of a remote code push.

## Next sandbox evidence to collect

Date/time, environment, approved amount/currency, order ID, capture ID, final API status and seller-activity confirmation. Never record client secrets, tokens, passwords or payment card data. Update this file only after observing the actual outcome.

## October 16 morning update

- `npm test`: 31/31 pass after scope-extraction skeleton. All model outputs in these tests are explicitly injected fakes. No live AI inference or semantic-quality evaluation is claimed.
- `npm run build`: pass. No UI changes in this update; prior screenshots still describe the UI.
- AI API is disabled without a configured provider, with explicit `AI_NOT_CONFIGURED` response. Source-span/schema checks, draft-only state, timeout/abort, refusal, unknown-field rejection and API-origin/session gate covered.
- PayPal login has been saved securely; actual dashboard and payment proof are being checked, not yet confirmed.

## October 16 real PayPal sandbox proof

Backend create -> actual browser buyer approval -> backend capture PASSED. Real PayPal sandbox API and generated test accounts were used, not mocked responses. No real money moved.

- Order: `1B9742610V884994A`, created by the actual Express `/api/orders` route at 07:34:46 IST, HTTP 201.
- Buyer: generated US Personal sandbox user; seller: existing IN Business sandbox user linked to the dedicated `ScopePay-Sandbox` merchant app.
- Browser displayed USD 10.00 and the buyer clicked `Continue to Review Order`. PayPal displayed `One more step / Finish paying at Test Store`.
- Actual Express capture endpoint returned HTTP 200 with `COMPLETED` and capture `1830049304363151S` at 07:35:37 IST. Handler verified both completed statuses and USD 10.00 before saving the Mongo receipt.
- A repeated capture request returned the same stored capture ID without issuing a second upstream capture call (the completed-record branch).
- Independently logged into the sandbox seller UI. Transaction detail shows Checkout, USD 10.00 gross, Completed, October 16 07:35:36 IST and the same capture ID. Seller activity lists one payment, USD 0.81 simulated fee and USD 9.19 simulated net.
- Seller evidence: https://sandbox.paypal.com/unifiedtransactions/details/payment/1830049304363151S
- Generated credentials are vaulted, never committed. Local temporary API credential files were removed after the proof.

Important boundary: this used the real backend routes with a temporary local MongoDB process and PayPal's returned approval URL, not the React JS SDK button. The full React SDK end-to-end checkout, cancel/decline UX and production hardening remain unverified. Source-bundle tests do not replace those checks. Earlier entries above record historical blockers, now resolved for account access and backend payment proof only.

## October 16 local full-flow build, after first push

- First PRIVATE GitHub push: `fe746fa359c9aabde93c4f6a3f0b7f3a4fd3493c`, with owner author and committer identity. Temporary token revoked after verification.
- New LOCAL-only code: Groq strict-output adapter, synthetic-only/free-confirmed opt-in, request cap, no retries/fallback; React brief/evidence/questions/conflicts/review UI; Mongo draft/review persistence; full app requires server-verified review before creating test order; setup and six-case synthetic evaluation harness.
- `npm test`: 43/43 pass, all provider/payment outputs in these tests are injected fakes. `npm run build`: pass.
- Actual MongoDB + UI integration with an explicitly fake provider passed extraction, evidence/question display, checkbox gate, saved review, immutable reviewed input, blocked checkout without credentials, desktop and 390px mobile no-overflow checks. Both full-page screenshots visually inspected. NOT live AI proof.
- Before this UI extension, actual SDK v6 loaded, eligibility passed and actual React button created sandbox order `62E41767TE7379118` and normal buyer-login popup. Browser closed without buyer login/approval/capture. Order was not paid. This is not full UI payment proof.
- Groq account identity choice, verified Free status/key/ZDR and live synthetic quality evaluation remain pending. Full UI sandbox capture remains pending secure test-buyer credential-use approval. No complete-project claim yet.

### Local error-path follow-up

Actual React UI + temporary MongoDB with explicitly FAKE SDK, AI and PayPal response adapters passed completed/cancelled/checkout-error/AI-error flows. Exactly one capture handler call occurred, only in the completed scenario; cancellation/error were not paid, the checkout disabled afterward, and raw provider secret text was not exposed. Success rendering screenshot visually inspected. This is regression coverage, NOT live provider/payment proof. `npm audit --omit=dev` reported 0 production dependency vulnerabilities at this check, not a security guarantee.

## October 16 live Groq evaluation

Personal 899 Google identity used for Groq. Live billing showed Free, $0, Current Plan. No card, credits or upgrade. Global ZDR enabled and read back, inference ZDR enabled, batch/fine-tuning storage disabled. API key stored securely, not in source. Current account limits list gpt-oss-20b at 30 RPM, 1,000/day, 8k TPM, 200k/day; limits may change.

The first live runs found two useful failures: a missing `conflicts` key was returned as provider HTTP400 despite strict output, and one evidence span was paraphrased. Both were rejected. Instructions now repeat the schema/all keys and exact contiguous source evidence; no failed-generation draft is rescued or silently accepted. An exact refusal-only provider failure is classified AI_REFUSED.

Final six synthetic cases were run on the updated instructions and manually reviewed: clear brief, login conflict, missing requirements and negation returned grounded draft output; injection/charge instruction and unsupported content safely refused. Evidence, source intent, conflicts and absent payment authority were inspected. Results are in `evaluation/verified-synthetic-20261016.json`. This small set is not a general accuracy/safety guarantee; human review stays required. No private client data was sent. Provider schema errors, evidence errors and quota failures can still reject otherwise useful briefs; there is no automatic retry or paid fallback.

### Current UI with actual live AI

Actual local Chrome ran the current React UI against real Groq Free extraction and temporary real MongoDB, saved the explicit review, and loaded actual PayPal SDK v6 to eligible checkout-ready state. AI output/evidence/questions were read; desktop and 390px mobile full-page pixels inspected. No order or buyer payment was created in this check. Test-buyer password-use permission still gates current full UI approval/capture. Temporary API/key copies were removed after testing.

## October 16 current full UI payment proof

At 16:24 IST the current React app ran real Groq Free extraction, saved explicit review to temporary local MongoDB, opened actual PayPal SDK v6 checkout, logged in the generated US sandbox buyer, displayed USD10 and received buyer approval through Complete Purchase. Actual app capture completed and UI displayed `Sandbox payment completed. Capture: 0VC41183E2084414S`.

- Order `32M04360X0766143V`; capture `0VC41183E2084414S`; USD 10.00.
- Independent IN sandbox seller UI confirmed Checkout, Completed, same capture and gross amount, timestamp October 16 16:24:57 IST.
- Observed seller URL: https://www.sandbox.paypal.com/unifiedtransactions/details/payment/0VC41183E2084414S
- First AI attempt in the checkout test failed closed before creating an order; manual fresh run succeeded. An earlier inspection-only buyer review was closed without approval (`3WF20359XF066080M`), so no capture was attempted for it. No automatic fallback or replacement capture.
- Immediate app screenshot includes PayPal's return overlay, so it is payment evidence, not polished demo media. Seller receipt screenshot visually inspected and confirms status independently.
- Local test databases/processes were stopped; temporary API/buyer/Groq copies removed. Credentials remain only in secure storage. No real funds moved.

Remaining owner handoff checks: run locally on the owner's laptop with a persistent MongoDB; choose license and public-release timing; video and hackathon submission. Production deployment/security and generalized semantic accuracy are not claimed.
