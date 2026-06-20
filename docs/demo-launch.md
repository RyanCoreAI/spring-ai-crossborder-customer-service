# OmniMerchant Demo Launch Guide

This guide defines the public demo story for Phase 7. It intentionally uses seeded local data; no external ecommerce write action is performed.

## Demo Flow

1. Start infrastructure and backend.
2. Run `scripts/demo.ps1` or `scripts/demo.sh`.
3. Open `http://localhost:5173/widget`.
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
7. Run evals from `/admin/evals` or `scripts/run-evals.ps1`.

## Screenshot Checklist

Use `scripts/capture-screenshots.ps1` after Vite is running to generate public screenshots:

```powershell
.\scripts\capture-screenshots.ps1
```

For authenticated admin pages, capture manually after login until a browser automation token fixture is added.

Required screenshots for launch:

- Buyer widget landing/session
- Buyer widget order answer
- Merchant inbox
- Orders list
- Products list
- Tickets list
- Eval run report
- Integrations page

## Launch Positioning

OmniMerchant should be described as:

> An open-source Spring AI cross-border ecommerce customer-service platform with multi-tenant security, commerce tools, Shopify-first integration, billing controls, and eval-ready demo data.

Avoid claiming production parity with commercial helpdesks until real channel connectors, production Shopify install flow, monitoring, and resolution analytics are complete.
