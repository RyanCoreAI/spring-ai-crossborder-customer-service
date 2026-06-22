param(
    [string]$MysqlContainer = "omni-mysql",
    [string]$PostgresContainer = "omni-postgres",
    [string]$ApiBase = "http://localhost:8090",
    [switch]$SkipDb
)

$ErrorActionPreference = "Stop"

function Invoke-SqlFile($Path) {
    Write-Host "Applying $Path"
    Get-Content -Raw -Encoding UTF8 $Path | docker exec -i $MysqlContainer sh -c 'mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE"'
}

if (-not $SkipDb) {
    Invoke-SqlFile "sql/db_main.sql"
    Invoke-SqlFile "sql/db_extensions.sql"
    Invoke-SqlFile "sql/db_observability.sql"
    Invoke-SqlFile "sql/db_eval_v2.sql"
    Invoke-SqlFile "sql/db_shopify_v2.sql"
    Invoke-SqlFile "sql/db_rag_safety.sql"
    Invoke-SqlFile "sql/demo_seed.sql"
    Write-Host "Applying sql/db_vector.sql"
    Get-Content -Raw -Encoding UTF8 "sql/db_vector.sql" | docker exec -i $PostgresContainer sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
}

Write-Host ""
Write-Host "Demo tenants:"
Write-Host "  OM-FASHION tenantId=1001"
Write-Host "  OM-ELECTRO tenantId=1002"
Write-Host ""
Write-Host "Open frontend:"
Write-Host "  http://localhost:5173/widget"
Write-Host "  http://localhost:5173/admin/inbox"
Write-Host ""
Write-Host "Seeded demo questions:"
Write-Host "  Where is my order #1001? My email is ava@example.com."
Write-Host "  Can I return my rain jacket from #1002? lucia@example.es"
Write-Host "  Recommend a waterproof travel backpack under `$80."
Write-Host "  I am angry because tracking VL2004US is late."
Write-Host ""
Write-Host "Run evals after backend login:"
Write-Host "  ./scripts/run-evals.ps1 -ApiBase $ApiBase"
