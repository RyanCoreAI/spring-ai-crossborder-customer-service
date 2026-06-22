#!/usr/bin/env bash
set -euo pipefail

API_BASE="${API_BASE:-http://localhost:8090}"
OUTPUT_DIR="${OUTPUT_DIR:-reports}"
MODE="${MODE:-DETERMINISTIC}"

if [[ -z "${ADMIN_EMAIL:-}" || -z "${ADMIN_PASSWORD:-}" ]]; then
  echo "ADMIN_EMAIL and ADMIN_PASSWORD must be set." >&2
  exit 1
fi

mkdir -p "$OUTPUT_DIR"

LOGIN_JSON=$(printf '{"email":"%s","password":"%s"}' "$ADMIN_EMAIL" "$ADMIN_PASSWORD")
TOKEN=$(curl -fsS -X POST "$API_BASE/api/admin/login" \
  -H "Content-Type: application/json" \
  -d "$LOGIN_JSON" | node -e 'let s="";process.stdin.on("data",d=>s+=d);process.stdin.on("end",()=>console.log(JSON.parse(s).data.token))')

JSON_PATH="$OUTPUT_DIR/agent-eval-report.json"
MD_PATH="$OUTPUT_DIR/agent-eval-report.md"
JUNIT_PATH="$OUTPUT_DIR/agent-eval-junit.xml"
TMP_JSON="$OUTPUT_DIR/.agent-eval.tmp.json"
TMP_RUNS="$OUTPUT_DIR/.agent-eval-runs.tmp.json"
: > "$TMP_JSON"
: > "$TMP_RUNS"

for tenant_id in 1001 1002; do
  echo "Running evals for tenant $tenant_id ($MODE)"
  curl -fsS -X POST "$API_BASE/api/evals/run" \
    -H "Authorization: Bearer $TOKEN" \
    -H "X-Tenant-Id: $tenant_id" \
    -H "Content-Type: application/json" \
    -d "{\"mode\":\"$MODE\",\"failOnThreshold\":false}" >> "$TMP_JSON"
  echo >> "$TMP_JSON"
  curl -fsS "$API_BASE/api/evals/runs?page=1&size=1" \
    -H "Authorization: Bearer $TOKEN" \
    -H "X-Tenant-Id: $tenant_id" >> "$TMP_RUNS"
  echo >> "$TMP_RUNS"
done

node - "$TMP_JSON" "$TMP_RUNS" "$JSON_PATH" "$MD_PATH" "$JUNIT_PATH" "$MODE" <<'NODE'
const fs = require('fs');
const [tmp, runsTmp, jsonPath, mdPath, junitPath, mode] = process.argv.slice(2);
const reports = fs.readFileSync(tmp, 'utf8').trim().split(/\n+/).map(line => JSON.parse(line).data);
const runSummaries = fs.readFileSync(runsTmp, 'utf8').trim().split(/\n+/).filter(Boolean)
  .map(line => JSON.parse(line).data.records?.[0]).filter(Boolean);
fs.writeFileSync(jsonPath, JSON.stringify({
  generatedAt: new Date().toISOString(),
  mode,
  reports,
  runSummaries
}, null, 2));
const lines = [
  '# OmniMerchant Agent Eval Report',
  '',
  `Mode: \`${mode}\``,
  '',
  '| Tenant | Total | Passed | Failed | Pass Rate | Tool Precision | Tool Recall | Citation Coverage | Poisoning Block |',
  '|---:|---:|---:|---:|---:|---:|---:|---:|---:|'
];
for (const r of reports) {
  const latest = runSummaries.find(s => String(s.runUuid || '').length > 0 && Number(s.totalCases || 0) === Number(r.total || 0)) || runSummaries.shift() || {};
  lines.push(`| ${r.tenantId} | ${r.total} | ${r.passed} | ${r.failed} | ${r.passRate}% | ${latest.toolPrecision ?? ''}% | ${latest.toolRecall ?? ''}% | ${latest.citationCoverage ?? ''}% | ${latest.poisoningBlockRate ?? ''}% |`);
}
lines.push('');
for (const r of reports) {
  lines.push(`## Tenant ${r.tenantId}`, '', '| Case | Intent | Status | Observation |', '|---|---|---|---|');
  for (const c of r.results) lines.push(`| ${c.caseCode} | ${c.intent} | ${c.status} | ${(c.actualObservation || '').replace(/\|/g, '\\|')} |`);
  lines.push('');
}
fs.writeFileSync(mdPath, lines.join('\n'));
const esc = (v) => String(v ?? '').replace(/[<>&"]/g, c => ({'<':'&lt;','>':'&gt;','&':'&amp;','"':'&quot;'}[c]));
let tests = 0, failures = 0;
for (const r of reports) { tests += r.total; failures += r.failed; }
const xml = [`<?xml version="1.0" encoding="UTF-8"?>`, `<testsuite name="OmniMerchant Agent Eval" tests="${tests}" failures="${failures}">`];
for (const r of reports) for (const c of r.results) {
  xml.push(`  <testcase classname="agent-eval" name="${esc(`tenant-${r.tenantId}.${c.caseCode}`)}">`);
  if (!c.passed) xml.push(`    <failure message="${esc(c.actualObservation)}" />`);
  xml.push(`  </testcase>`);
}
xml.push(`</testsuite>`);
fs.writeFileSync(junitPath, xml.join('\n'));
NODE

rm -f "$TMP_JSON" "$TMP_RUNS"
echo "Wrote $JSON_PATH"
echo "Wrote $MD_PATH"
echo "Wrote $JUNIT_PATH"
