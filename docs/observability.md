# Observability And Trace Replay

OmniMerchant v3 uses DB-backed traces plus a Trust Console. The goal is to answer why an agent response succeeded or failed, whether eval/RAG safety gates are healthy, and how much the agent costs without storing raw PII-heavy prompts by default.

## Data Model

- `agent_run`: one row per agent/eval execution with trace id, conversation, intent, model, status, failure category, latency, token estimate, and cost estimate.
- `agent_step`: ordered timeline entries for intent routing, retrieval, tool calls, safety blocks, model responses, and failures.
- `tool_call_log`: existing audit table, now linked into trace steps when possible.

## Failure Categories

`FailureAttributionService` normalizes failures into:

`AUTH`, `TENANT`, `RATE_LIMIT`, `LLM_TIMEOUT`, `CIRCUIT_OPEN`, `MODEL_UNAVAILABLE`, `TOOL_EXCEPTION`, `RAG_NO_RESULT`, `RAG_NO_CITATION`, `SAFETY_BLOCK`, `SHOPIFY_API`, `WEBHOOK_INVALID`, `UNKNOWN`.

## APIs

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/observability/summary` | Tenant-scoped KPI summary. |
| `GET` | `/api/observability/failures` | Failure buckets, optionally filtered by category. |
| `GET` | `/api/observability/tools` | Tool call volume, failures, success rate, and P95 latency. |
| `GET` | `/api/observability/eval-trend` | Recent eval pass/tool/RAG safety trend. |
| `GET` | `/api/observability/rag` | Recent RAG citation, retrieval, unsupported-claim, and poisoning metrics. |
| `GET` | `/api/observability/traces` | Trace list filtered by conversation or status. |
| `GET` | `/api/observability/traces/{traceId}` | Full trace timeline. |

All endpoints require admin JWT plus `X-Tenant-Id` membership.

## Metrics

The summary API is computed from local MySQL tables and Micrometer/Actuator exposes health and Prometheus metrics through:

- `/actuator/health`
- `/actuator/prometheus`

The dashboard focuses on AI resolution rate, escalation rate, tool success rate, fallback/failure categories, RAG citation coverage, retrieval precision@k, unsupported claim rate, poisoning block rate, eval pass rate, cost per resolved conversation, P95 first-token latency, P95 full-response latency, P95 tool latency, and Shopify webhook backlog.

## Privacy Boundary

Trace steps store redacted message summaries and metadata by default. Full transcript capture should only be enabled in local demo environments and should not be used for production customer data without explicit privacy controls.
