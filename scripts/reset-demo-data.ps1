param(
    [string]$ComposeFile = "compose.demo.yml"
)

$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$composePath = (Resolve-Path (Join-Path $repoRoot $ComposeFile)).Path
$seedPath = (Resolve-Path (Join-Path $repoRoot "omni-merchant-bootstrap/src/main/resources/db/demo/mysql/R__zz_demo_ui_scenarios.sql")).Path

if (-not $composePath.StartsWith($repoRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "Compose file must stay inside the OmniMerchant workspace."
}

$deleteSql = @"
SET FOREIGN_KEY_CHECKS = 0;
DELETE FROM channel_message WHERE message_uuid LIKE 'demo-v4-msg-%';
DELETE FROM channel_conversation WHERE external_thread_id LIKE 'demo-v4-thread-%';
DELETE FROM translation_event WHERE id BETWEEN 56001 AND 56012;
DELETE FROM qa_review_queue WHERE id BETWEEN 55001 AND 55010;
DELETE FROM audit_event WHERE id BETWEEN 59001 AND 59026;
DELETE FROM alert_event WHERE id BETWEEN 58101 AND 58108;
DELETE FROM slo_snapshot WHERE id BETWEEN 58001 AND 58030;
DELETE FROM rag_safety_review WHERE id BETWEEN 57001 AND 57009;
DELETE FROM return_request WHERE id BETWEEN 54001 AND 54010;
DELETE FROM ticket WHERE id BETWEEN 53001 AND 53013;
DELETE FROM chat_message WHERE message_uuid LIKE 'demo-v4-msg-%';
DELETE FROM conversation WHERE id BETWEEN 51001 AND 51020 AND conversation_uuid LIKE 'demo-v4-%';
DELETE FROM app_user WHERE id BETWEEN 91001 AND 91003 AND email LIKE '%@demo.example';
SET FOREIGN_KEY_CHECKS = 1;
"@

Push-Location $repoRoot
try {
    $deleteSql | docker compose -f $composePath exec -T mysql sh -lc 'mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE"'
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to remove the fixed demo-v4 records."
    }

    Get-Content -LiteralPath $seedPath -Raw | docker compose -f $composePath exec -T mysql sh -lc 'mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE"'
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to reload the deterministic demo-v4 seed."
    }

    Write-Host "Demo v4 data reset completed. Only fixed demo-v4 ids and keys were replaced."
}
finally {
    Pop-Location
}
