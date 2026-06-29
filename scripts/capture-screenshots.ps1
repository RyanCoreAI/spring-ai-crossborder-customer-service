param(
    [string]$BaseUrl = "http://localhost:5173",
    [string]$ApiBase = "http://localhost:8090",
    [string]$AdminEmail = $env:ADMIN_EMAIL,
    [string]$AdminPassword = $env:ADMIN_PASSWORD,
    [string]$AdminToken = "",
    [string]$OutputDir = "docs/assets/screenshots",
    [string]$TenantId = "1001",
    [int]$DebugPort = 9223,
    [int]$WaitMs = 1800,
    [switch]$PublicOnly
)

$ErrorActionPreference = "Stop"

function Find-Browser {
    $candidates = @(
        "$env:ProgramFiles\Google\Chrome\Application\chrome.exe",
        "${env:ProgramFiles(x86)}\Google\Chrome\Application\chrome.exe",
        "$env:ProgramFiles\Microsoft\Edge\Application\msedge.exe",
        "${env:ProgramFiles(x86)}\Microsoft\Edge\Application\msedge.exe"
    ) | Where-Object { $_ -and (Test-Path $_) }

    if (-not $candidates) {
        throw "Chrome or Edge was not found. Install one browser or capture screenshots manually."
    }
    return $candidates[0]
}

function Wait-For-Cdp {
    param([int]$Port)
    $versionUrl = "http://127.0.0.1:$Port/json/version"
    for ($i = 0; $i -lt 50; $i++) {
        try {
            return Invoke-RestMethod -Uri $versionUrl -TimeoutSec 1
        } catch {
            Start-Sleep -Milliseconds 200
        }
    }
    throw "Chrome DevTools endpoint did not start on port $Port."
}

function New-Cdp-WebSocket {
    param([int]$Port, [string]$Url)
    $targetUrl = "http://127.0.0.1:$Port/json/new?$([uri]::EscapeDataString($Url))"
    try {
        $target = Invoke-RestMethod -Method Put -Uri $targetUrl
    } catch {
        $target = Invoke-RestMethod -Uri $targetUrl
    }
    $ws = [System.Net.WebSockets.ClientWebSocket]::new()
    [void]$ws.ConnectAsync([Uri]$target.webSocketDebuggerUrl, [Threading.CancellationToken]::None).GetAwaiter().GetResult()
    return $ws
}

function Invoke-Cdp {
    param(
        [System.Net.WebSockets.ClientWebSocket]$Socket,
        [int]$Id,
        [string]$Method,
        [hashtable]$Params = @{}
    )

    $payload = @{ id = $Id; method = $Method; params = $Params } | ConvertTo-Json -Depth 20 -Compress
    $bytes = [Text.Encoding]::UTF8.GetBytes($payload)
    $Socket.SendAsync([ArraySegment[byte]]::new($bytes), [System.Net.WebSockets.WebSocketMessageType]::Text, $true, [Threading.CancellationToken]::None).GetAwaiter().GetResult()

    while ($true) {
        $buffer = New-Object byte[] 1048576
        $builder = [System.Text.StringBuilder]::new()
        do {
            $segment = [ArraySegment[byte]]::new($buffer)
            $result = $Socket.ReceiveAsync($segment, [Threading.CancellationToken]::None).GetAwaiter().GetResult()
            if ($result.MessageType -eq [System.Net.WebSockets.WebSocketMessageType]::Close) {
                throw "Chrome DevTools websocket closed while waiting for $Method."
            }
            [void]$builder.Append([Text.Encoding]::UTF8.GetString($buffer, 0, $result.Count))
        } until ($result.EndOfMessage)

        $message = $builder.ToString() | ConvertFrom-Json
        if ($message.id -eq $Id) {
            if ($message.error) {
                throw "CDP $Method failed: $($message.error.message)"
            }
            return $message.result
        }
    }
}

function Get-Admin-Token {
    if ($AdminToken) {
        return $AdminToken
    }
    if (-not $AdminEmail -or -not $AdminPassword) {
        throw "ADMIN_EMAIL and ADMIN_PASSWORD must be set or pass -AdminToken. Use -PublicOnly to capture only public pages."
    }
    $loginBody = @{ email = $AdminEmail; password = $AdminPassword } | ConvertTo-Json
    $login = Invoke-RestMethod -Method Post -Uri "$ApiBase/api/admin/login" -ContentType "application/json" -Body $loginBody
    $token = $login.data.token
    if (-not $token) {
        throw "Admin login did not return a JWT token."
    }
    return $token
}

function Save-Screenshot {
    param(
        [System.Net.WebSockets.ClientWebSocket]$Socket,
        [ref]$CommandId,
        [string]$Url,
        [string]$OutPath
    )
    Write-Host "Capturing $Url -> $OutPath"
    $CommandId.Value++
    Invoke-Cdp -Socket $Socket -Id $CommandId.Value -Method "Page.navigate" -Params @{ url = $Url } | Out-Null
    Start-Sleep -Milliseconds $WaitMs
    $CommandId.Value++
    $shot = Invoke-Cdp -Socket $Socket -Id $CommandId.Value -Method "Page.captureScreenshot" -Params @{
        format = "png"
        captureBeyondViewport = $true
    }
    [IO.File]::WriteAllBytes($OutPath, [Convert]::FromBase64String($shot.data))
}

