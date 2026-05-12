#!/bin/sh
# Loads demo seed data into the running PostgreSQL container.
# Usage: ./scripts/seed-demo.sh
#
# Prerequisites: docker compose must be running (./run.sh up  or  make up).

set -e

CONTAINER="${POSTGRES_CONTAINER:-sentinel-postgres}"
DB_NAME="${DB_NAME:-sentinel}"
DB_USER="${DB_USER:-sentinel}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "[seed] Waiting for PostgreSQL to be ready..."
until docker exec "$CONTAINER" pg_isready -U "$DB_USER" -d "$DB_NAME" -q 2>/dev/null; do
  sleep 1
done

echo "[seed] Loading demo data — 5 devices × 30 days (may take 30–60 s)..."
docker exec -i "$CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" \
  < "$SCRIPT_DIR/seed-demo.sql"

echo "[seed] Dashboard → http://localhost:3000"
echo "[seed] Login     → admin / \$INIT_ADMIN_PASSWORD"
