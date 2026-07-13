param(
    [string]$ComposeFile = "compose.demo.yml",
    [string]$OutputRoot = "backups"
)

$ErrorActionPreference = "Stop"
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$output = Join-Path $OutputRoot $stamp
New-Item -ItemType Directory -Force -Path $output | Out-Null

$mysql = (& docker compose -f $ComposeFile ps -q mysql).Trim()
$postgres = (& docker compose -f $ComposeFile ps -q postgres).Trim()
if (-not $mysql -or -not $postgres) {
    throw "MySQL and PostgreSQL demo containers must be running."
}

try {
    & docker exec $mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysqldump -u"$MYSQL_USER" --single-transaction --no-tablespaces --routines --triggers "$MYSQL_DATABASE" > /tmp/omnimerchant-mysql.sql'
    if ($LASTEXITCODE -ne 0) { throw "MySQL dump failed" }
    & docker cp "${mysql}:/tmp/omnimerchant-mysql.sql" (Join-Path $output "mysql.sql")

    & docker exec $postgres sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc -f /tmp/omnimerchant-postgres.dump'
    if ($LASTEXITCODE -ne 0) { throw "PostgreSQL dump failed" }
    & docker cp "${postgres}:/tmp/omnimerchant-postgres.dump" (Join-Path $output "postgres.dump")

    @(
        "createdAt=$((Get-Date).ToString('o'))"
        "composeFile=$ComposeFile"
        "mysqlDump=mysql.sql"
        "postgresDump=postgres.dump"
        "containsSecrets=false"
    ) | Set-Content -LiteralPath (Join-Path $output "manifest.txt") -Encoding UTF8
} finally {
    & docker exec $mysql rm -f /tmp/omnimerchant-mysql.sql 2>$null
    & docker exec $postgres rm -f /tmp/omnimerchant-postgres.dump 2>$null
}

Write-Host "Backup written to $((Resolve-Path $output).Path)"
