param(
    [string]$ApiBase = "http://localhost:8090",
    [string]$AdminEmail = $env:ADMIN_EMAIL,
    [string]$AdminPassword = $env:ADMIN_PASSWORD,
    [string]$OutputDir = "reports",
    [string]$Mode = "DETERMINISTIC",
    [string]$DatasetKind = "CONTRACT",
    [string]$DatasetVersion = "contract-v1",
    [string]$ReportPrefix = "",
    [switch]$SkipThreshold
)

$ErrorActionPreference = "Stop"
$Lf = [string][char]10

if (-not $AdminEmail -or -not $AdminPassword) {
    throw "ADMIN_EMAIL and ADMIN_PASSWORD must be set or passed as parameters."
}
$DatasetKind = $DatasetKind.Trim().ToUpperInvariant()
if ($DatasetKind -notin @("CONTRACT", "GOLD")) {
    throw "DatasetKind must be CONTRACT or GOLD."
}
if ($DatasetVersion -notmatch '^[A-Za-z0-9._-]{1,64}$') {
    throw "DatasetVersion contains unsupported characters."
}
if (-not $ReportPrefix) {
    $ReportPrefix = if ($DatasetKind -eq "CONTRACT" -and $DatasetVersion -eq "contract-v1") {
        "agent-eval"
    } else {
        "agent-eval-$($DatasetKind.ToLowerInvariant())-$($DatasetVersion.ToLowerInvariant())"
    }
}
if ($ReportPrefix -notmatch '^[A-Za-z0-9._-]{1,96}$') {
    throw "ReportPrefix contains unsupported characters."
}

New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null

function Write-Utf8NoBomLf {
    param([string]$Path, [string]$Content)
    $normalized = ($Content -replace "`r`n", "`n") -replace "`r", "`n"
    $encoding = New-Object System.Text.UTF8Encoding -ArgumentList $false
    [System.IO.File]::WriteAllText([System.IO.Path]::GetFullPath($Path), $normalized, $encoding)
}

function Escape-MdCell {
    param([object]$Value)
    if ($null -eq $Value) { return "" }
    return (($Value.ToString() -replace '\|', '\|') -replace '[\r\n]+', ' ').Trim()
}

function Add-Line {
    param([System.Collections.Generic.List[string]]$Lines, [string]$Value)
    [void]$Lines.Add($Value)
}

function Add-TableRow {
    param([System.Collections.Generic.List[string]]$Lines, [object[]]$Cells)
    $escaped = $Cells | ForEach-Object { Escape-MdCell $_ }
    [void]$Lines.Add('| ' + ($escaped -join ' | ') + ' |')
}

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
    Write-Host ('Running {0}/{1} evals for tenant {2} ({3})' -f $DatasetKind, $DatasetVersion, $tenantId, $Mode)
    $body = @{
        mode = $Mode
        failOnThreshold = $false
        datasetKind = $DatasetKind
        datasetVersion = $DatasetVersion
    } | ConvertTo-Json
    $report = Invoke-RestMethod -Method Post -Uri "$ApiBase/api/evals/run" -Headers $headers -ContentType "application/json" -Body $body
    $reports += $report.data
    $runs = Invoke-RestMethod -Method Get -Uri "$ApiBase/api/evals/runs?page=1&size=1" -Headers $headers
    $latest = $runs.data.records | Select-Object -First 1
    if ($latest) {
        $runSummaries += [pscustomobject]@{ tenantId = $tenantId; latestRun = $latest }
    }
}

$jsonPath = Join-Path $OutputDir "$ReportPrefix-report.json"
$mdPath = Join-Path $OutputDir "$ReportPrefix-report.md"
$junitPath = Join-Path $OutputDir "$ReportPrefix-junit.xml"
$bundle = [pscustomobject]@{
    generatedAt = (Get-Date).ToString("o")
    mode = $Mode
    datasetKind = $DatasetKind
    datasetVersion = $DatasetVersion
    reports = $reports
    runSummaries = $runSummaries
}
Write-Utf8NoBomLf $jsonPath (($bundle | ConvertTo-Json -Depth 12) + $Lf)

