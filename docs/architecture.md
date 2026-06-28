# OmniMerchant Architecture

OmniMerchant is a Spring AI cross-border ecommerce customer-service platform. It is organized around a tenant-safe commerce cache, an AI agent tool layer, and a merchant helpdesk console.

## Runtime Flow

```mermaid
flowchart LR
  Buyer["Buyer Widget /api/widget"] --> TenantResolve["Tenant Resolve by tenantCode"]
  Admin["Merchant Console /admin"] --> JWT["JWT + X-Tenant-Id Membership"]
  TenantResolve --> Agent["ReActAgentService / Spring AI ChatClient"]
  JWT --> Agent
  Agent --> Safety["SafeGuardAdvisor + RateLimiter"]
  Safety --> Tools["Tool Calling Layer"]
  Tools --> Orders["Order / Logistics Cache"]
  Tools --> Products["Product Catalog"]
  Tools --> Policies["Hybrid RAG Policy Search"]
  Tools --> Tickets["Escalation + Approval Requests"]
  Tools --> Audit["tool_call_log"]
  Agent --> Trace["agent_run / agent_step Trace Replay"]
  Trace --> Obs["Observability + Failure Attribution"]
  Policies --> RagSafety["RAG Safety Review"]
  Shopify["Shopify Webhooks / Sync"] --> Webhook["HMAC + Idempotent webhook_event"]
  Shopify --> Jobs["shopify_sync_job"]
  Webhook --> Orders
  Webhook --> Products
  Webhook --> Customers["Customer Cache"]
```

## Data Boundaries

```mermaid
flowchart TB
  Tenant["tenant"] --> Customer["customer"]
  Tenant --> Order["order_info"]
  Tenant --> Product["product"]
  Tenant --> Knowledge["knowledge_doc"]
  Tenant --> Conversation["conversation"]
  Conversation --> Message["chat_message"]
  Conversation --> ToolLog["tool_call_log"]
  Conversation --> Escalation["escalation_record"]
  Order --> ReturnReq["return_request"]
  Tenant --> Integration["integration_credential"]
  Tenant --> Webhook["webhook_event"]
  Tenant --> Eval["agent_eval_case"]
  Eval --> EvalRun["agent_eval_run / agent_eval_result"]
  Tenant --> Trace["agent_run / agent_step"]
  Knowledge --> RagReview["rag_safety_review"]
  Integration --> SyncJob["shopify_sync_job"]
  Tenant --> ActionRequest["commerce_action_request"]
```

All business tables are tenant-scoped. `tenant` is the only global table ignored by the MyBatis tenant interceptor.

## Safety Model

- Admin API calls require JWT before tenant context is accepted.
- `X-Tenant-Id` is an input hint, not authority; membership is verified from JWT claims.
- Public widget sessions resolve tenant by public tenant code and still require order email or phone for order details.
- Query tools may return data after verification.
- Refund, replacement, return, cancellation, and address-change flows create internal approval requests only.
- Shopify webhook deliveries require HMAC validation and idempotent event recording.
- Tool calls are logged for audit, latency, success rate, eval scoring, and trace replay.

## Demo Data

`sql/demo_seed.sql` creates:

- 2 tenants: `OM-FASHION`, `OM-ELECTRO`
- 10 customers
- 20 products
- 30 orders with realistic statuses and tracking histories
- policy documents
- widget channel installs
- 80 eval cases covering normal and adversarial scenarios

## Extension Points

- Add new ecommerce platforms behind integration services, not inside agent tools.
- Keep external write operations behind approval records until a human or policy engine authorizes them.
- Add channel adapters under `omni-merchant-channel` when email, WhatsApp, or platform chat support is implemented.
