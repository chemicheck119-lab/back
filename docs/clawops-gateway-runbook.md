# ClawOps Gateway staging runbook

## Boundary

The gateway owns the single reverse WebSocket connection for one 070 number. A new
gateway process takes that number from the old process even when Cloud Run HTTP traffic
has not been shifted. For that reason this service does not use the backend blue/green
traffic procedure as a safety boundary.

## Before deployment

1. Confirm backend V11 is migrated and phone ingress is enabled.
2. Confirm exactly one unexpired BFF `WAITING_FOR_CALL` session exists for the smoke call.
   Prepare it immediately before a consented call, not before a long deployment.
3. Verify Secret Manager contains separate ClawOps API, account, OpenAI, and shared
   phone-ingress secrets. Grant the gateway runtime service account access only to these.
4. Confirm the ClawOps subscription, 070 number, and paid-call authority with the user.
5. Record the previous gateway image digest and revision.
6. Verify shared-token authentication against the exact candidate backend before
   transferring the number. With the gateway-normalized token, POST an end event
   for a freshly generated, nonexistent `AUTH-CHECK-<UUID>` call ID: expect
   `404 PHONE_CALL_NOT_FOUND`, not `401 PHONE_INGRESS_UNAUTHORIZED`. Repeat with an
   invalid token and expect 401. This negative lookup must not use a real call ID
   or create/claim a waiting incident. Do not log secret values or request headers.

Both backend and gateway strip surrounding configuration whitespace from the shared
token. Incoming request tokens must still match exactly. A blank configured token is
never authorized. A healthy `/healthz` proves SDK connectivity, not backend authentication
or end-to-end phone transcription; those checks must be recorded separately. The pinned
SDK's health server handles any path; an authenticated external GET `/` returning
`200 ready` was verified on 2026-10-02. Do not treat a Google-front-end `/healthz` 404
as an SDK failure without checking the external `/` and the startup probe separately.

## Reception lease and release alignment

- Keep Firebase `/api/**` and `/auth/**`, and gateway `CHEMICHECK119_BACKEND_URL`, on
  the same verified BFF candidate. A new backend revision alone does not update the
  pinned Firebase rewrite.
- The visible reception screen renews a 90-second waiting lease every 20 seconds.
  The same authenticated login reuses its active incident. Another login cannot take it.
- Hidden/closed screens stop renewal. Expired or explicitly canceled waiting sessions
  cannot be resurrected by a heartbeat; press reception start to create a fresh session.
- `IN_CALL` must not be canceled as a waiting session. Keep its transcript/audit trail.
- Ending reception is **not** stopping Cloud Run or Cloud SQL billing.
- Remove the old gateway revision tag before deploying its replacement. HTTP traffic
  percentages alone do not control the reverse-WebSocket number owner. Verify the new
  revision is SDK-connected and the prior revision no longer reconnects.

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
disable the exact gateway service with Cloud Run manual scaling `--scaling=0` after
checking for active calls. Do not use `--max-instances=0` as a shutdown command.

## Proposed intermittent operation (not automatically enabled)

Use an explicitly authorized operating window, for example 60 minutes. This is a
staging/demo model, not an always-available emergency intake service.

1. Start the retained SQL instance; wait until RUNNABLE and BFF database readiness passes.
2. Start exactly one gateway instance; verify SDK readiness and the shared-token check.
3. Open the verified dashboard and start reception. Do not advertise a ready telephone
   until all three checks have passed. Keep BFF/model HTTP services request-billed with
   minimum instances zero, subject to latency validation.
4. Before closing, end waiting reception and let an active call finish. Verify FINAL
   transcription and save pending work. Do not kill an active call to meet a timer.
5. With explicit stop authority, disable the gateway and stop (do not delete) SQL:

```sh
gcloud run services update chemicheck119-clawops-gateway-staging --project=chemi-check --region=asia-northeast3 --scaling=0
gcloud sql instances patch chemicheck119-pg-staging --project=chemi-check --activation-policy=NEVER
```

To start an approved window, start SQL first and confirm readiness before the gateway:

```sh
gcloud sql instances patch chemicheck119-pg-staging --project=chemi-check --activation-policy=ALWAYS
gcloud run services update chemicheck119-clawops-gateway-staging --project=chemi-check --region=asia-northeast3 --scaling=1
```

These are operator commands, not a deployed automatic shutdown mechanism. A future
expiry controller needs a server-side deadline, active-call drain, bounded retries,
audited stop results, and failure notification. Do not rely on a browser timer to stop
billable infrastructure. Do not add paid scheduling services without approval.

SQL storage/retained networking and other providers' number/subscription charges may
remain when stopped; accrued charges are not erased. Billing budgets are alerts, not
an unconditional hard ceiling. The Cloud Billing Spend caps preview has supported-
service and ongoing-resource limitations; it is not a substitute for explicit shutdown.

Official references (checked 2026-10-02):

- https://docs.cloud.google.com/run/docs/configuring/services/manual-scaling
- https://docs.cloud.google.com/sql/docs/postgres/start-stop-restart-instance
- https://docs.cloud.google.com/billing/docs/how-to/budgets-spend-caps

## Verification on 2026-10-02

- BFF candidate `candidate-ea82329-1`: configured ingress token gets the expected
  nonexistent-call 404, invalid token gets 401. No secret is logged.
- An explicitly labeled synthetic API chain passed: prepare/reuse/renew; cross-login
  claim rejection; provider bind; INTERIM review rejection; FINAL pre-review analysis
  rejection; exact approved revision; zero/one CAS locked; two synthetic CAS confirmations
  produce supported CAMEO screening and official evidence; record save/readback; next
  incident and cancel with no resurrection. The live model service was used.
- Grounded prose reported `FALLBACK_EXTRACTIVE`, not an LLM-generated narrative.
- This does **not** verify phone carrier routing, audible AI dialogue, real STT, or the
  public browser end-to-end. Record a separately consented short live call before claiming
  service completion. Do not present synthetic confirmations as field observations.

## Current limitation

The realtime SDK has no provider event ID for each transcript callback. The gateway derives
a stable SHA-256 event ID from call ID, segment order, and text, while the BFF enforces unique
provider event IDs. Post-crash final reconciliation should use the signed, paid
`transcript.completed` webhook when that ClawOps add-on is approved and enabled.
