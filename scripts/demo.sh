#!/usr/bin/env bash
set -euo pipefail

MYSQL_CONTAINER="${MYSQL_CONTAINER:-omni-mysql}"
POSTGRES_CONTAINER="${POSTGRES_CONTAINER:-omni-postgres}"
API_BASE="${API_BASE:-http://localhost:8090}"
SKIP_DB="${SKIP_DB:-false}"

if [[ "$SKIP_DB" != "true" ]]; then
  echo "Applying sql/db_main.sql"
  docker exec -i "$MYSQL_CONTAINER" sh -c 'mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE"' < sql/db_main.sql
  echo "Applying sql/db_extensions.sql"
  docker exec -i "$MYSQL_CONTAINER" sh -c 'mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE"' < sql/db_extensions.sql
  echo "Applying sql/db_observability.sql"
  docker exec -i "$MYSQL_CONTAINER" sh -c 'mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE"' < sql/db_observability.sql
  echo "Applying sql/db_eval_v2.sql"
  docker exec -i "$MYSQL_CONTAINER" sh -c 'mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE"' < sql/db_eval_v2.sql
  echo "Applying sql/db_shopify_v2.sql"
  docker exec -i "$MYSQL_CONTAINER" sh -c 'mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE"' < sql/db_shopify_v2.sql
  echo "Applying sql/db_rag_safety.sql"
  docker exec -i "$MYSQL_CONTAINER" sh -c 'mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE"' < sql/db_rag_safety.sql
  echo "Applying sql/demo_seed.sql"
  docker exec -i "$MYSQL_CONTAINER" sh -c 'mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE"' < sql/demo_seed.sql
  echo "Applying sql/db_vector.sql"
  docker exec -i "$POSTGRES_CONTAINER" sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < sql/db_vector.sql
fi

cat <<EOF

Demo tenants:
  OM-FASHION tenantId=1001
  OM-ELECTRO tenantId=1002

Open frontend:
  http://localhost:5173/widget
  http://localhost:5173/admin/inbox

Seeded demo questions:
  Where is my order #1001? My email is ava@example.com.
  Can I return my rain jacket from #1002? lucia@example.es
  Recommend a waterproof travel backpack under \$80.
  I am angry because tracking VL2004US is late.

Run evals after backend login:
  API_BASE=$API_BASE ./scripts/run-evals.sh
EOF
