#!/bin/sh
# Loads industry device catalog seed data into the running PostgreSQL container.
# Creates 8 industry organisations, 47 devices, 48 h of telemetry, and sample alerts.
#
# Usage: ./scripts/seed-industry.sh
#
# Prerequisites:
#   docker compose must be running (./run.sh up  or  make up).
#   The default org must already exist (run seed-demo.sh first, or start the backend
#   once so DataInitializer creates the default org and admin users).

set -e

CONTAINER="${POSTGRES_CONTAINER:-sentinel-postgres}"
DB_NAME="${DB_NAME:-sentinel}"
DB_USER="${DB_USER:-sentinel}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "[seed-industry] Waiting for PostgreSQL to be ready..."
until docker exec "$CONTAINER" pg_isready -U "$DB_USER" -d "$DB_NAME" -q 2>/dev/null; do
  sleep 1
done

echo "[seed-industry] Seeding 8 industries × 47 devices (may take 10–20 s)..."
docker exec -i "$CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" \
  < "$SCRIPT_DIR/seed-industry.sql"

echo "[seed-industry] Done."
echo ""
echo "  Industry logins (password: sentinel123):"
echo "    org-manufacturing-admin   org-cold-chain-admin"
echo "    org-datacenter-admin      org-agriculture-admin"
echo "    org-healthcare-admin      org-energy-admin"
echo "    org-smart-building-admin  org-logistics-admin"
echo ""
echo "  POST /api/v1/auth/login"
echo "    { \"username\": \"org-manufacturing-admin\", \"password\": \"sentinel123\" }"
