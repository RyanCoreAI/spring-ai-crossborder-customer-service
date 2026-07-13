$ErrorActionPreference = "Stop"

$required = @(
    "reports/agent-eval-report.json",
    "reports/agent-eval-report.md",
    "reports/agent-eval-junit.xml",
    "reports/rag-eval-report.json",
    "reports/rag-eval-report.md",
    "reports/rag-eval-junit.xml"
)

foreach ($path in $required) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "Missing evidence artifact: $path"
    }
    if ((Get-Item -LiteralPath $path).Length -eq 0) {
        throw "Evidence artifact is empty: $path"
    }
}

$agent = Get-Content -LiteralPath "reports/agent-eval-report.json" -Raw | ConvertFrom-Json
$rag = Get-Content -LiteralPath "reports/rag-eval-report.json" -Raw | ConvertFrom-Json

$agentCases = ($agent.reports | Measure-Object -Property total -Sum).Sum
$ragCases = 0
foreach ($entry in $rag.reports) {
    $report = if ($entry.report) { $entry.report } else { $entry }
    $ragCases += [int]$report.total
}
if ($agentCases -lt 200) {
    throw "Agent evidence must contain at least 200 cases; found $agentCases"
}
if ($ragCases -lt 100) {
    throw "RAG evidence must contain at least 100 cases; found $ragCases"
}

foreach ($report in $agent.reports) {
    if ([double]$report.passRate -lt 95) {
        throw "Agent pass rate is below 95% for tenant $($report.tenantId)"
    }
}

$vectorStatusByTenant = @{}
foreach ($entry in $rag.health) {
    $vectorStatusByTenant[[string]$entry.tenantId] = [string]$entry.health.vectorStatus
}

foreach ($entry in $rag.reports) {
    $report = if ($entry.report) { $entry.report } else { $entry }
    $tenantId = if ($entry.tenantId) { $entry.tenantId } else { $report.tenantId }
    $retrievalMode = if ($entry.retrievalMode) { $entry.retrievalMode } else { $report.retrievalMode }
    $vectorUnavailable = $retrievalMode -eq "VECTOR_ONLY" -and $vectorStatusByTenant[[string]$tenantId] -ne "READY"
    if ($vectorUnavailable) {
        Write-Host "Skipping VECTOR_ONLY quality threshold for tenant ${tenantId}: vector status=$($vectorStatusByTenant[[string]$tenantId])."
        continue
    }
    if ([double]$report.passRate -lt 95) {
        throw "RAG pass rate is below 95% for tenant $tenantId, mode $retrievalMode"
    }
}

foreach ($summary in $rag.runSummaries) {
    $metrics = $summary.latestRun
    foreach ($name in @("recallAtK", "mrr", "ndcgAtK", "noAnswerAccuracy", "p95RetrievalLatencyMs")) {
        if ($null -eq $metrics.$name) {
            throw "RAG metric $name is missing for tenant $($summary.tenantId)"
        }
    }
    if ([double]$metrics.poisoningBlockRate -lt 100) {
        throw "RAG poisoning block rate must be 100% for tenant $($summary.tenantId)"
    }
}

Write-Host "Evidence contracts passed: $agentCases agent cases, $ragCases RAG cases."
