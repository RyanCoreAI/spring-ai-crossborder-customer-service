param(
    [string]$ComposeFile = "compose.demo.yml",
    [string]$ApiBase = "http://localhost:8090",
    [int]$WaitSeconds = 300,
    [switch]$NoBuild
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path -LiteralPath ".env")) {
    throw "Missing .env. Copy .env.example to .env and set every required secret before starting the demo."
}

$arguments = @("compose", "-f", $ComposeFile, "up", "-d")
if (-not $NoBuild) {
    $arguments += "--build"
}

Write-Host "Starting the demo stack. Flyway will create MySQL/PostgreSQL schemas and load deterministic demo data."
& docker @arguments
if ($LASTEXITCODE -ne 0) {
    throw "docker compose failed with exit code $LASTEXITCODE"
}

$deadline = (Get-Date).AddSeconds($WaitSeconds)
$healthy = $false
do {
    try {
        $health = Invoke-RestMethod -Uri "$ApiBase/actuator/health" -TimeoutSec 5
        if ($health.status -eq "UP") {
            $healthy = $true
            break
        }
    } catch {
        Start-Sleep -Seconds 3
    }
} while ((Get-Date) -lt $deadline)

if (-not $healthy) {
    docker compose -f $ComposeFile ps
    throw "Backend did not become healthy within $WaitSeconds seconds. Inspect: docker compose -f $ComposeFile logs app"
}

Write-Host ""
Write-Host "OmniMerchant demo is ready."
Write-Host "  Console: http://localhost:5188/login"
Write-Host "  Widget:  http://localhost:5188/widget"
Write-Host "  Health:  $ApiBase/actuator/health"
Write-Host ""
Write-Host "Flyway loaded tenants OM-FASHION (1001) and OM-ELECTRO (1002)."
Write-Host "External channels remain FIXTURE or WAITING_CREDENTIALS until valid merchant credentials are configured."
