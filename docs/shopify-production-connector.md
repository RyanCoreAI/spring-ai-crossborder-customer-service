# Shopify Production Connector

OmniMerchant v1 supported a Custom App token path. v2 adds the production connector backbone: OAuth install, HMAC/state validation, sync jobs, webhook status processing, duplicate detection, and replay APIs.

## OAuth Install

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/integrations/shopify/install?shop=store.myshopify.com` | Create install URL with state nonce. |
| `GET` | `/api/integrations/shopify/oauth/callback` | Verify HMAC/state/shop and exchange code for offline token. |

Required environment variables:

- `SHOPIFY_CLIENT_ID`
- `SHOPIFY_CLIENT_SECRET`
- `SHOPIFY_SCOPES`
- `APP_BASE_URL`

The OAuth callback is public because Shopify redirects into it, but it verifies HMAC and nonce before storing encrypted credentials.

## Sync Jobs

`shopify_sync_job` records resource, cursor, status, attempts, last error, throttle cost, and next run time. The admin UI can inspect and retry jobs through:

- `GET /api/integrations/shopify/jobs`
- `POST /api/integrations/shopify/jobs/{jobId}/retry`

The implementation runs resource-specific GraphQL queries for products, customers, and orders, advances `pageInfo.endCursor`, and stores Shopify `extensions.cost.throttleStatus` so the next run backs off when the GraphQL bucket is low. Fulfillment and refund jobs are tracked as derived jobs because those updates are applied through order sync and webhook processors.

## Webhooks

Incoming webhooks use:

- `X-Shopify-Hmac-Sha256` verification
- `X-Shopify-Webhook-Id` idempotency key
- `webhook_event` persistence
- processor status fields: `RECEIVED`, `PROCESSING`, `SUCCESS`, `FAILED`, `DEAD`
- replay API: `POST /api/integrations/shopify/webhooks/{eventId}/replay`

The processor applies verified payloads to the local commerce cache:

- `products/*` updates `product`
- `customers/*` updates `customer`
- `orders/*` updates `order_info`
- `fulfillments/*` updates tracking fields on `order_info`
- `refunds/*` updates refund/payment status on `order_info`

Fixture-backed tests cover webhook HMAC verification, invalid OAuth HMAC rejection before token exchange, REST-id to Shopify GID conversion, GraphQL cursor query construction, throttle backoff, duplicate webhook no-op, product cache mutation, fulfillment tracking mutation, refund status mutation, and failed payload DLQ/retry state.

Do not claim Shopify App Store production parity yet. Missing pieces remain: embedded admin UI, app billing, automated token rotation, broader captured payload coverage from a live development store, and approved external write execution.

## Action Safety

The LLM does not directly refund, cancel, or change addresses. High-risk external actions should create `commerce_action_request` rows for human approval. A later production connector can execute approved actions with explicit audit trails and store-level permissions.
