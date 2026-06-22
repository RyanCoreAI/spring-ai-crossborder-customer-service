param(
    [string]$ApiBase = "http://localhost:8090",
    [string]$AdminEmail = $env:ADMIN_EMAIL,
    [string]$AdminPassword = $env:ADMIN_PASSWORD,
    [string]$OutputDir = "reports",
    [string]$Mode = "DETERMINISTIC"
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
$runSummaries = @()
foreach ($tenantId in $tenantIds) {
    $headers = $headersBase.Clone()
    $headers["X-Tenant-Id"] = "$tenantId"
    Write-Host "Running evals for tenant $tenantId ($Mode)"
    $body = @{ mode = $Mode; failOnThreshold = $false } | ConvertTo-Json
    $report = Invoke-RestMethod -Method Post -Uri "$ApiBase/api/evals/run" -Headers $headers -ContentType "application/json" -Body $body
    $reports += $report.data
    $runs = Invoke-RestMethod -Method Get -Uri "$ApiBase/api/evals/runs?page=1&size=1" -Headers $headers
    $latest = $runs.data.records | Select-Object -First 1
    if ($latest) {
        $runSummaries += [pscustomobject]@{ tenantId = $tenantId; latestRun = $latest }
    }
}

$jsonPath = Join-Path $OutputDir "agent-eval-report.json"
$mdPath = Join-Path $OutputDir "agent-eval-report.md"
$junitPath = Join-Path $OutputDir "agent-eval-junit.xml"
$bundle = [pscustomobject]@{
    generatedAt = (Get-Date).ToString("o")
    mode = $Mode
    reports = $reports
    runSummaries = $runSummaries
}
$bundle | ConvertTo-Json -Depth 12 | Set-Content -Encoding UTF8 $jsonPath

$lines = @("# OmniMerchant Agent Eval Report", "", "Mode: ``$Mode``", "", "| Tenant | Total | Passed | Failed | Pass Rate | Tool Precision | Tool Recall | Citation Coverage | Poisoning Block |", "|---:|---:|---:|---:|---:|---:|---:|---:|---:|")
foreach ($r in $reports) {
    $summary = $runSummaries | Where-Object { $_.tenantId -eq $r.tenantId } | Select-Object -First 1
    $latest = $summary.latestRun
    $toolPrecision = if ($latest) { $latest.toolPrecision } else { "" }
    $toolRecall = if ($latest) { $latest.toolRecall } else { "" }
    $citationCoverage = if ($latest) { $latest.citationCoverage } else { "" }
    $poisoningBlockRate = if ($latest) { $latest.poisoningBlockRate } else { "" }
    $lines += "| $($r.tenantId) | $($r.total) | $($r.passed) | $($r.failed) | $($r.passRate)% | $toolPrecision% | $toolRecall% | $citationCoverage% | $poisoningBlockRate% |"
}
$lines += ""
foreach ($r in $reports) {
    $lines += "## Tenant $($r.tenantId)"
    $lines += ""
    $lines += "| Case | Intent | Status | Observation |"
    $lines += "|---|---|---|---|"
    foreach ($case in $r.results) {
        $obs = ($case.actualObservation -replace "\|", "\|")
        $lines += "| $($case.caseCode) | $($case.intent) | $($case.status) | $obs |"
    }
    $lines += ""
}
$lines | Set-Content -Encoding UTF8 $mdPath

$tests = 0
$failures = 0
foreach ($r in $reports) {
    $tests += [int]$r.total
    $failures += [int]$r.failed
}
$xml = New-Object System.Text.StringBuilder
[void]$xml.AppendLine('<?xml version="1.0" encoding="UTF-8"?>')
[void]$xml.AppendLine("<testsuite name=`"OmniMerchant Agent Eval`" tests=`"$tests`" failures=`"$failures`">")
foreach ($r in $reports) {
    foreach ($case in $r.results) {
        $name = [System.Security.SecurityElement]::Escape("tenant-$($r.tenantId).$($case.caseCode)")
        [void]$xml.AppendLine("  <testcase classname=`"agent-eval`" name=`"$name`">")
        if (-not $case.passed) {
            $msg = [System.Security.SecurityElement]::Escape($case.actualObservation)
            [void]$xml.AppendLine("    <failure message=`"$msg`" />")
        }
        [void]$xml.AppendLine('  </testcase>')
    }
}
[void]$xml.AppendLine('</testsuite>')
$xml.ToString() | Set-Content -Encoding UTF8 $junitPath

Write-Host "Wrote $jsonPath"
Write-Host "Wrote $mdPath"
Write-Host "Wrote $junitPath"
