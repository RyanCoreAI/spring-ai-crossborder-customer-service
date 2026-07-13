param(
    [string]$ApiBase = "http://localhost:8090",
    [string]$AdminEmail = $env:ADMIN_EMAIL,
    [string]$AdminPassword = $env:ADMIN_PASSWORD,
    [long[]]$TenantIds = @(1001, 1002),
    [int]$MinApprovedCasesPerTenant = 100,
    [double]$MinPassRate = 95,
    [string]$OutputPath = "reports/gold-eval-gate.json"
)

$ErrorActionPreference = "Stop"

if (-not $AdminEmail -or -not $AdminPassword) {
    throw "ADMIN_EMAIL and ADMIN_PASSWORD must be set or passed as parameters."
}

$loginBody = @{ email = $AdminEmail; password = $AdminPassword } | ConvertTo-Json
$login = Invoke-RestMethod -Method Post -Uri "$ApiBase/api/auth/login" -ContentType "application/json" -Body $loginBody
$token = $login.data.accessToken
if (-not $token) {
    $token = $login.data.token
}
if (-not $token) {
    throw "Login did not return an access token."
}

$evidence = @()
foreach ($tenantId in $TenantIds) {
    $headers = @{
        Authorization = "Bearer $token"
        "X-Tenant-Id" = "$tenantId"
    }
    $datasets = Invoke-RestMethod -Method Get -Uri "$ApiBase/api/rag/datasets?kind=GOLD" -Headers $headers
    $published = @($datasets.data | Where-Object {
        $_.datasetKind -eq "GOLD" -and $_.status -eq "PUBLISHED" -and $null -ne $_.approvedBy
    } | Sort-Object createdAt -Descending)
    if (-not $published.Count) {
        throw "Tenant $tenantId has no human-published GOLD dataset."
    }

    $dataset = $published[0]
    $caseUri = "$ApiBase/api/evals/gold/cases?datasetVersion=$([uri]::EscapeDataString($dataset.version))&annotationStatus=APPROVED&page=1&size=100"
    $cases = Invoke-RestMethod -Method Get -Uri $caseUri -Headers $headers
    if ([int]$cases.data.total -lt $MinApprovedCasesPerTenant) {
        throw "Tenant $tenantId GOLD/$($dataset.version) has $($cases.data.total) approved cases; expected at least $MinApprovedCasesPerTenant."
    }

    $runs = Invoke-RestMethod -Method Get -Uri "$ApiBase/api/evals/runs?page=1&size=100" -Headers $headers
    $run = @($runs.data.records | Where-Object {
        $_.datasetKind -eq "GOLD" -and $_.datasetVersion -eq $dataset.version -and $_.status -eq "COMPLETED"
    } | Sort-Object finishedAt -Descending | Select-Object -First 1)
    if (-not $run.Count) {
        throw "Tenant $tenantId GOLD/$($dataset.version) has no completed eval run."
    }
    if ([double]$run[0].passRate -lt $MinPassRate) {
        throw "Tenant $tenantId GOLD/$($dataset.version) pass rate is $($run[0].passRate)%, below $MinPassRate%."
    }

    $evidence += [pscustomobject]@{
        tenantId = $tenantId
        datasetVersion = $dataset.version
        approvedCases = [int]$cases.data.total
        publishedBy = $dataset.approvedBy
        publishedAt = $dataset.approvedAt
        runUuid = $run[0].runUuid
        passRate = $run[0].passRate
        finishedAt = $run[0].finishedAt
    }
}

$parent = Split-Path -Parent $OutputPath
if ($parent) {
    New-Item -ItemType Directory -Force -Path $parent | Out-Null
}
$result = [pscustomobject]@{
    verifiedAt = (Get-Date).ToString("o")
    minimumApprovedCasesPerTenant = $MinApprovedCasesPerTenant
    minimumPassRate = $MinPassRate
    tenants = $evidence
}
$encoding = New-Object System.Text.UTF8Encoding -ArgumentList $false
[System.IO.File]::WriteAllText(
    [System.IO.Path]::GetFullPath($OutputPath),
    (($result | ConvertTo-Json -Depth 6) -replace "`r`n", "`n") + "`n",
    $encoding
)
Write-Host "GOLD gate passed for $($TenantIds.Count) tenants. Wrote $OutputPath"