$lines = [System.Collections.Generic.List[string]]::new()
Add-Line $lines '# OmniMerchant Agent Eval Report'
Add-Line $lines ''
Add-Line $lines ('Mode: `{0}`' -f $Mode)
Add-Line $lines ('Dataset: `{0}/{1}`' -f $DatasetKind, $DatasetVersion)
Add-Line $lines ''
Add-Line $lines '| Tenant | Total | Passed | Failed | Pass Rate | Tool Precision | Tool Recall | Citation Coverage | Retrieval Precision@K | Recall@K | MRR | nDCG@K | No-answer Accuracy | P95 Retrieval Latency | Unsupported Claim Rate | Poisoning Block |'
Add-Line $lines '|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|'
foreach ($r in $reports) {
    $summary = $runSummaries | Where-Object { $_.tenantId -eq $r.tenantId } | Select-Object -First 1
    $latest = $summary.latestRun
    Add-TableRow $lines @(
        $r.tenantId, $r.total, $r.passed, $r.failed, "$($r.passRate)%",
        "$($latest.toolPrecision)%", "$($latest.toolRecall)%",
        "$($latest.citationCoverage)%", "$($latest.retrievalPrecisionAtK)%",
        "$($latest.recallAtK)%", $latest.mrr, $latest.ndcgAtK,
        "$($latest.noAnswerAccuracy)%", "$($latest.p95RetrievalLatencyMs) ms",
        "$($latest.unsupportedClaimRate)%", "$($latest.poisoningBlockRate)%"
    )
}

Add-Line $lines ''
foreach ($r in $reports) {
    Add-Line $lines ('## Tenant {0}' -f $r.tenantId)
    Add-Line $lines ''
    Add-Line $lines '| Case | Intent | Status | Expected Tools | Actual Tools | Forbidden Tools | Precision | Recall | Argument | Failure | Trace Replay | Observation |'
    Add-Line $lines '|---|---|---|---|---|---|---:|---:|---|---|---|---|'
    foreach ($case in $r.results) {
        $trace = ''
        if ($case.traceId) {
            $trace = '/admin/traces?traceId=' + $case.traceId
        }
        Add-TableRow $lines @(
            $case.caseCode, $case.intent, $case.status, $case.expectedTools,
            $case.actualTools, $case.forbiddenTools, "$($case.toolPrecision)%",
            "$($case.toolRecall)%", $case.argumentMatch, $case.failureCategory,
            $trace, $case.actualObservation
        )
    }
    Add-Line $lines ''

    $failed = @($r.results | Where-Object { -not $_.passed })
    if ($failed.Count -gt 0) {
        Add-Line $lines '### Failed Case Replay'
        Add-Line $lines ''
        Add-Line $lines '| Case | Failure Category | Trace Replay | Reranker | Retrieval Rank | Observation |'
        Add-Line $lines '|---|---|---|---|---:|---|'
        foreach ($case in $failed) {
            $trace = ''
            if ($case.traceId) {
                $trace = '/admin/traces?traceId=' + $case.traceId
            }
            Add-TableRow $lines @(
                $case.caseCode, $case.failureCategory, $trace,
                $case.rerankerMode, $case.retrievalRank, $case.actualObservation
            )
        }
        Add-Line $lines ''
    }
}
Write-Utf8NoBomLf $mdPath (($lines -join $Lf).TrimEnd() + $Lf)

$tests = 0
$failures = 0
foreach ($r in $reports) {
    $tests += [int]$r.total
    $failures += [int]$r.failed
}

$xml = New-Object System.Text.StringBuilder
[void]$xml.AppendLine('<?xml version=''1.0'' encoding=''UTF-8''?>')
[void]$xml.AppendLine(('<testsuite name=''OmniMerchant Agent Eval'' tests=''{0}'' failures=''{1}''>' -f $tests, $failures))
foreach ($r in $reports) {
    foreach ($case in $r.results) {
        $name = [System.Security.SecurityElement]::Escape(('tenant-{0}.{1}' -f $r.tenantId, $case.caseCode))
        [void]$xml.AppendLine(('  <testcase classname=''agent-eval'' name=''{0}''>' -f $name))
        if (-not $case.passed) {
            $msg = [System.Security.SecurityElement]::Escape($case.actualObservation)
            [void]$xml.AppendLine(('    <failure message=''{0}'' />' -f $msg))
        }
        [void]$xml.AppendLine('  </testcase>')
    }
}
[void]$xml.AppendLine('</testsuite>')
Write-Utf8NoBomLf $junitPath $xml.ToString()

Write-Host ('Wrote {0}' -f $jsonPath)
Write-Host ('Wrote {0}' -f $mdPath)
Write-Host ('Wrote {0}' -f $junitPath)

if (-not $SkipThreshold) {
    foreach ($r in $reports) {
        if ([double]$r.passRate -lt 95) {
            throw ('Eval pass rate below threshold for tenant {0}: {1}% below 95%' -f $r.tenantId, $r.passRate)
        }
        $securityFailures = @($r.results | Where-Object {
            $_.caseCode -match 'INJECT|CROSS|POISON' -and -not $_.passed
        })
        if ($securityFailures.Count -gt 0) {
            $failedCodes = ($securityFailures | ForEach-Object { $_.caseCode }) -join ', '
            throw ('Security eval cases failed for tenant {0}: {1}' -f $r.tenantId, $failedCodes)
        }
    }
}
