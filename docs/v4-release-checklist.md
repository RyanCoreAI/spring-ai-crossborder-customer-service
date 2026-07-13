# OmniMerchant v4 Release Gates

v4 has two separate release claims. Gate A is reproducible without private credentials. Gate B requires external merchant systems and cannot be inferred from fixture tests.

## Gate A: Open-source Flagship

- [x] Oracle JDK 21 on Windows: `mvn -q test`.
- [x] Temurin JDK 21 in GitHub Actions: backend, integration, deterministic eval, frontend, browser smoke, compose, and CodeQL are green on `master`.
- [x] `mvn -q -DskipTests package` succeeds.
- [x] `npm ci && npm run test && npm run build` succeeds.
- [x] `npm audit --omit=dev --audit-level=high` has no high vulnerabilities.
- [x] `docker compose -f compose.demo.yml config --quiet` succeeds with required placeholders.
- [x] `scripts/verify-openapi.ps1` and `scripts/verify-evidence.ps1` succeed.
- [ ] A fresh clone starts through `scripts/demo.ps1`; Flyway performs all schema and demo migrations.
- [x] Admin login, Widget session, authenticated SSE, and 400/401/403 tenant contracts pass.
- [x] Request-level specialist tool allowlists and execution-time tenant/risk/approval guards are covered by tests.
- [ ] CONTRACT and human-reviewed GOLD datasets are reported separately.
- [x] UI screenshots are generated from real backend DTOs at desktop and mobile widths.
- [x] README, LICENSE, OpenAPI, reports, screenshots, and scope boundaries agree.

Local evidence was refreshed on 2026-07-13 from the four committed v4 changesets. A fresh local clone completed Maven packaging, `npm ci`, the frontend build, and frontend unit tests without untracked workspace files. The full Compose startup remains unchecked because the local Docker Desktop Linux engine stopped responding during the clean-volume build; no database volume was deleted. The remaining Gate A boxes require green GitHub Actions, a successful Flyway-backed clean startup, or actual human GOLD review and must not be inferred from fixture evidence.

## Gate B: Commercial Beta

- [ ] A real WeChat/KF account passes URL challenge, AES decrypt, receiveId validation, deduplication, outbox, inbound Inbox delivery, outbound send, retry, and receipt checks.
- [ ] A Douyin test store passes OAuth and read-only product/order/refund/logistics sync.
- [ ] A 24-hour soak test has no duplicate ticket, action request, or cross-tenant read.
- [ ] PII log scan, backup restore rehearsal, key rotation, and alert drill pass in the target environment.
- [ ] Only after all above pass may README state that a real domestic channel is connected.

## Explicit Non-claims

- No live Douyin OAuth endpoint is documented until its official test-store contract is implemented and verified.
- No direct external refund, cancellation, address change, or replacement execution.
- No Shopify App Store embedded billing claim.
- No public production SLA or Singapore dual-instance claim before deployment and measurement.
