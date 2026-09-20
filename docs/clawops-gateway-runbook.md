# ClawOps Gateway staging runbook

## Boundary

The gateway owns the single reverse WebSocket connection for one 070 number. A new
gateway process takes that number from the old process even when Cloud Run HTTP traffic
has not been shifted. For that reason this service does not use the backend blue/green
traffic procedure as a safety boundary.

## Before deployment

1. Confirm backend V10 is migrated and phone ingress is enabled.
2. Confirm exactly one BFF `WAITING_FOR_CALL` session exists for the smoke call.
3. Verify Secret Manager contains separate ClawOps API, account, OpenAI, and shared
   phone-ingress secrets. Grant the gateway runtime service account access only to these.
4. Confirm the ClawOps subscription, 070 number, and paid-call authority with the user.
5. Record the previous gateway image digest and revision.

## Deploy and smoke

Run `ClawOps Gateway Cloud Run staging deployment` from `develop` with both confirmation
inputs enabled. The deployment enforces one instance, continuously allocated CPU,
recording disabled, private HTTP access, and an SDK-connected `/healthz` startup probe.

Then place one consented synthetic call and verify:

- a single waiting incident changes to `IN_CALL`;
- in-call user utterances appear only as `INTERIM`;
- the aggregated transcript becomes `FINAL_PENDING_REVIEW` after hang-up;
- analysis remains blocked before review;
- the exact approved revision reaches analysis;
- one CAS confirmation keeps rules locked and two confirmations unlock the supported rule;
- the response record can be read back after save.

Do not put the phone number or transcript body in screenshots or log excerpts.

## Rollback

Traffic percentages do not restore the number owner. Redeploy the recorded previous image
digest as a new one-instance revision so it establishes a fresh ClawOps control connection.
Verify `/healthz` and one approved synthetic call. If no known-good digest is available,
set the gateway service maximum instances to zero to stop new calls and keep the BFF phone
ingress disabled until a reviewed image is ready.

## Current limitation

The realtime SDK has no provider event ID for each transcript callback. The gateway derives
a stable SHA-256 event ID from call ID, segment order, and text, while the BFF enforces unique
provider event IDs. Post-crash final reconciliation should use the signed, paid
`transcript.completed` webhook when that ClawOps add-on is approved and enabled.
