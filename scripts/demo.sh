#!/usr/bin/env sh
set -eu

COMPOSE_FILE="${COMPOSE_FILE:-compose.demo.yml}"
API_BASE="${API_BASE:-http://localhost:8090}"
WEB_BASE="${WEB_BASE:-http://localhost:5188}"
WAIT_SECONDS="${WAIT_SECONDS:-300}"
PROJECT_NAME="${PROJECT_NAME:-${COMPOSE_PROJECT_NAME:-}}"

compose() {
  if [ -n "$PROJECT_NAME" ]; then
    docker compose -p "$PROJECT_NAME" -f "$COMPOSE_FILE" "$@"
  else
    docker compose -f "$COMPOSE_FILE" "$@"
  fi
}

if [ ! -f .env ]; then
  echo "Missing .env. Copy .env.example to .env and set every required secret." >&2
  exit 1
fi

echo "Starting the demo stack. Flyway will migrate both databases and load deterministic demo data."
compose up -d --build

elapsed=0
until curl -fsS "$API_BASE/actuator/health" | grep -q '"status":"UP"'; do
  if [ "$elapsed" -ge "$WAIT_SECONDS" ]; then
    compose ps
    echo "Backend did not become healthy. Inspect the selected Compose project's app logs." >&2
    exit 1
  fi
  sleep 3
  elapsed=$((elapsed + 3))
done

elapsed=0
until curl -fsS "$WEB_BASE/login" >/dev/null; do
  if [ "$elapsed" -ge "$WAIT_SECONDS" ]; then
    compose ps
    echo "Frontend did not become healthy. Inspect the selected Compose project's web logs." >&2
    exit 1
  fi
  sleep 3
  elapsed=$((elapsed + 3))
done

cat <<EOF

OmniMerchant demo is ready.
  Console: $WEB_BASE/login
  Widget:  $WEB_BASE/widget
  Health:  $API_BASE/actuator/health

Flyway loaded tenants OM-FASHION (1001) and OM-ELECTRO (1002).
External channels remain FIXTURE or WAITING_CREDENTIALS until valid merchant credentials are configured.
EOF
