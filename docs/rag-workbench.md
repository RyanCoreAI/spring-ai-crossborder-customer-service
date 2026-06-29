# RAG Workbench

`/admin/rag-workbench` is the operator-facing view for inspecting one RAG query end to end. It is intentionally backed only by API data; when a database has no reviews, vectors, evals, or traces, the page shows empty states instead of sample rows.

## Backend APIs

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/rag/query/debug` | Run query planning, vector/BM25 retrieval, RRF fusion, rerank, context expansion, and evidence packing. |
| `GET` | `/api/rag/health` | Show knowledge health counters. |
| `GET` | `/api/rag/chunks/{chunkUuid}/neighbors` | Inspect previous/next chunks for a hit. |
| `POST` | `/api/rag/evals/run` | Run the RAG-focused deterministic eval subset. |

All APIs require Bearer JWT plus `X-Tenant-Id`; they remain tenant-scoped and fail closed through the same admin boundary as other management APIs.

## What The Page Shows

- query rewrite and expansion terms from `RagQueryPlanningService`
- vector and BM25 candidates
- RRF fused candidates and rerank scores
- expanded neighbor chunks
- context pack text and citation metadata
- `NONE / WEAK / PARTIAL / SUFFICIENT` evidence level
- knowledge health counters from persisted documents and reviews

## Current Limits

The default query planner is deterministic. Live LLM query rewriting is reserved for `omnimerchant.rag.live-query-rewrite=true` and provider credentials, and should not be enabled in CI.
