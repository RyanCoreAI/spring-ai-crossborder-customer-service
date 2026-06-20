# OmniMerchant Open Source Credibility Audit

Date: 2026-06-20

Audience: GitHub readers evaluating whether this is a credible Spring AI multi-tenant cross-border ecommerce customer-service platform.

## Verdict

**Updated score: 79 / 100**

OmniMerchant has moved from a security-focused Spring AI sample into a credible **open-source cross-border ecommerce AI customer-service platform v1**. The project now has tenant-scoped commerce APIs, real order/logistics/product/customer/ticket data models, structured tool outputs, tool-call audit logging, buyer widget entrypoint, merchant workbench pages, Shopify connector skeleton with encrypted credentials and webhook HMAC verification, deterministic demo seed data, and an Agent eval dataset.

It is still not a production replacement for Gorgias, Fin, Zendesk, or Dify. The remaining gap is operational maturity: real hosted demo assets, screenshots/GIF, deeper agent eval automation, observability dashboards, and hardened live Shopify sync coverage.

| Area | Score | Reason |
|---|---:|---|
| Security and multi-tenant credibility | 22 / 25 | JWT membership, tenant fail-closed, SQL fail-closed, DOMPurify output handling, Redis fail-closed, customer order verification, and approval-gated actions are strong. |
| Engineering verifiability | 18 / 20 | Maven, npm, audit, package, compose config, CI, CodeQL, deterministic SQL seed, and Testcontainers profile are present; Docker was unavailable locally so containers were skipped. |
| AI architecture quality | 12 / 15 | Tool calling, product/order/logistics/policy/escalation tools, Reactor resilience, model routing, and tool audit logging are present; durable ReAct trace UI and automated eval runner remain next. |
| Product and competitor differentiation | 11 / 15 | The app now has a real helpdesk console, widget, demo commerce data, and Shopify-first connector shape; commercial-grade channel breadth and outcome analytics remain incomplete. |
| Open-source packaging and docs | 10 / 15 | README, setup path, API list, demo scripts/data, and positioning are clearer; screenshots, GIF/video, and public hosted demo are still missing. |
| Maintainability and ops readiness | 6 / 10 | Modules remain understandable and scoped; monitoring, migrations, deployment runbooks, and dependency freshness still need work. |

## Current Repository Evidence

Implemented platform evidence:

- Tenant requests fail closed through JWT principal + `X-Tenant-Id` membership checks.
- MyBatis tenant handler fails closed on missing tenant context and only ignores the global `tenant` table.
- Buyer widget routes are public but resolve tenants explicitly and still rely on tool-level order verification.
- Order-sensitive tool responses require order email or phone before revealing details.
- Refund, replacement, return, and address-change tools create internal approval requests only; they do not write external ecommerce systems.
- Commerce APIs now cover customers, orders, products, escalations, tool calls, dashboard metrics, integrations, evals, widget sessions, and Shopify webhooks.
- Tool calls are persisted to `tool_call_log` with tenant, conversation, params summary, result summary, latency, status, and error fields.
- Demo data includes 2 tenants, 10 customers, 20 products, 30 orders, shipping histories, policy docs, widget channel installs, and 10 eval cases.
- Frontend now includes merchant pages for inbox, customers, orders, products, tickets, integrations, usage, evals, and a public widget page.
- Local verification passed:
  - `mvn -q test`
  - `mvn -q -DskipTests package`
  - `mvn -q -Pintegration verify` with Testcontainers skipped because Docker was unavailable
  - `npm ci`
  - `npm run build`
  - `npm audit --omit=dev --audit-level=high`
  - `docker compose config --quiet` with required placeholder env vars

Remaining credibility gaps:

- No screenshots, GIF, video, or hosted demo yet.
- Agent eval cases are seeded but not yet executed by a CI-visible eval runner.
- Shopify sync is a v1 Admin GraphQL import path, not a fully hardened production connector.
- No OpenAPI spec or generated API docs yet.
- Frontend still reports a Vite large chunk warning.
- Spring Boot remains at 3.2.5 while dependency freshness should be revisited before public launch.

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
- The seeded eval cases cover prompt injection, RAG poisoning, customer/order data leakage, and unsafe tool use, but need automated scoring.

