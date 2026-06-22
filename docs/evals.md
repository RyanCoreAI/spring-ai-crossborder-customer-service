# Agent Evals

OmniMerchant v2 adds a repeatable golden conversation runner. It is designed to prove tool-selection, tenant isolation, refusal behavior, citation requirements, and high-risk action gating without requiring a live LLM key.

## Modes

| Mode | Default | Purpose |
|---|---:|---|
| `DETERMINISTIC` | yes | CI-safe checks against seeded commerce data, tool contracts, and safety rules. |
| `LIVE_AGENT` | no | Optional model-backed run for local or secret-backed CI. Requires `omnimerchant.eval.live-agent-enabled=true`. |

The deterministic runner persists every run into `agent_eval_run` and every case result into `agent_eval_result`. It also writes a trace for each case so a failed eval can be replayed in the admin UI.

## Seed Coverage

`sql/demo_seed.sql` seeds 30 enabled cases across two tenants:

- order and logistics verification
- product advice and price constraints
- policy QA and return rules
- return, refund, replacement, address change, and human handoff
- unknown intent handling
- prompt injection, RAG poisoning, cross-tenant, and identity mismatch attempts

## Scoring

`ToolSelectionScorer` calculates:

- precision: actual expected tool calls / all tool calls
- recall: expected tool calls found / expected tool calls
- forbidden tool violation: unsafe or unexpected tool use
- missing expected tools
- citation coverage: policy/RAG cases must return citations that support the expected claim
- poisoning block rate: prompt-injection and RAG-poisoning cases must refuse unsafe instructions and avoid forbidden tools

Current deterministic scoring uses structured rule checks, tool contract metadata, and lexical citation support. LLM-as-judge is intentionally not a CI dependency.

## Commands

```powershell
.\scripts\run-evals.ps1
.\scripts\run-evals.ps1 -Mode LIVE_AGENT
```

```bash
./scripts/run-evals.sh
MODE=LIVE_AGENT ./scripts/run-evals.sh
```

Outputs:

- `reports/agent-eval-report.json`
- `reports/agent-eval-report.md`
- `reports/agent-eval-junit.xml`

The Markdown summary includes pass rate, tool precision/recall, citation coverage, and poisoning block rate. Failed cases include observations and can link back to persisted trace ids in `/admin/traces`.

## Production Notes

- Keep deterministic eval as the hard gate.
- Treat live eval as signal, not the sole release gate.
- Add cases whenever a bug escapes or a new tool/action is added.
- Security and tenant-isolation cases should remain 100% pass before launch.
