param(
    [string]$ComposeFile = "compose.demo.yml",
    [string]$ApiBase = "http://localhost:8090",
    [string]$WebBase = "http://localhost:5188",
    [string]$ProjectName = $env:COMPOSE_PROJECT_NAME,
    [int]$WaitSeconds = 300,
    [switch]$NoBuild
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path -LiteralPath ".env")) {
    throw "Missing .env. Copy .env.example to .env and set every required secret before starting the demo."
}

$composeArguments = @("compose")
if ($ProjectName) {
    $composeArguments += @("-p", $ProjectName)
}
$composeArguments += @("-f", $ComposeFile)
$arguments = $composeArguments + @("up", "-d")
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
    & docker @composeArguments ps
    throw "Backend did not become healthy within $WaitSeconds seconds. Inspect the selected Compose project's app logs."
}

$webDeadline = (Get-Date).AddSeconds($WaitSeconds)
$webReady = $false
do {
    try {
        $webResponse = Invoke-WebRequest -UseBasicParsing -Uri "$WebBase/login" -TimeoutSec 5
        if ($webResponse.StatusCode -eq 200) {
            $webReady = $true
            break
        }
    } catch {
        Start-Sleep -Seconds 3
    }
} while ((Get-Date) -lt $webDeadline)

if (-not $webReady) {
    & docker @composeArguments ps
    throw "Frontend did not become healthy within $WaitSeconds seconds. Inspect the selected Compose project's web logs."
}

Write-Host ""
Write-Host "OmniMerchant demo is ready."
Write-Host "  Console: $WebBase/login"
Write-Host "  Widget:  $WebBase/widget"
Write-Host "  Health:  $ApiBase/actuator/health"
Write-Host ""
Write-Host "Flyway loaded tenants OM-FASHION (1001) and OM-ELECTRO (1002)."
Write-Host "External channels remain FIXTURE or WAITING_CREDENTIALS until valid merchant credentials are configured."
