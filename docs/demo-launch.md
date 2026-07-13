# OmniMerchant Demo Launch Guide

This guide defines the public demo story for Phase 7. It intentionally uses seeded local data; no external ecommerce write action is performed.

## Demo Flow

1. Start infrastructure and backend.
2. Run `scripts/demo.ps1` or `scripts/demo.sh`.
3. Open `http://localhost:5188/widget`.
4. Use tenant `OM-FASHION`.
5. Ask:
   - `Where is my order #1001? My email is ava@example.com.`
   - `Recommend a waterproof travel backpack under $80.`
   - `Can I return my rain jacket from #1002? lucia@example.es`
6. Open merchant console:
   - `/admin/inbox`
   - `/admin/orders`
   - `/admin/products`
   - `/admin/tickets`
   - `/admin/evals`
   - `/admin/traces`
   - `/admin/observability`
   - `/admin/rag-safety`
   - `/admin/integrations`
7. Run evals from `/admin/evals` or `scripts/run-evals.ps1`.

## Screenshot Checklist

Use `scripts/capture-screenshots.ps1` after Vite, backend, and seeded data are running. The script logs in with `ADMIN_EMAIL` / `ADMIN_PASSWORD`, injects the JWT into a temporary Chrome profile, and captures both public and authenticated pages:

```powershell
.\scripts\capture-screenshots.ps1
```

For repository-safe public evidence without an admin account, capture only the buyer widget and login page:

```powershell
.\scripts\capture-screenshots.ps1 -PublicOnly
```

Required screenshots for launch:

- Buyer widget landing/session (`docs/assets/screenshots/widget.png`, committed)
- Merchant login (`docs/assets/screenshots/login.png`, committed)
- Dashboard
- Merchant inbox
- Orders list
- Products list
- Tickets list
- Eval run report
- Trace replay drawer
- Observability dashboard
- RAG safety review list
- Integrations page

## Demo GIF

The repository includes a 32-page preview GIF generated from the latest committed desktop runtime screenshots backed by the deterministic local database:

```text
docs/assets/demo/omnimerchant-demo-preview.gif
```

Rebuild it after refreshing screenshots:

```powershell
.\scripts\capture-screenshots.ps1
.\scripts\create-demo-gif.ps1
```

The GIF covers public entry points, helpdesk workflows, commerce data, RAG/Agent evidence, channels, operations, security, and administration. The full 90-second recording flow remains in `scripts/demo-recording.md`.

## Launch Positioning

OmniMerchant should be described as:

> A Spring Boot 4 + Spring AI 2 trustworthy omnichannel ecommerce helpdesk candidate with multi-tenant security, evidence-grade RAG, controlled agent tools, human handoff, eval reports, trace replay, and connector backbones.

Avoid claiming production parity with commercial helpdesks until the hosted-demo gate, human-reviewed GOLD evidence, a live WeChat/KF channel, a Douyin test store, and the commercial-beta soak/security gates are complete.
