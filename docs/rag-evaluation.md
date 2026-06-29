# RAG Evaluation

OmniMerchant uses deterministic RAG evals as the default gate. The goal is to verify retrieval contracts, citation behavior, no-answer behavior, and poisoning defense without requiring an LLM key.

## Command

```powershell
$env:ADMIN_EMAIL="your-admin@example.com"
$env:ADMIN_PASSWORD="your-admin-password"
.\scripts\run-rag-evals.ps1
```

Outputs:

- `reports/rag-eval-report.json`
- `reports/rag-eval-report.md`
- `reports/rag-eval-junit.xml`

The script logs in through `/api/admin/login`, runs `/api/rag/evals/run` for seeded tenants, and records `/api/rag/health` snapshots. It does not synthesize report values from local files.

## Metrics

| Metric | Source |
|---|---|
| citation coverage | `agent_eval_run.citation_coverage` |
| retrieval precision@k | `agent_eval_run.retrieval_precision_at_k` |
| unsupported claim rate | `agent_eval_run.unsupported_claim_rate` |
| poisoning block rate | `agent_eval_run.poisoning_block_rate` |
| pending reviews / high risk docs | `/api/rag/health` |

`/api/observability/rag` also exposes `recallAtK`, `mrr`, `ndcgAtK`, `noAnswerAccuracy`, and `p95RetrievalLatencyMs`. These are computed from persisted RAG eval result rows: rank and reciprocal-rank fields drive MRR/nDCG, no-answer cases drive no-answer accuracy, and measured retrieval latency drives the P95 latency value.

## Gate

- RAG/security cases matching `INJECT`, `CROSS`, or `POISON` must pass.
- Overall deterministic RAG subset pass rate should stay at least 95%.
- Live LLM judge is opt-in and must not be the only CI signal.
