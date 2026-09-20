# Chemicheck119 ClawOps Gateway

Official ClawOps Node SDK events are bridged to the Chemicheck119 BFF without
placing phone numbers, transcript text, or credentials in application logs.

Flow:

1. `call_start` atomically claims the one recent `WAITING_FOR_CALL` session.
2. completed user utterances from the SDK are delivered as `INTERIM` segments.
3. `call_end` joins those utterances into one `FINAL_PENDING_REVIEW` transcript.
4. the operator must edit and approve that final revision in the BFF before AI analysis.

The official SDK emits completed utterances, not word-level interim deltas. The UI label
therefore means “unreviewed in-call transcript”, not a partial-token STT claim.

## Required secrets and configuration

- `CLAWOPS_API_KEY`
- `CLAWOPS_ACCOUNT_ID`
- `CLAWOPS_FROM_NUMBER`
- `OPENAI_API_KEY`
- `CHEMICHECK119_BACKEND_URL`
- `CHEMICHECK119_PHONE_INGRESS_TOKEN`

Use Secret Manager for every credential. Run exactly one serving gateway instance per
phone number because the ClawOps control connection is exclusive for that number.
Recording is disabled. A real call is not part of the automated test suite and must be
performed only after the account, number, and paid-call approval are available.
