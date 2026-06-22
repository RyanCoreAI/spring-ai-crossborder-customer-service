# RAG Safety

RAG improves factuality and freshness, but it also introduces poisoning and prompt-injection risks through uploaded documents, product descriptions, and retrieved chunks. OmniMerchant v2 adds a lightweight ingestion review layer before content enters the retrieval path.

## Ingestion Review

`RagSafetyScanner` scans knowledge documents for:

- prompt-injection instructions
- hidden Markdown or HTML directives
- dangerous tool/action instructions
- cross-tenant or order-data leakage hints
- likely secrets, tokens, card numbers, and PII
- base64 or zero-width suspicious payloads

Review rows are written to `rag_safety_review` with status and risk metadata.

## States

| State | Meaning |
|---|---|
| `APPROVED` | Content can be indexed. |
| `QUARANTINED` | Content is blocked from indexing until a human approves it. |
| `REJECTED` | Content should not be indexed. |
| `INDEXED` | Content passed review and entered retrieval. |

`PolicyIndexService` checks the latest review before indexing. Strict mode is controlled by `omnimerchant.rag-safety.strict`.

## Admin APIs

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/rag/safety/docs` | List review records by status or risk. |
| `POST` | `/api/rag/safety/docs/{docUuid}/approve` | Human approval with optional note. |
| `POST` | `/api/rag/safety/docs/{docUuid}/reject` | Human rejection with optional note. |

## Citation Faithfulness

`CitationFaithfulnessChecker` verifies that policy answers include citations and that the expected policy claim has lexical support in cited snippets. Deterministic eval policy cases now fail when RAG returns no citation or an unsupported citation.

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

The scanner and citation checker are deterministic and rule-based. They catch common injection, data-leak, missing-citation, and weak lexical-support patterns, but they are not a replacement for full content moderation, source-trust workflow, or sentence-level entailment checks for regulated production support.
