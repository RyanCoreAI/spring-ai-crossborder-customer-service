# OmniMerchant Open Source Credibility Audit

Date: 2026-06-20

Audience: GitHub readers evaluating whether this is a credible Spring AI multi-tenant cross-border ecommerce customer-service platform.

## Verdict

**Updated score: 86 / 100**

OmniMerchant has moved from a security-focused Spring AI sample into a credible **Spring Boot 4 + Spring AI 2 trustworthy ecommerce customer-service agent platform v3 baseline**. The project now has tenant-scoped commerce APIs, real order/logistics/product/customer/ticket data models, structured tool outputs, tool-call audit logging, buyer widget entrypoint, merchant workbench pages, Shopify OAuth + cursor sync + webhook processors, deterministic demo seed data, persisted Agent eval runs, trajectory replay, Trust Console APIs, and RAG safety review.

It is still not a production replacement for Gorgias, Fin, Zendesk, or Dify. The remaining gap is production operations and market proof: real hosted demo assets, screenshots/GIF, provider-backed live eval evidence, Shopify App Store embedded/billing flow, token rotation automation, and broader channel integrations.

| Area | Score | Reason |
|---|---:|---|
| Security and multi-tenant credibility | 22 / 25 | JWT membership, tenant fail-closed, SQL fail-closed, DOMPurify output handling, Redis fail-closed, customer order verification, and approval-gated actions are strong. |
| Engineering verifiability | 18 / 20 | Maven, npm, audit, package, compose config, deterministic SQL seed, eval reports, and Testcontainers profile are present; Docker-based integration still depends on local environment. |
| AI architecture quality | 14 / 15 | Tool calling, product/order/logistics/policy/escalation tools, Reactor resilience, model routing, tool audit logging, persisted traces, eval runner, and citation checker are present. |
| Product and competitor differentiation | 12 / 15 | The app has a real helpdesk console, widget, demo commerce data, and Shopify-first connector; commercial-grade channel breadth and live outcome benchmarks remain incomplete. |
| Open-source packaging and docs | 13 / 15 | README, setup path, API list, demo scripts/data, v3 docs, eval docs, and launch script are present; screenshots, GIF/video, and public hosted demo are still missing. |
| Maintainability and ops readiness | 8 / 10 | Modules remain scoped; observability, failure categories, Prometheus, and replay exist. Real migrations, deployment runbooks, token rotation, and external monitoring remain work. |

## Current Repository Evidence

Implemented platform evidence:

- Tenant requests fail closed through JWT principal + `X-Tenant-Id` membership checks.
- MyBatis tenant handler fails closed on missing tenant context and only ignores the global `tenant` table.
- Buyer widget routes are public but resolve tenants explicitly and still rely on tool-level order verification.
- Order-sensitive tool responses require order email or phone before revealing details.
- Refund, replacement, return, and address-change tools create internal approval requests only; they do not write external ecommerce systems.
- Commerce APIs now cover customers, orders, products, escalations, tool calls, dashboard metrics, integrations, evals, widget sessions, and Shopify webhooks.
- Tool calls are persisted to `tool_call_log` with tenant, conversation, params summary, result summary, latency, status, and error fields.
- Demo data includes 2 tenants, 10 customers, 20 products, 30 orders, shipping histories, policy docs, widget channel installs, and 200 deterministic eval cases.
- Frontend now includes merchant pages for inbox, customers, orders, products, tickets, integrations, usage, evals, observability, trace replay, RAG safety, and a public widget page.
- Agent eval runs are persisted to `agent_eval_run` / `agent_eval_result`, scored with tool selection precision/recall, poisoning block rate, citation coverage, retrieval precision@k, and unsupported claim rate.
- Agent execution traces are persisted to `agent_run` / `agent_step`, with failure attribution and replay APIs.
- RAG documents are scanned by `RagSafetyScanner`; policy answers are checked by `CitationFaithfulnessChecker`.
- Shopify sync jobs track resource cursors and GraphQL throttle status; verified webhooks update product, customer, order, fulfillment, and refund cache data.
- Local verification passed:
  - `mvn -q test`
  - `mvn -q -DskipTests package`
  - `mvn -q -Pintegration verify` with Testcontainers skipped because Docker was unavailable
  - `npm ci`
  - `npm run build`
  - `npm audit --omit=dev --audit-level=high`
  - `docker compose config --quiet` with required placeholder env vars

Remaining credibility gaps:

- Screenshot capture script and README matrix exist; hosted demo, GIF, and video are still missing.
- Live provider-backed eval is opt-in and not run by default.
- Shopify connector is stronger but still not an App Store embedded/billing app and does not execute external write actions.
- Static OpenAPI spec is present, but it must stay in sync with DTOs because it is not generated from code.
- Frontend still reports a Vite large chunk warning.
- v3 baseline upgrades the project to Spring Boot 4.1.0, Spring AI 2.0.0, Java 21, Boot 4 compatible MyBatis-Plus/Druid starters, and Testcontainers 2 artifact naming.

## External Benchmark

Official docs and framework baseline:

- [Spring AI ChatClient docs](https://docs.spring.io/spring-ai/reference/api/chatclient.html) support synchronous and streaming model calls; OmniMerchant uses this surface for SSE chat.
- [Spring AI Tool Calling docs](https://docs.spring.io/spring-ai/reference/api/tools.html) match the project’s annotated Java tool approach.
- [Spring AI Advisors docs](https://docs.spring.io/spring-ai/reference/api/advisors.html) support reusable interception and enhancement; OmniMerchant has safety and token-usage advisors but needs deeper observability advisors.
- [Resilience4j Reactor examples](https://resilience4j.readme.io/docs/examples-1) support decorating `Flux` with `CircuitBreakerOperator`, matching the streaming hardening direction.
- [Spring Boot Testcontainers docs](https://docs.spring.io/spring-boot/reference/testing/testcontainers.html) support real dependency testing; the repo has the profile but local Docker was unavailable.

Security and LLM risk baseline:

- [OWASP LLM Top 10 2025](https://genai.owasp.org/llm-top-10/) includes Prompt Injection, Sensitive Information Disclosure, Data and Model Poisoning, Improper Output Handling, Excessive Agency, Vector and Embedding Weaknesses, Misinformation, and Unbounded Consumption.
- OmniMerchant now directly addresses Excessive Agency by gating refunds, replacements, returns, and address changes behind internal approval requests.
- DOMPurify output sanitization addresses Improper Output Handling on the frontend.
- The seeded eval cases cover prompt injection, RAG poisoning, customer/order data leakage, and unsafe tool use; deterministic scoring now records pass rate, tool precision/recall, citation coverage, and poisoning block rate.

Commercial and open-source competitors:

- [Gorgias AI Agent](https://www.gorgias.com/ai-agent) sets the ecommerce-native bar with order tracking, returns, discounts, upsells, analytics, and performance visibility.
- [Fin](https://fin.ai/) and [Intercom pricing](https://www.intercom.com/pricing) show the category is measured by resolution outcomes, not only token usage.
- [Zendesk AI Agents](https://www.zendesk.com/service/ai/ai-agents/) markets high automated resolution and self-improving agents, so OmniMerchant needs visible eval and resolution metrics.
- [Dify](https://github.com/langgenius/dify) wins on workflow/platform breadth; OmniMerchant’s wedge is Java/Spring AI plus multi-tenant commerce security and billing boundaries.
- [Shopify Storefront MCP](https://shopify.dev/docs/apps/build/storefront-mcp) reinforces that product discovery, policy FAQ, cart/order context, and returns are expected ecommerce agent capabilities.

## Top Risks

### P0 - No hosted visual proof yet

Evidence: The backend and frontend now build, seed data exists, and `scripts/capture-screenshots.ps1` can capture public and authenticated routes when the local runtime and admin credentials are available. The repository currently commits public login/widget screenshots; a hosted demo and GIF/video are still missing.

Impact: GitHub visitors cannot judge product completeness in under a minute.

Fix: Commit the screenshot matrix generated from seeded data and add a 90-second GIF using the fixed demo script.

### P1 - Live eval and hosted evidence are still missing

Evidence: Deterministic eval and trace replay exist, but provider-backed `LIVE_AGENT` eval requires explicit secrets and there is no published hosted demo/GIF.

Impact: GitHub readers can verify engineering depth locally, but cannot see model-quality evidence instantly.

Fix: Run live eval in a secret-backed environment, publish the report artifact, and add the 90-second GIF using seeded scenarios.

### P1 - Shopify connector is not App Store production complete

Evidence: OAuth, HMAC, cursor sync, throttle backoff, webhook processors, and replay exist. Missing pieces are embedded app UI, billing, token rotation automation, captured fixture breadth, and approved external write execution.

Impact: It is a credible production connector backbone, not a merchant-ready Shopify App Store app.

Fix: Add fixture-backed webhook/sync tests, token rotation docs, OAuth install UX, and explicit approval execution for selected non-LLM writes.

### P1 - API documentation is static, not generated

Evidence: `docs/openapi.yaml` documents the v3 public, admin, eval, observability, RAG safety, and Shopify endpoints, but it is maintained manually.

Impact: Integrators can inspect the contract quickly, but drift is possible unless API changes update the spec in the same commit.

Fix: Keep `docs/openapi.yaml` in the release gate now; consider `springdoc-openapi` later if runtime-generated docs become worth the dependency.

### P2 - Frontend bundle warning remains

Evidence: `npm run build` passes after the Ant Design Vue migration, but Vite reports the `antd` chunk is larger than the configured warning threshold.

Impact: Not a v1 blocker, but it is polish debt.

Fix: Add Ant Design Vue on-demand imports or more granular manual chunks after screenshots and eval runner are done.

## 30-Day Highest-ROI Roadmap

### Week 1 - Public proof

- Add screenshots and a short GIF using seeded demo flows.
- Add `scripts/demo.ps1` and `scripts/demo.sh` to load schema, extensions, seed, and print demo questions.
- Add docs explaining order verification and approval-gated actions.

### Week 2 - Live eval proof

- Run `LIVE_AGENT` eval in a secret-backed environment.
- Publish deterministic and live Markdown/JSON/JUnit reports.
- Add trace links for failed cases in README screenshots.

### Week 3 - Shopify proof

- Add captured Shopify payload fixtures for orders/products/customers/fulfillments/refunds.
- Smoke OAuth install with a development store.
- Document token rotation and rate-limit recovery behavior.

### Week 4 - API and launch polish

- Keep OpenAPI docs synced with v3 endpoints.
- Add architecture diagram and data-flow diagram.
- Split frontend chunks if needed.
- Keep dependency freshness review as a release gate after the Boot 4 / Spring AI 2 migration.

## Final Recommendation

Market OmniMerchant as:

> An open-source Spring AI cross-border ecommerce customer-service platform with multi-tenant security, commerce tools, Shopify-first integration, billing controls, and eval-ready demo data.

That claim now matches the implementation. The next trust unlock is visual proof plus automated eval output, not more feature breadth.
