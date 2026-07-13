$ErrorActionPreference = "Stop"

$contract = Get-Content -LiteralPath "docs/openapi.yaml" -Raw -Encoding UTF8
$requiredPaths = @(
    "/api/auth/login",
    "/api/auth/refresh",
    "/api/auth/logout",
    "/api/auth/me",
    "/api/admin/users",
    "/api/public/channels/wechat-kf/{callbackKey}",
    "/api/channels/accounts/{accountId}/credentials",
    "/api/multilingual/detect",
    "/api/evals/gold/cases",
    "/api/qa/summary",
    "/api/rag/datasets",
    "/api/rag/feedback",
    "/api/rag/index/releases",
    "/api/sre/snapshots",
    "/api/sre/alerts",
    "/api/sre/rollouts",
    "/api/integrations/shopify/privacy-requests",
    "/api/integrations/shopify/bulk"
)

foreach ($path in $requiredPaths) {
    $escaped = [regex]::Escape("  ${path}:")
    if ($contract -notmatch $escaped) {
        throw "OpenAPI contract is missing implemented v4 path: $path"
    }
}

$forbiddenLegacyPaths = @(
    "/api/channels/wechat-kf/webhook"
)
foreach ($path in $forbiddenLegacyPaths) {
    if ($contract.Contains("  ${path}:")) {
        throw "OpenAPI still documents removed or unsafe path: $path"
    }
}

Write-Host "OpenAPI v4 contract checks passed for $($requiredPaths.Count) implemented paths."
