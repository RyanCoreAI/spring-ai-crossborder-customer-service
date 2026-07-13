param(
    [string[]]$Paths = @(),
    [string]$DockerComposeFile = "",
    [string]$DockerService = "app",
    [string]$Since = "1h"
)

$ErrorActionPreference = "Stop"
$temporaryLog = $null
if ($DockerComposeFile) {
    if (-not (Test-Path -LiteralPath $DockerComposeFile)) {
        throw "Compose file does not exist: $DockerComposeFile"
    }
    $temporaryLog = Join-Path ([System.IO.Path]::GetTempPath()) "omnimerchant-log-scan-$([guid]::NewGuid().ToString('N')).log"
    & docker compose -f $DockerComposeFile logs --no-color --since $Since $DockerService |
        Set-Content -LiteralPath $temporaryLog -Encoding UTF8
    if ($LASTEXITCODE -ne 0) { throw "Could not read Docker Compose logs." }
    $Paths += $temporaryLog
}

$existing = @($Paths | Where-Object { Test-Path -LiteralPath $_ })
if (-not $existing.Count) { throw "Provide -Paths or -DockerComposeFile so a real log source is scanned." }

$patterns = @(
    '(?i)\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b',
    '(?<![A-Za-z0-9-])(?:\+?86[\s-]?)?1[3-9]\d{9}(?![A-Za-z0-9-])',
    '(?<!\d)\+[1-9]\d{7,14}(?!\d)',
    '(?i)(?:api[_-]?key|client[_-]?secret|access[_-]?token|refresh[_-]?token|password)\s*[:=]\s*["'']?[^\s,"'']{8,}',
    '\bsk-[A-Za-z0-9_-]{16,}\b'
)

try {
    $hits = @()
    foreach ($path in $existing) {
        foreach ($pattern in $patterns) {
            $result = & rg -n --hidden --glob '*.log' --glob '*.txt' --glob '*.json' --glob '*.jsonl' --pcre2 $pattern $path 2>$null
            if ($LASTEXITCODE -eq 0) { $hits += $result }
        }
    }

    if ($hits.Count) {
        $hits | Sort-Object -Unique | ForEach-Object { Write-Error $_ }
        throw "Potential raw PII or secret material was found in logs. Review and redact before release."
    }

    Write-Host "Sensitive log scan passed for $($existing.Count) source(s)."
} finally {
    if ($temporaryLog -and (Test-Path -LiteralPath $temporaryLog)) {
        Remove-Item -LiteralPath $temporaryLog -Force
    }
}
