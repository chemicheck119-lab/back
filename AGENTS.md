# AGENTS.md

## Scope

These instructions apply to the entire backend repository.

## Validation

- Run `./gradlew clean test --no-daemon --console=plain` for code changes.
- Keep BFF OpenAPI artifacts and cross-repository contract metadata synchronized when an API contract changes.
- Treat database migrations and security configuration as high-risk changes requiring focused tests.

## Code Review Rules

- Treat the two-CAS confirmation gate as a safety boundary. Flag any path that invokes or exposes compatibility/CAMEO results without independently confirmed incident and facility CAS values.
- Flag authorization or incident-scope bypasses, unsigned or weak session handling, non-idempotent retries, and audit-history overwrites. Confirmation corrections must remain traceable revisions.
- Flag use of client-supplied AI output as authoritative state when a server-side validated snapshot should be used.
- Flag secrets, cookies, raw incident text, search terms, GPS coordinates, or model payloads written to ordinary logs or metrics. Keep metric labels low-cardinality.
- Flag automatic fallback that fabricates routes, model results, evidence, or safe outcomes. Provider and contract failures must fail closed.
- Flag incompatible schema migrations, missing rollback/backward-compatibility consideration, API contract drift, and behavior changes without proportional tests.

## Fix Guidance

- Prefer minimal, backward-compatible fixes that preserve authorization, auditability, and fail-closed behavior.
- When asked to fix a review finding, add or update a regression test and identify any migration or deployment dependency.
