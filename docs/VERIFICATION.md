# Verification record

Honest scoping: what is proven by automated tests, what used scripted stand-ins, and what remains to verify on the owner's machine. Updated October 18, 2026 for the Spring Boot rebuild.

## Automated proof (runs anywhere, no external services)

`cd backend && mvn test` - 74 JUnit tests, 0 failures, against an in-memory H2 database with stubbed PayPal and AI providers:

- **AI extraction**: schema validation, verbatim evidence grounding (`UNGROUNDED_DELIVERABLE`/`UNGROUNDED_CONFLICT` on mismatch), disabled/timeout/refusal paths, no canned fallback.
- **Review gate**: orders require a saved review from the same session; the acknowledgment is required.
- **Server-owned price**: client-injected amounts are rejected; the SKU catalog owns the USD 10.00.
- **Idempotency**: the same key returns the original order with exactly one PayPal create; a failed create keeps its reservation so a retry conflicts instead of duplicating.
- **Fail-closed capture**: pending/unclear captures become `PENDING_REVIEW` and are never shown as paid; a wrong captured amount or currency is rejected and the order stays `CREATED`.
- **State machine**: legal transitions pass; skips, terminal-state writes and unknown states throw.
- **Ledger**: a completed capture posts one balanced double-entry group; a replayed capture does not post twice; nothing posts when money did not move.
- **Outbox**: state changes write their events atomically; receipts expose the timeline session-scoped (another session sees none of it).
- **Webhooks**: verified transmissions append `WEBHOOK_CONFIRMED` without moving order state; forged signatures, missing headers and untrusted certificate URLs are rejected and recorded; unknown captures are ignored.
- **Demo tools**: simulator and failure modes work when `DEMO_TOOLS=true` and return 404 otherwise; failure modes drive the real fail-closed handlers.

`cd client && npm run build` - production build passes.

## Scripted stand-ins (not live proof)

Screenshots and the local demo runner (`DemoMain`, test sources only) use a scripted PayPal stand-in and scripted AI provider so the full UI flow - brief, review, order, capture, timeline, ledger, replay, webhook simulation, failure drills - can be exercised without sandbox credentials. The stand-in is clearly flagged (`DEMO_STUB_PAYPAL`, order ids read `DEMO...`) and never part of production code.

## Pending owner-machine verification

- Full live flow on the owner's laptop with real sandbox credentials: real Groq draft, saved review, SDK v6 buyer approval, server capture, independent seller receipt in the PayPal dashboard.
- Live webhook delivery from the PayPal sandbox dashboard to `/api/webhooks/paypal` (requires `PAYPAL_WEBHOOK_ID`).
- Clean-clone run following docs/LOCAL_RUN.md exactly.

## Historical (previous Node prototype, superseded)

The Node/Express/MongoDB version's live sandbox flow was verified October 16, 2026: order `32M04360X0766143V`, capture `0VC41183E2084414S`, USD 10.00, completed, with independent seller receipt. Groq account Free/$0 and Global ZDR were verified the same day. These results describe the superseded stack; the Spring Boot rebuild re-verifies live before submission.
