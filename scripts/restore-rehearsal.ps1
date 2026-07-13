param(
    [Parameter(Mandatory = $true)][string]$BackupDirectory,
    [string]$ComposeFile = "compose.demo.yml"
)

$ErrorActionPreference = "Stop"
$backup = (Resolve-Path -LiteralPath $BackupDirectory).Path
$mysqlDump = Join-Path $backup "mysql.sql"
$postgresDump = Join-Path $backup "postgres.dump"
if (-not (Test-Path -LiteralPath $mysqlDump) -or -not (Test-Path -LiteralPath $postgresDump)) {
    throw "Backup directory must contain mysql.sql and postgres.dump."
}

$suffix = Get-Date -Format "yyyyMMddHHmmss"
$mysqlDb = "omni_restore_$suffix"
$postgresDb = "omni_restore_$suffix"
$mysql = (& docker compose -f $ComposeFile ps -q mysql).Trim()
$postgres = (& docker compose -f $ComposeFile ps -q postgres).Trim()
if (-not $mysql -or -not $postgres) { throw "Demo database containers must be running." }
$mysqlRootPassword = (& docker exec $mysql printenv MYSQL_ROOT_PASSWORD).Trim()
$postgresUser = (& docker exec $postgres printenv POSTGRES_USER).Trim()
if (-not $mysqlRootPassword -or -not $postgresUser) { throw "Database container credentials are unavailable." }

try {
    & docker exec -e "MYSQL_PWD=$mysqlRootPassword" $mysql mysql -uroot -e "CREATE DATABASE $mysqlDb CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
    if ($LASTEXITCODE -ne 0) { throw "Could not create disposable MySQL database" }
    Get-Content -LiteralPath $mysqlDump -Raw -Encoding UTF8 |
        & docker exec -i -e "MYSQL_PWD=$mysqlRootPassword" $mysql mysql -uroot $mysqlDb
    if ($LASTEXITCODE -ne 0) { throw "MySQL restore failed" }
    $mysqlTables = (& docker exec -e "MYSQL_PWD=$mysqlRootPassword" $mysql mysql -N -uroot $mysqlDb -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN('tenant','app_user','flyway_schema_history');").Trim()
    if ([int]$mysqlTables -lt 3) { throw "MySQL restore is missing required tables" }

    & docker cp $postgresDump "${postgres}:/tmp/restore.dump"
    & docker exec $postgres createdb -U $postgresUser $postgresDb
    if ($LASTEXITCODE -ne 0) { throw "Could not create disposable PostgreSQL database" }
    & docker exec $postgres pg_restore -U $postgresUser -d $postgresDb /tmp/restore.dump
    if ($LASTEXITCODE -ne 0) { throw "PostgreSQL restore failed" }
    $pgTables = (& docker exec $postgres psql -U $postgresUser -d $postgresDb -tAc "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='public';").Trim()
    if ([int]$pgTables -lt 1) { throw "PostgreSQL restore contains no public tables" }

    Write-Host "Restore rehearsal passed: MySQL required tables=$mysqlTables; PostgreSQL public tables=$pgTables"
} finally {
    & docker exec -e "MYSQL_PWD=$mysqlRootPassword" $mysql mysql -uroot -e "DROP DATABASE IF EXISTS $mysqlDb" 2>$null
    & docker exec $postgres dropdb -U $postgresUser --if-exists $postgresDb 2>$null
    & docker exec $postgres rm -f /tmp/restore.dump 2>$null
}
