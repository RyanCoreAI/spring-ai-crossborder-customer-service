# Observability And Trace Replay

OmniMerchant v2 adds DB-backed agent traces and an admin observability surface. The goal is to answer why an agent response succeeded or failed without storing raw PII-heavy prompts by default.

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
| `GET` | `/api/observability/traces` | Trace list filtered by conversation or status. |
| `GET` | `/api/observability/traces/{traceId}` | Full trace timeline. |

All endpoints require admin JWT plus `X-Tenant-Id` membership.

## Metrics

The summary API is computed from local MySQL tables and Micrometer/Actuator exposes health and Prometheus metrics through:

- `/actuator/health`
- `/actuator/prometheus`

The dashboard focuses on AI resolution rate, escalation rate, tool success rate, fallback/failure categories, RAG citation coverage, eval pass rate, cost per conversation, and P95 latency.

## Privacy Boundary

Trace steps store redacted message summaries and metadata by default. Full transcript capture should only be enabled in local demo environments and should not be used for production customer data without explicit privacy controls.
