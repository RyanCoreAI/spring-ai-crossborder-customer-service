param(
    [string]$ScreenshotDir = "docs/assets/screenshots",
    [string]$Output = "docs/assets/demo/omnimerchant-demo-preview.gif",
    [int]$Width = 960,
    [int]$Height = 667,
    [int]$SecondsPerFrame = 1,
    [int]$Fps = 6,
    [string]$Ffmpeg = "ffmpeg"
)

$ErrorActionPreference = "Stop"

$frames = @(
    "login.png",
    "widget.png",
    "dashboard.png",
    "inbox.png",
    "conversations.png",
    "tickets.png",
    "actions.png",
    "sla.png",
    "qa.png",
    "customers.png",
    "orders.png",
    "products.png",
    "knowledge-chat.png",
    "knowledge.png",
    "rag-workbench.png",
    "rag-safety.png",
    "agent-workflow.png",
    "traces.png",
    "evals.png",
    "tool-calls.png",
    "observability.png",
    "multilingual.png",
    "channels.png",
    "integrations.png",
    "operations.png",
    "sre.png",
    "security.png",
    "audit.png",
    "usage.png",
    "macros.png",
    "tenants.png",
    "users.png"
)

if ($frames.Count -lt 30) {
    throw "The public demo GIF must include at least 30 current runtime screenshots."
}

function Resolve-RepoPath {
    param([string]$Path)
    if ([IO.Path]::IsPathRooted($Path)) {
        return [IO.Path]::GetFullPath($Path)
    }
    return [IO.Path]::GetFullPath((Join-Path (Get-Location) $Path))
}

function Format-ForConcatFile {
    param([string]$Path)
    $normalized = $Path.Replace("\", "/").Replace("'", "'\''")
    return "file '$normalized'"
}

$resolvedScreenshotDir = Resolve-RepoPath $ScreenshotDir
$resolvedOutput = Resolve-RepoPath $Output
$outputDir = Split-Path -Parent $resolvedOutput
New-Item -ItemType Directory -Force -Path $outputDir | Out-Null

$missing = @()
foreach ($frame in $frames) {
    $path = Join-Path $resolvedScreenshotDir $frame
    if (-not (Test-Path $path)) {
        $missing += $path
    }
}

if ($missing.Count -gt 0) {
    throw "Missing screenshots required for demo GIF:`n$($missing -join "`n")"
}

$tempDir = Join-Path ([IO.Path]::GetTempPath()) ("omnimerchant-demo-gif-" + [Guid]::NewGuid())
New-Item -ItemType Directory -Force -Path $tempDir | Out-Null
$concatFile = Join-Path $tempDir "frames.txt"
$paletteFile = Join-Path $tempDir "palette.png"

try {
    $normalizeFilter = "scale=$($Width):-1:flags=lanczos,crop=$($Width):$($Height):0:0,setsar=1"
    $normalizedFrames = New-Object System.Collections.Generic.List[string]
    for ($i = 0; $i -lt $frames.Count; $i++) {
        $source = [IO.Path]::GetFullPath((Join-Path $resolvedScreenshotDir $frames[$i]))
        $target = Join-Path $tempDir ("frame-{0:D3}.png" -f ($i + 1))
        & $Ffmpeg -hide_banner -loglevel error -y -i $source -vf $normalizeFilter -frames:v 1 -update 1 $target
        if ($LASTEXITCODE -ne 0) {
            throw "ffmpeg frame normalization failed for $source with exit code $LASTEXITCODE"
        }
        $normalizedFrames.Add($target)
    }

    $concatLines = New-Object System.Collections.Generic.List[string]
    foreach ($path in $normalizedFrames) {
        $concatLines.Add((Format-ForConcatFile $path))
        $concatLines.Add("duration $SecondsPerFrame")
    }
    $lastFrame = $normalizedFrames[-1]
    $concatLines.Add((Format-ForConcatFile $lastFrame))
    [IO.File]::WriteAllLines($concatFile, $concatLines, [Text.UTF8Encoding]::new($false))

    $paletteFilter = "fps=$Fps,palettegen=max_colors=128"
    & $Ffmpeg -hide_banner -loglevel error -y -f concat -safe 0 -i $concatFile -vf $paletteFilter -frames:v 1 -update 1 $paletteFile
    if ($LASTEXITCODE -ne 0) {
        throw "ffmpeg palette generation failed with exit code $LASTEXITCODE"
    }

    $gifFilter = "fps=$Fps[x];[x][1:v]paletteuse=dither=bayer:bayer_scale=5"
    & $Ffmpeg -hide_banner -loglevel error -y -f concat -safe 0 -i $concatFile -i $paletteFile -lavfi $gifFilter $resolvedOutput
    if ($LASTEXITCODE -ne 0) {
        throw "ffmpeg GIF generation failed with exit code $LASTEXITCODE"
    }

    $size = (Get-Item $resolvedOutput).Length
    Write-Host "Demo GIF written to $resolvedOutput ($size bytes)"
} finally {
    if (Test-Path $tempDir) {
        Remove-Item -LiteralPath $tempDir -Recurse -Force -ErrorAction SilentlyContinue
    }
}
