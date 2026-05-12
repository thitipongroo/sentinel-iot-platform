-- ─────────────────────────────────────────────────────────────────────────────
-- Sentinel IoT Platform — Remove Demo Seed Data
--
-- Deletes all devices whose name matches sensor-<number> (sensor-1 … sensor-500)
-- and all associated telemetry, aggregates, and alerts.
--
-- Run:
--   ./scripts/unseed-demo.sh
--   docker exec -i sentinel-postgres psql -U sentinel -d sentinel < scripts/unseed-demo.sql
-- ─────────────────────────────────────────────────────────────────────────────

\set ON_ERROR_STOP on

BEGIN;

SET LOCAL app.org_id = 'a0000000-0000-0000-0000-000000000001';

DO $$
DECLARE
  seed_ids UUID[];
BEGIN
  SELECT ARRAY(
    SELECT id FROM devices
    WHERE name ~ '^sensor-[0-9]+$'
      AND organization_id = 'a0000000-0000-0000-0000-000000000001'
  ) INTO seed_ids;

  IF array_length(seed_ids, 1) IS NULL THEN
    RAISE NOTICE 'No demo devices found — nothing to delete.';
    RETURN;
  END IF;

  DELETE FROM alerts                      WHERE device_id = ANY(seed_ids);
  DELETE FROM telemetry_hourly_aggregates WHERE device_id = ANY(seed_ids);
  DELETE FROM telemetry                   WHERE device_id = ANY(seed_ids);
  DELETE FROM devices                     WHERE id        = ANY(seed_ids);

  RAISE NOTICE 'Removed % demo device(s) and all associated data.', array_length(seed_ids, 1);
END;
$$;

COMMIT;

\echo 'Demo data removed.'
