#!/bin/sh
# Removes all demo seed data (sensor-1 … sensor-500) from the database.
# Usage: ./scripts/unseed-demo.sh

set -e

CONTAINER="${POSTGRES_CONTAINER:-sentinel-postgres}"
DB_NAME="${DB_NAME:-sentinel}"
DB_USER="${DB_USER:-sentinel}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "[unseed] Waiting for PostgreSQL to be ready..."
until docker exec "$CONTAINER" pg_isready -U "$DB_USER" -d "$DB_NAME" -q 2>/dev/null; do
  sleep 1
done

echo "[unseed] Removing demo data..."
docker exec -i "$CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" \
  < "$SCRIPT_DIR/unseed-demo.sql"
