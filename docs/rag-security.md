# RAG Safety And Evidence Quality

RAG improves factuality and freshness, but it also introduces poisoning and prompt-injection risks through uploaded documents, product descriptions, and retrieved chunks. OmniMerchant v3 keeps a deterministic ingestion review layer before content enters the retrieval path and reports RAG safety metrics through eval and Trust Console.

## Ingestion Review

`RagSafetyScanner` scans knowledge documents for:

- prompt-injection instructions
- hidden Markdown or HTML directives
- dangerous tool/action instructions
- cross-tenant or order-data leakage hints
- likely secrets, tokens, card numbers, and PII
- base64 or zero-width suspicious payloads

Review rows are written to `rag_safety_review` with status, risk metadata, source trust, index version, and approval history.

## States

| State | Meaning |
|---|---|
| `APPROVED` | Content can be indexed. |
| `QUARANTINED` | Content is blocked from indexing until a human approves it. |
| `REJECTED` | Content should not be indexed. |
| `INDEXED` | Content passed review and entered retrieval. |

`PolicyIndexService` checks the latest review before indexing. Strict mode is controlled by `omnimerchant.rag-safety.strict`.

## Evidence Metadata

`sql/db_rag_deepening.sql` extends `knowledge_doc` with source trust and approval metadata. `sql/db_vector.sql` stores the evidence fields that the RAG Workbench reads from PGVector:

- `source_title`, `source_uri`, `source_type`, `source_trust_level`
- `content_hash`, `doc_version`, `effective_from`, `effective_to`
- `neighbor_prev_uuid`, `neighbor_next_uuid`, `section_path`, `language`
- `risk_level`, `index_version`

High-risk or untrusted chunks are filtered out of retrieval. Neighbor UUIDs support context-window inspection without merging unrelated chunks into the prompt.

## Admin APIs

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/rag/safety/docs` | List review records by status or risk. |
| `POST` | `/api/rag/safety/docs/{docUuid}/approve` | Human approval with optional note. |
| `POST` | `/api/rag/safety/docs/{docUuid}/reject` | Human rejection with optional note. |
| `POST` | `/api/rag/query/debug` | Show query rewrite, candidates, fusion, context pack, evidence level. |
| `GET` | `/api/rag/health` | Knowledge health summary. |
| `GET` | `/api/rag/chunks/{chunkUuid}/neighbors` | Inspect neighboring chunks. |

## Citation Faithfulness

`CitationFaithfulnessChecker` verifies that policy answers include citations and that the expected policy claim has lexical support in cited snippets. Deterministic eval policy cases now fail when RAG returns no citation or an unsupported citation.

The deterministic eval report tracks:

- citation coverage
- retrieval precision@k
- unsupported claim rate
- poisoning block rate

If PGVector has not been indexed in a fresh demo, eval uses approved tenant policy documents as a lexical fallback so reviewers can still verify citation and unsupported-claim behavior without an embedding provider.

## Evidence Sufficiency

`RagContextPacker` assigns one of four levels:

| Level | Behavior |
|---|---|
| `NONE` | No usable evidence; answer should refuse or escalate. |
| `WEAK` | Evidence exists but is too weak for a firm policy conclusion. |
| `PARTIAL` | Answer may proceed only with limited-evidence wording. |
| `SUFFICIENT` | The retrieved citations are enough for a clear policy answer. |

This level is returned in `PolicyAnswer.evidenceLevel` and shown in `/admin/rag-workbench`.

## Fixture Evidence

Regression tests cover:

- direct prompt injection plus dangerous tool instructions
- hidden HTML comments and `javascript:` Markdown links
- email/secret redaction in review excerpts
- cross-tenant leakage hints
- long base64 payloads and zero-width text
- missing citations
- citations that do not support the expected policy claim

## Current Limits

The scanner, query planner, context packer, and citation checker are deterministic and rule-based. They catch common injection, data-leak, missing-citation, and weak lexical-support patterns, but they are not a replacement for full content moderation, sentence-level entailment, or human knowledge governance in regulated production support. LLM-as-judge and entailment remain opt-in enhancements, not default CI gates.