Commercial and open-source competitors:

- [Gorgias AI Agent](https://www.gorgias.com/ai-agent) sets the ecommerce-native bar with order tracking, returns, discounts, upsells, analytics, and performance visibility.
- [Fin](https://fin.ai/) and [Intercom pricing](https://www.intercom.com/pricing) show the category is measured by resolution outcomes, not only token usage.
- [Zendesk AI Agents](https://www.zendesk.com/service/ai/ai-agents/) markets high automated resolution and self-improving agents, so OmniMerchant needs visible eval and resolution metrics.
- [Dify](https://github.com/langgenius/dify) wins on workflow/platform breadth; OmniMerchant’s wedge is Java/Spring AI plus multi-tenant commerce security and billing boundaries.
- [Shopify Storefront MCP](https://shopify.dev/docs/apps/build/storefront-mcp) reinforces that product discovery, policy FAQ, cart/order context, and returns are expected ecommerce agent capabilities.

## Top Risks

### P0 - No visual proof path yet

Evidence: The backend and frontend now build, and seed data exists, but there are no screenshots, GIF, or hosted demo.

Impact: GitHub visitors cannot judge product completeness in under a minute.

Fix: Add screenshots for widget, inbox, order detail/list, product catalog, ticket queue, integrations, and evals; add a 90-second GIF using seeded scenarios.

### P1 - Eval runner is not automated

Evidence: `agent_eval_case` seed data exists, but there is no command that runs those cases and reports pass/fail.

Impact: The project cannot yet prove correct tool use, refusal behavior, groundedness, or escalation quality.

Fix: Add a keyless eval runner for deterministic tool/service cases and an opt-in LLM eval profile for provider-backed runs.

### P1 - Shopify connector needs production hardening

Evidence: Encrypted credential storage, Admin GraphQL sync, and HMAC webhook recording exist, but sync coverage is intentionally small.

Impact: It is a credible connector skeleton, not a merchant-ready app install flow.

Fix: Add cursor pagination, webhook event processors, retry status transitions, token rotation docs, and integration tests with captured Shopify payload fixtures.

### P1 - API documentation is still manual

Evidence: README lists endpoints, but there is no OpenAPI spec.

Impact: Integrators and reviewers need to inspect code to know request/response shapes.

Fix: Add springdoc-openapi or a static OpenAPI file generated from current DTOs.

### P2 - Frontend bundle warning remains

Evidence: `npm run build` passes but Vite reports a main chunk larger than 500 kB.

Impact: Not a v1 blocker, but it is polish debt.

Fix: Add manual chunks for Element Plus and Markdown/DOMPurify after screenshots and eval runner are done.

## 30-Day Highest-ROI Roadmap

### Week 1 - Public proof

- Add screenshots and a short GIF using seeded demo flows.
- Add `scripts/demo.ps1` and `scripts/demo.sh` to load schema, extensions, seed, and print demo questions.
- Add docs explaining order verification and approval-gated actions.

### Week 2 - Eval automation

- Build an offline eval runner around `agent_eval_case`.
- Score expected tool calls, safe refusal, customer verification, and action gating.
- Publish eval output as a Markdown/JSON artifact.

### Week 3 - Shopify hardening

- Add cursor pagination and fixture-backed tests.
- Implement webhook event processors for order/customer/product/fulfillment updates.
- Add retry and replay controls for failed webhook events.

### Week 4 - API and launch polish

- Add OpenAPI docs.
- Add architecture diagram and data-flow diagram.
- Split frontend chunks if needed.
- Re-run dependency freshness review for Spring Boot and core libraries.

## Final Recommendation

Market OmniMerchant as:

> An open-source Spring AI cross-border ecommerce customer-service platform with multi-tenant security, commerce tools, Shopify-first integration, billing controls, and eval-ready demo data.

That claim now matches the implementation. The next trust unlock is visual proof plus automated eval output, not more feature breadth.
