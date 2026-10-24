# Verification record

Honest scoping: what is proven by automated tests, what used scripted stand-ins, and what remains to verify on the owner's machine. Updated October 24, 2026.

## Automated proof (runs anywhere, no external services)

`cd backend && mvn test` - 165 JUnit tests, 0 failures. One of them (PostgresSchemaValidationTest) boots the app against a real embedded PostgreSQL with Flyway and Hibernate validation, exactly like production config; the rest run against an in-memory H2 database or as plain unit tests, with stubbed PayPal and AI providers. That guard was added after a real install on October 18 caught a migration/entity drift (ledger currency CHAR(3) vs VARCHAR) that the H2 suite could not see - fixed by migration V3.

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
- **Delivery comparison**: per-deliverable claim-coverage verdicts with verbatim quotes enforced as exact substrings of the submitted account; blanket claims and instruction-override text downgrade to UNCLEAR; scope exclusions judged on their own claim axis (EXCLUSION_VIOLATED / EXCLUSION_RESPECT_CLAIMED / EXCLUSION_NO_CLAIM), never as missing work.
- **Client complaint coverage** (October 24): the comparison takes the client's complaint as a separate cited input; each coverage quote must be a verbatim substring of the complaint (validator-enforced, fail-closed), unmentioned deliverables stay unknown (never agreement), and the complaint never changes a verdict. Coverage rows present without a complaint, or missing when one was supplied, are rejected.
- **Fixture replay** (October 24): `DEMO_FIXTURES=true` with no Groq key replays one bundled synthetic example through the same fail-closed validators and refuses any other input byte-for-byte; activation wiring is tested (fixture only with flag + blank key; live key always wins; never in the test profile).

`cd client && npm run build` - production build passes.

## Scripted stand-ins (not live proof)

Screenshots and the local demo runner (`DemoMain`, test sources only) use a scripted PayPal stand-in and scripted AI provider so the full UI flow - brief, review, order, capture, timeline, ledger, replay, webhook simulation, failure drills - can be exercised without sandbox credentials. The stand-in is clearly flagged (`DEMO_STUB_PAYPAL`, order ids read `DEMO...`) and never part of production code.

## Verified live on the owner's machine (PayPal sandbox + real Groq)

- October 23, 2026: full live flow - real Groq draft, saved review, SDK v6 buyer approval, server capture. Sandbox order `47594649JU108911K`, capture `1MU8S777825626334`, USD 10.00, COMPLETED (sandbox, not real money).
- October 24, 2026: live exclusion-aware comparison on the real Groq provider - the exclusion item judged on its own exclusion axis (EXCLUSION_RESPECT_CLAIMED), positive deliverables on theirs, with one deliverable staying reasoned UNCLEAR rather than being force-resolved.
- October 24, 2026: live USD 5.00 partial refund (half the remaining cap) approved by the owner through the make-good flow; reconciled to REFUNDED from PayPal's own record (sandbox refund `773103623A473584W`); balanced ledger reversal posted.

## Pending owner-machine verification

- A live Groq run of the client-complaint coverage path added October 24 (sandbox tests and fixture replay only so far - the complaintCoverage block has not yet been produced by the live model).
- Live webhook delivery from the PayPal sandbox dashboard to `/api/webhooks/paypal` (requires `PAYPAL_WEBHOOK_ID`).
- Clean-clone run following docs/LOCAL_RUN.md exactly.

## Historical (previous Node prototype, superseded)

The Node/Express/MongoDB version's live sandbox flow was verified October 16, 2026: order `32M04360X0766143V`, capture `0VC41183E2084414S`, USD 10.00, completed, with independent seller receipt. Groq account Free/$0 and Global ZDR were verified the same day. These results describe the superseded stack; the Spring Boot rebuild re-verifies live before submission.
