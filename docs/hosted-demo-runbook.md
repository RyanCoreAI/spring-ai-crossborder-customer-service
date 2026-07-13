# Hosted Demo Runbook

This runbook defines the boundary for a public OmniMerchant demo. The repository does not currently claim that a hosted URL exists.

## Safe public profile

- Use a dedicated demo deployment and the committed deterministic seed only.
- Do not load Shopify, WeChat/KF, Douyin, email, or live commerce credentials.
- Keep external refund, cancellation, address-change, replacement, and coupon execution disabled. The UI may create an internal approval request only.
- Use a dedicated tenant member account for interactive helpdesk flows and a separate `READ_ONLY_AUDITOR` account for assurance pages. Never expose the platform bootstrap account.
- Rotate demo credentials on every deployment and rate-limit login, Widget sessions, chat, eval runs, and RAG debug calls.
- Keep prompt/tool-content observation disabled. Logs and traces must retain redacted summaries only.
- Reset the demo database on a schedule and discard visitor-entered personal information.

## Deployment gate

Do not publish a URL until all checks pass:

1. Fresh Flyway migrations complete for MySQL and PostgreSQL.
2. `/actuator/health`, `/login`, and `/widget` return 200 through the public TLS endpoint.
3. Missing admin JWT, missing tenant, tenant mismatch, and Widget token mismatch return 401/400/403 as documented.
4. The demo tenant can open Inbox, tickets, actions, RAG Workbench, traces, evals, and observability without cross-tenant data.
5. Destructive external actions and connector credential screens are unavailable to the published demo role.
6. CONTRACT eval and RAG evidence gates pass on the deployed revision.
7. PII log scan, backup/restore rehearsal, cost alert, and rollback procedure are recorded for the deployment.

## Public wording

Use `hosted fixture demo` until a real channel gate passes. `LIVE`, `real channel connected`, and `commercial beta` are prohibited labels without the Gate B evidence in `docs/v4-release-checklist.md`.
