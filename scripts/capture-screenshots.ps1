param(
    [string]$BaseUrl = "http://localhost:5173",
    [string]$OutputDir = "docs/assets/screenshots"
)

$ErrorActionPreference = "Stop"

$ResolvedOutputDir = (New-Item -ItemType Directory -Force -Path $OutputDir).FullName

$browserCandidates = @(
    "$env:ProgramFiles\Google\Chrome\Application\chrome.exe",
    "${env:ProgramFiles(x86)}\Google\Chrome\Application\chrome.exe",
    "$env:ProgramFiles\Microsoft\Edge\Application\msedge.exe",
    "${env:ProgramFiles(x86)}\Microsoft\Edge\Application\msedge.exe"
) | Where-Object { $_ -and (Test-Path $_) }

if (-not $browserCandidates) {
    throw "Chrome or Edge was not found. Install one browser or capture screenshots manually."
}

$browser = $browserCandidates[0]
$pages = @(
    @{ Name = "widget"; Path = "/widget" },
    @{ Name = "login"; Path = "/login" }
)

foreach ($page in $pages) {
    $out = Join-Path $ResolvedOutputDir "$($page.Name).png"
    $url = "$BaseUrl$($page.Path)"
    Write-Host "Capturing $url -> $out"
    & $browser --headless --disable-gpu --hide-scrollbars --window-size=1440,1000 "--screenshot=$out" $url | Out-Null
    if (-not (Test-Path $out)) {
        throw "Screenshot was not written: $out"
    }
}

Write-Host "Screenshots written to $ResolvedOutputDir"
