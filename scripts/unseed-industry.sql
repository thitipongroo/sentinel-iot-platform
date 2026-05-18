-- ─────────────────────────────────────────────────────────────────────────────
-- Sentinel IoT — Remove Industry Device Seed Data
--
-- Deletes all 8 industry organisations and their associated devices, telemetry,
-- hourly aggregates, alerts, and admin users created by seed-industry.sql.
-- The default organisation and demo seed data are NOT affected.
--
-- Run:
--   docker exec -i sentinel-postgres psql -U sentinel -d sentinel \
--     < scripts/unseed-industry.sql
-- ─────────────────────────────────────────────────────────────────────────────

\set ON_ERROR_STOP on
BEGIN;

DO $$
DECLARE
  industry_org_ids UUID[] := ARRAY[
    'b1000000-0000-0000-0000-000000000000'::uuid,
    'b2000000-0000-0000-0000-000000000000'::uuid,
    'b3000000-0000-0000-0000-000000000000'::uuid,
    'b4000000-0000-0000-0000-000000000000'::uuid,
    'b5000000-0000-0000-0000-000000000000'::uuid,
    'b6000000-0000-0000-0000-000000000000'::uuid,
    'b7000000-0000-0000-0000-000000000000'::uuid,
    'b8000000-0000-0000-0000-000000000000'::uuid
  ];
  device_ids UUID[];
BEGIN
  SELECT ARRAY(SELECT id FROM devices WHERE organization_id = ANY(industry_org_ids))
  INTO device_ids;

  IF array_length(device_ids, 1) IS NOT NULL THEN
    DELETE FROM alerts                     WHERE device_id      = ANY(device_ids);
    DELETE FROM telemetry_hourly_aggregates WHERE device_id     = ANY(device_ids);
    DELETE FROM telemetry                  WHERE device_id      = ANY(device_ids);
    DELETE FROM devices                    WHERE id             = ANY(device_ids);
  END IF;

  DELETE FROM app_users     WHERE organization_id = ANY(industry_org_ids);
  DELETE FROM organizations WHERE id              = ANY(industry_org_ids);
END;
$$;

COMMIT;

\echo 'Industry seed data removed.'
