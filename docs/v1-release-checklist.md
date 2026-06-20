# OmniMerchant v1 Release Checklist

This checklist defines the end condition for the v1 release baseline. It is intentionally narrower than the product roadmap.

## Required Gates

- Git contains only source, configuration, SQL, scripts, tests, docs, and CI files.
- Generated artifacts stay out of git: `target/`, `dist/`, `node_modules/`, `.codex-logs/`, `.env`, and generated root `META-INF/`.
- README claims match source-backed behavior. Roadmap or skeleton capabilities must be labeled as such.
- A fresh clone can install, build, validate compose config, and run the smoke endpoints below without copying local build artifacts.

## Build And Test

```powershell
mvn -q -pl omni-merchant-common,omni-merchant-agent -am "-Dtest=JwtUtilTest,CommerceControllerTest,CommercePlatformServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
mvn -q -pl omni-merchant-bootstrap -am -DskipTests package

cd omnimerchant-web
npm ci
npm run build
npm audit --omit=dev --audit-level=high
cd ..

docker compose config --quiet
```

`docker compose config --quiet` requires the variables from `.env.example`. Use non-production placeholder values for CI/config validation.

## Runtime Smoke

- `POST /api/admin/login` returns `200`.
- `POST /api/widget/session` returns `customerSessionToken` and `expiresAt`.
- `POST /api/widget/chat/stream` without a widget token returns `401`.
- `POST /api/widget/chat/stream` with a valid widget token returns `200 text/event-stream`.
- Admin tenant-scoped interfaces keep fail-closed behavior: missing tenant header, missing JWT, and tenant mismatch return `400`, `401`, and `403` respectively.
- `localhost:5188` and `127.0.0.1:5188` are both accepted by dev CORS when using the default `CORS_ALLOWED_ORIGINS`.

## Fresh Clone Check

```powershell
git clone <repo-url> OmniMerchant-fresh-check
cd OmniMerchant-fresh-check
Copy-Item .env.example .env
# Fill required MYSQL_ROOT_PASSWORD, MYSQL_PASSWORD, PG_PASSWORD, ADMIN_EMAIL,
# ADMIN_PASSWORD, JWT_SECRET, and INTEGRATION_ENCRYPTION_KEY.

docker compose config --quiet
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

Open `http://localhost:5173/login` for the merchant console and `http://localhost:5173/widget` for the buyer widget.

## Known v1 Non-goals

- No production Shopify App Store OAuth install flow.
- No live external refund, cancellation, or address-change mutation by LLM.
- No hosted public demo requirement.
- No mandatory local Testcontainers gate when Docker is unavailable; use `mvn -q -Pintegration verify` in Docker-capable CI or development environments.
