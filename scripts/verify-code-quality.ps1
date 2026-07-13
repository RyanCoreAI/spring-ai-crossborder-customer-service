param(
    [int]$MaxServiceLines = 800,
    [int]$MaxVueViewLines = 700,
    [int]$MaxBroadTypeUsages = 0,
    [int]$MaxApiViewsWithoutTypes = 0
)

$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$failures = [System.Collections.Generic.List[string]]::new()

function Relative-Path([string]$path) {
    $fullPath = [System.IO.Path]::GetFullPath($path)
    return $fullPath.Substring($repo.Length).TrimStart([char[]]'\/')
}

function Count-Lines([string]$path) {
    return (Get-Content -LiteralPath $path).Count
}

Get-ChildItem -Path $repo -Recurse -File -Filter '*Service.java' |
    Where-Object { $_.FullName -match '[\\/]src[\\/]main[\\/]java[\\/]' } |
    ForEach-Object {
        $lines = Count-Lines $_.FullName
        if ($lines -gt $MaxServiceLines) {
            $failures.Add("Application service exceeds $MaxServiceLines lines: $(Relative-Path $_.FullName) ($lines)")
        }
    }

Get-ChildItem -Path (Join-Path $repo 'omnimerchant-web/src/views') -Recurse -File -Filter '*.vue' |
    ForEach-Object {
        $lines = Count-Lines $_.FullName
        if ($lines -gt $MaxVueViewLines) {
            $failures.Add("Vue view exceeds $MaxVueViewLines lines: $(Relative-Path $_.FullName) ($lines)")
        }
    }

$frontendFiles = Get-ChildItem -Path (Join-Path $repo 'omnimerchant-web/src') -Recurse -File |
    Where-Object { $_.Extension -in '.ts', '.vue' }
$broadTypeUsages = 0
foreach ($file in $frontendFiles) {
    $content = Get-Content -LiteralPath $file.FullName -Raw
    $broadTypeUsages += [regex]::Matches($content, 'ref<any|reactive<any|:\s*any\b|any\[\]').Count
}
if ($broadTypeUsages -gt $MaxBroadTypeUsages) {
    $failures.Add("Broad TypeScript usage increased: $broadTypeUsages > $MaxBroadTypeUsages. Define DTO-backed types first.")
}

$apiViewsWithoutTypes = [System.Collections.Generic.List[string]]::new()
Get-ChildItem -Path (Join-Path $repo 'omnimerchant-web/src/views') -Recurse -File -Filter '*.vue' |
    ForEach-Object {
        $content = Get-Content -LiteralPath $_.FullName -Raw
        if ($content -match 'api\.(get|post|put|delete|patch)' -and $content -notmatch '@/types/') {
            $apiViewsWithoutTypes.Add((Relative-Path $_.FullName))
        }
    }
if ($apiViewsWithoutTypes.Count -gt $MaxApiViewsWithoutTypes) {
    $failures.Add("API views without DTO type imports increased: $($apiViewsWithoutTypes.Count) > $MaxApiViewsWithoutTypes")
}

# Lightweight method-body scan: read-named methods must not mutate through MyBatis mappers.
$readMethodPattern = [regex]'public\s+[^{;]+\s+(?<name>(get|list|summary|page|find|query)\w*)\s*\([^)]*\)\s*\{'
Get-ChildItem -Path $repo -Recurse -File -Filter '*.java' |
    Where-Object { $_.FullName -match '[\\/]src[\\/]main[\\/]java[\\/]' } |
    ForEach-Object {
        $content = Get-Content -LiteralPath $_.FullName -Raw
        foreach ($match in $readMethodPattern.Matches($content)) {
            $start = $match.Index + $match.Length
            $depth = 1
            $cursor = $start
            while ($cursor -lt $content.Length -and $depth -gt 0) {
                if ($content[$cursor] -eq '{') { $depth++ }
                elseif ($content[$cursor] -eq '}') { $depth-- }
                $cursor++
            }
            if ($depth -ne 0) { continue }
            $body = $content.Substring($start, $cursor - $start - 1)
            if ($body -match '(?i)mapper\.(insert|update|delete)|Mapper\.(insert|update|delete)') {
                $failures.Add("Read method mutates persistence: $(Relative-Path $_.FullName)::$($match.Groups['name'].Value)")
            }
        }
    }

Get-ChildItem -Path $repo -Recurse -File -Filter '*Legacy*.java' |
    Where-Object { $_.FullName -match '[\\/]src[\\/]main[\\/]java[\\/]' -and $_.FullName -notmatch '[\\/](adapter|projection)[\\/]' } |
    ForEach-Object { $failures.Add("Legacy compatibility must live in adapter/projection: $(Relative-Path $_.FullName)") }

Write-Host "Code quality metrics: broadTypeUsages=$broadTypeUsages, apiViewsWithoutTypes=$($apiViewsWithoutTypes.Count)"
if ($failures.Count -gt 0) {
    $failures | ForEach-Object { Write-Error $_ }
    exit 1
}

Write-Host 'Code quality gates passed.'
