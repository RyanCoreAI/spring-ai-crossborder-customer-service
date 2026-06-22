# OmniMerchant v2 Flagship Evidence Checklist

This checklist defines the end condition for the v2 job-search flagship baseline. It is narrower than a production SaaS launch: the goal is public, reproducible evidence for a Spring AI ecommerce customer-service platform.

## Required Gates

- Git contains only source, configuration, SQL, scripts, tests, docs, CI, and intentionally captured screenshots.
- Generated build artifacts stay out of git: `target/`, `dist/`, `node_modules/`, `.codex-logs/`, `.env`, and root `META-INF/`.
- README claims are split into `implemented`, `opt-in`, and `roadmap`; no claim depends on private stores, live merchants, or uncommitted local state.
- A fresh clone can initialize demo data, build backend/frontend, run deterministic evals, and open the merchant console routes.

## Build And Test

```powershell
mvn -q test
mvn -q -pl omni-merchant-bootstrap -am -DskipTests package

cd omnimerchant-web
npm ci
npm run build
npm audit --omit=dev --audit-level=high
cd ..

$env:MYSQL_ROOT_PASSWORD='dev-root-password'
$env:MYSQL_PASSWORD='dev-mysql-password'
$env:PG_PASSWORD='dev-pg-password'
$env:ADMIN_EMAIL='admin@example.com'
$env:ADMIN_PASSWORD='dev-admin-password'
$env:JWT_SECRET='dev-jwt-secret-at-least-32-characters-long'
$env:INTEGRATION_ENCRYPTION_KEY='dev-integration-key-at-least-32-chars'
docker compose config --quiet
```

## Evidence Artifacts

```powershell
.\scripts\demo.ps1
.\scripts\run-evals.ps1
.\scripts\capture-screenshots.ps1
```

Expected outputs:

- `reports/agent-eval-report.json`
- `reports/agent-eval-report.md`
- `reports/agent-eval-junit.xml`
- `docs/assets/screenshots/widget.png`
- `docs/assets/screenshots/dashboard.png`
- `docs/assets/screenshots/evals.png`
- `docs/assets/screenshots/observability.png`
- `docs/assets/screenshots/traces.png`
- `docs/assets/screenshots/rag-safety.png`

## Runtime Smoke

- `POST /api/admin/login` returns `200`.
- `POST /api/widget/session` returns `customerSessionToken` and `expiresAt`.
- `POST /api/widget/chat/stream` without widget token returns `401`.
- `POST /api/widget/chat/stream` with valid widget token returns `200 text/event-stream`.
- Tenant-scoped admin interfaces keep fail-closed behavior: missing tenant, missing JWT, and tenant mismatch return `400`, `401`, and `403`.
- `POST /api/evals/run` creates persisted `agent_eval_run` and case-level `agent_eval_result` rows.
- `/admin/traces`, `/admin/observability`, and `/admin/rag-safety` load after admin login.

## Proof Boundaries

- Shopify is a connector backbone: OAuth install/callback, HMAC, cursor sync jobs, throttle backoff, webhook status, DLQ/replay, and cache mutation are implemented. App Store embedded UI, billing, automated token rotation, and real external write actions are roadmap.
- `LIVE_AGENT` eval is opt-in and requires provider keys. Deterministic eval is the default reproducible gate.
- RAG safety is deterministic scanning plus manual review and citation faithfulness checks. It is not a full adversarial red-team platform.
- Observability is local DB + Micrometer/Prometheus. Langfuse, Jaeger, and Grafana are optional future integrations, not required dependencies.

## Fresh Clone Check

```powershell
git clone <repo-url> OmniMerchant-v2-check
cd OmniMerchant-v2-check
Copy-Item .env.example .env
# Fill required MYSQL_ROOT_PASSWORD, MYSQL_PASSWORD, PG_PASSWORD, ADMIN_EMAIL,
# ADMIN_PASSWORD, JWT_SECRET, and INTEGRATION_ENCRYPTION_KEY.

docker compose config --quiet
mvn -q test
mvn -q -pl omni-merchant-bootstrap -am -DskipTests package
cd omnimerchant-web
npm ci
npm run build
```

Full local runtime also requires Docker services and database initialization:

```powershell
docker compose up -d mysql redis postgres rocketmq-namesrv rocketmq-broker
.\scripts\demo.ps1
mvn -pl omni-merchant-bootstrap -am spring-boot:run -Dspring-boot.run.profiles=dev
```

Open `http://localhost:5173/login`, `http://localhost:5173/widget`, `/admin/evals`, `/admin/traces`, `/admin/observability`, and `/admin/rag-safety`.
