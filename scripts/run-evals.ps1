param(
    [string]$ApiBase = "http://localhost:8090",
    [string]$AdminEmail = $env:ADMIN_EMAIL,
    [string]$AdminPassword = $env:ADMIN_PASSWORD,
    [string]$OutputDir = "reports"
)

$ErrorActionPreference = "Stop"

if (-not $AdminEmail -or -not $AdminPassword) {
    throw "ADMIN_EMAIL and ADMIN_PASSWORD must be set or passed as parameters."
}

New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null

$loginBody = @{ email = $AdminEmail; password = $AdminPassword } | ConvertTo-Json
$login = Invoke-RestMethod -Method Post -Uri "$ApiBase/api/admin/login" -ContentType "application/json" -Body $loginBody
$token = $login.data.token
if (-not $token) {
    throw "Login did not return a JWT token."
}

$headersBase = @{ Authorization = "Bearer $token" }
$tenantIds = @(1001, 1002)
$reports = @()
foreach ($tenantId in $tenantIds) {
    $headers = $headersBase.Clone()
    $headers["X-Tenant-Id"] = "$tenantId"
    Write-Host "Running evals for tenant $tenantId"
    $report = Invoke-RestMethod -Method Post -Uri "$ApiBase/api/evals/run" -Headers $headers
    $reports += $report.data
}

$jsonPath = Join-Path $OutputDir "agent-eval-report.json"
$mdPath = Join-Path $OutputDir "agent-eval-report.md"
$reports | ConvertTo-Json -Depth 8 | Set-Content -Encoding UTF8 $jsonPath

$lines = @("# OmniMerchant Agent Eval Report", "", "| Tenant | Total | Passed | Failed | Pass Rate |", "|---:|---:|---:|---:|---:|")
foreach ($r in $reports) {
    $lines += "| $($r.tenantId) | $($r.total) | $($r.passed) | $($r.failed) | $($r.passRate)% |"
}
$lines += ""
foreach ($r in $reports) {
    $lines += "## Tenant $($r.tenantId)"
    $lines += ""
    $lines += "| Case | Intent | Status | Observation |"
    $lines += "|---|---|---|---|"
    foreach ($case in $r.results) {
        $obs = ($case.actualObservation -replace "\|", "\\|")
        $lines += "| $($case.caseCode) | $($case.intent) | $($case.status) | $obs |"
    }
    $lines += ""
}
$lines | Set-Content -Encoding UTF8 $mdPath

Write-Host "Wrote $jsonPath"
Write-Host "Wrote $mdPath"
