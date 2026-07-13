# Incident Response Runbook

## First 10 Minutes

1. Identify tenant, channel, trace ID, first failure time, and user-visible impact.
2. Freeze high-risk commerce action approvals if duplicate execution or tenant leakage is suspected.
3. Check `/actuator/health`, `/api/sre/summary`, persisted alerts, webhook backlog, tool failures, and RAG index version.
4. Preserve logs and audit rows. Do not enable raw prompt logging to diagnose an incident.

## Redis Unavailable

- Paid LLM endpoints must remain fail-closed or use only the configured tiny local fallback quota.
- Conversation locks and idempotency guards may reject work; do not bypass them to restore throughput.
- Restore Redis, then replay only persisted outbox/DLQ records with their original idempotency keys.

## LLM Provider Unavailable

- Confirm model/circuit-breaker failure category and provider status.
- Keep deterministic commerce queries and human Inbox available.
- Route the conversation to human support; do not silently claim an AI answer succeeded.

## PostgreSQL or RAG Unavailable

- Policy answers with insufficient evidence must refuse or hand off.
- Do not switch to unscoped SQL or a stale quarantined index.
- Restore the last active reviewed index release, then run the RAG CONTRACT gate.

## Shopify or WeChat Backlog

- Inspect webhook/outbox state, retry count, resource version, and duplicate key.
- Old Shopify events must not overwrite a newer resource checkpoint.
- Replays must reuse event/idempotency identity and produce no duplicate side effect.

## Tenant Leakage Suspected

- Disable affected tenant integration credentials and stop external outbound processing.
- Preserve JWT subject, actor, tenant header, mapper query, trace, audit, and tool-call IDs.
- Validate membership, TenantContext cleanup, MyBatis interceptor behavior, and PGVector tenant filters.
- Treat as a security incident; do not close solely because a retry succeeds.

## Recovery Exit Criteria

- Health and SLO snapshots are stable for at least two evaluation windows.
- Backlog drains without duplicate side effects.
- Targeted regression and tenant isolation tests pass.
- Root cause, affected scope, timeline, and follow-up control are recorded.
