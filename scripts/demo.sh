#!/usr/bin/env sh
set -eu

COMPOSE_FILE="${COMPOSE_FILE:-compose.demo.yml}"
API_BASE="${API_BASE:-http://localhost:8090}"
WAIT_SECONDS="${WAIT_SECONDS:-300}"

if [ ! -f .env ]; then
  echo "Missing .env. Copy .env.example to .env and set every required secret." >&2
  exit 1
fi

echo "Starting the demo stack. Flyway will migrate both databases and load deterministic demo data."
docker compose -f "$COMPOSE_FILE" up -d --build

elapsed=0
until curl -fsS "$API_BASE/actuator/health" | grep -q '"status":"UP"'; do
  if [ "$elapsed" -ge "$WAIT_SECONDS" ]; then
    docker compose -f "$COMPOSE_FILE" ps
    echo "Backend did not become healthy. Inspect: docker compose -f $COMPOSE_FILE logs app" >&2
    exit 1
  fi
  sleep 3
  elapsed=$((elapsed + 3))
done

cat <<EOF

OmniMerchant demo is ready.
  Console: http://localhost:5188/login
  Widget:  http://localhost:5188/widget
  Health:  $API_BASE/actuator/health

Flyway loaded tenants OM-FASHION (1001) and OM-ELECTRO (1002).
External channels remain FIXTURE or WAITING_CREDENTIALS until valid merchant credentials are configured.
EOF
