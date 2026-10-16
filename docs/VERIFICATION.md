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
