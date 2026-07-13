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
- [x] A fresh clone starts through `scripts/demo.ps1`; Flyway performs all schema and demo migrations.
- [x] Admin login, Widget session, authenticated SSE, and 400/401/403 tenant contracts pass.
- [x] Request-level specialist tool allowlists and execution-time tenant/risk/approval guards are covered by tests.
- [ ] CONTRACT and human-reviewed GOLD datasets are reported separately.
- [x] UI screenshots are generated from real backend DTOs at desktop and mobile widths.
- [x] README, LICENSE, OpenAPI, reports, screenshots, and scope boundaries agree.

Local evidence was refreshed on 2026-07-13 from an isolated clone of commit `1a57bb19` with the current release patch applied. A cold frontend image build completed with Node 22, MySQL applied 26 Flyway migrations, PostgreSQL applied its vector migration, both app and web containers became healthy, proxied admin login and Widget session returned 200, and an unauthenticated order request returned 401. The demo used a separate Compose project and alternate database ports; no existing database volume was deleted. GitHub Actions and CodeQL are green on `master`. The remaining Gate A checkbox requires actual named human GOLD review and must not be inferred from generated CONTRACT or fixture evidence.

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