function Remove-ProfileDir {
    param([string]$Path)
    if (-not (Test-Path $Path)) {
        return
    }
    $resolvedTemp = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
    $resolvedPath = [IO.Path]::GetFullPath($Path)
    if (-not $resolvedPath.StartsWith($resolvedTemp, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to remove profile directory outside temp: $resolvedPath"
    }
    for ($i = 0; $i -lt 6; $i++) {
        try {
            Remove-Item -LiteralPath $resolvedPath -Recurse -Force -ErrorAction Stop
            return
        } catch {
            if ($i -eq 5) {
                Write-Warning "Could not remove temporary browser profile: $($_.Exception.Message)"
                return
            }
            Start-Sleep -Milliseconds 500
        }
    }
}

$resolvedOutputDir = (New-Item -ItemType Directory -Force -Path $OutputDir).FullName
$browser = Find-Browser
$profileDir = Join-Path ([IO.Path]::GetTempPath()) ("omnimerchant-screenshots-" + [Guid]::NewGuid())
New-Item -ItemType Directory -Force -Path $profileDir | Out-Null
$chrome = $null
$socket = $null

try {
    $args = @(
        "--headless=new",
        "--disable-gpu",
        "--hide-scrollbars",
        "--no-first-run",
        "--window-size=1440,1000",
        "--remote-debugging-port=$DebugPort",
        "--user-data-dir=$profileDir",
        "about:blank"
    )
    $chrome = Start-Process -FilePath $browser -ArgumentList $args -PassThru -WindowStyle Hidden
    Wait-For-Cdp -Port $DebugPort | Out-Null

    $socket = New-Cdp-WebSocket -Port $DebugPort -Url $BaseUrl
    $commandId = 1
    Invoke-Cdp -Socket $socket -Id $commandId -Method "Page.enable" | Out-Null
    $commandId++
    Invoke-Cdp -Socket $socket -Id $commandId -Method "Runtime.enable" | Out-Null
    $commandId++
    Invoke-Cdp -Socket $socket -Id $commandId -Method "Emulation.setDeviceMetricsOverride" -Params @{
        width = 1440
        height = 1000
        deviceScaleFactor = 1
        mobile = $false
    } | Out-Null

    $publicPages = @(
        @{ Name = "widget"; Path = "/widget" },
        @{ Name = "login"; Path = "/login" }
    )
    foreach ($page in $publicPages) {
        $out = Join-Path $resolvedOutputDir "$($page.Name).png"
        Save-Screenshot -Socket $socket -CommandId ([ref]$commandId) -Url "$BaseUrl$($page.Path)" -OutPath $out
    }

    if (-not $PublicOnly) {
        $token = Get-Admin-Token
        $escapedToken = ($token | ConvertTo-Json -Compress)
        $emailForStorage = if ($AdminEmail) { $AdminEmail } else { "admin@example.com" }
        $escapedEmail = ($emailForStorage | ConvertTo-Json -Compress)
        $script = "localStorage.setItem('token',$escapedToken); localStorage.setItem('email',$escapedEmail); localStorage.setItem('selectedTenantId','$TenantId');"
        $commandId++
        Invoke-Cdp -Socket $socket -Id $commandId -Method "Page.addScriptToEvaluateOnNewDocument" -Params @{ source = $script } | Out-Null
        $commandId++
        Invoke-Cdp -Socket $socket -Id $commandId -Method "Runtime.evaluate" -Params @{ expression = $script } | Out-Null

        $adminPages = @(
            @{ Name = "dashboard"; Path = "/admin" },
            @{ Name = "inbox"; Path = "/admin/inbox" },
            @{ Name = "orders"; Path = "/admin/orders" },
            @{ Name = "products"; Path = "/admin/products" },
            @{ Name = "tickets"; Path = "/admin/tickets" },
            @{ Name = "integrations"; Path = "/admin/integrations" },
            @{ Name = "evals"; Path = "/admin/evals" },
            @{ Name = "observability"; Path = "/admin/observability" },
            @{ Name = "traces"; Path = "/admin/traces" },
            @{ Name = "rag-workbench"; Path = "/admin/rag-workbench" },
            @{ Name = "rag-safety"; Path = "/admin/rag-safety" }
        )
        foreach ($page in $adminPages) {
            $out = Join-Path $resolvedOutputDir "$($page.Name).png"
            Save-Screenshot -Socket $socket -CommandId ([ref]$commandId) -Url "$BaseUrl$($page.Path)" -OutPath $out
        }
    }

    $count = (Get-ChildItem -LiteralPath $resolvedOutputDir -Filter *.png).Count
    if (-not $PublicOnly -and $count -lt 8) {
        throw "Expected at least 8 screenshots, wrote $count."
    }
    Write-Host "Screenshots written to $resolvedOutputDir"
} finally {
    if ($socket) {
        $socket.Dispose()
    }
    if ($chrome -and -not $chrome.HasExited) {
        Stop-Process -Id $chrome.Id -Force
        Wait-Process -Id $chrome.Id -Timeout 5 -ErrorAction SilentlyContinue
    }
    Remove-ProfileDir -Path $profileDir
}
