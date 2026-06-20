#!/usr/bin/env bash
set -euo pipefail

API_BASE="${API_BASE:-http://localhost:8090}"
OUTPUT_DIR="${OUTPUT_DIR:-reports}"

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
TMP_JSON="$OUTPUT_DIR/.agent-eval.tmp.json"
: > "$TMP_JSON"

for tenant_id in 1001 1002; do
  echo "Running evals for tenant $tenant_id"
  curl -fsS -X POST "$API_BASE/api/evals/run" \
    -H "Authorization: Bearer $TOKEN" \
    -H "X-Tenant-Id: $tenant_id" >> "$TMP_JSON"
  echo >> "$TMP_JSON"
done

node - "$TMP_JSON" "$JSON_PATH" "$MD_PATH" <<'NODE'
const fs = require('fs');
const [tmp, jsonPath, mdPath] = process.argv.slice(2);
const reports = fs.readFileSync(tmp, 'utf8').trim().split(/\n+/).map(line => JSON.parse(line).data);
fs.writeFileSync(jsonPath, JSON.stringify(reports, null, 2));
const lines = ['# OmniMerchant Agent Eval Report', '', '| Tenant | Total | Passed | Failed | Pass Rate |', '|---:|---:|---:|---:|---:|'];
for (const r of reports) lines.push(`| ${r.tenantId} | ${r.total} | ${r.passed} | ${r.failed} | ${r.passRate}% |`);
lines.push('');
for (const r of reports) {
  lines.push(`## Tenant ${r.tenantId}`, '', '| Case | Intent | Status | Observation |', '|---|---|---|---|');
  for (const c of r.results) lines.push(`| ${c.caseCode} | ${c.intent} | ${c.status} | ${(c.actualObservation || '').replace(/\|/g, '\\|')} |`);
  lines.push('');
}
fs.writeFileSync(mdPath, lines.join('\n'));
NODE

rm -f "$TMP_JSON"
echo "Wrote $JSON_PATH"
echo "Wrote $MD_PATH"
