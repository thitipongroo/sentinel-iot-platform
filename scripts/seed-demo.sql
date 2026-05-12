-- ─────────────────────────────────────────────────────────────────────────────
-- Sentinel IoT Platform — Demo Seed Data (500 devices)
--
-- Seeds 500 devices + ~1 000 000 telemetry rows (7 days, 5-min intervals)
-- + hourly aggregates + ~120 sample alerts.
--
-- Device layout: 100 "buildings" × 5 sensor types per building
--   Profile 1 (n%5=1) — Assembly Line   : 60–82 °C
--   Profile 2 (n%5=2) — Cold Storage    : 15–25 °C, high humidity
--   Profile 3 (n%5=3) — Engine Room     : 70–92 °C  ← most alerts
--   Profile 4 (n%5=4) — Server Room     : 18–28 °C, stable
--   Profile 0 (n%5=0) — Packaging Area  : 22–35 °C, active motion
--
-- Devices 491–500 are OFFLINE (demo of device-down state).
-- sensor-1 / sensor-2 / sensor-3 match simulator device IDs
-- so live readings merge automatically into the seeded history.
--
-- Run:
--   ./scripts/seed-demo.sh
--   docker exec -i sentinel-postgres psql -U sentinel -d sentinel < scripts/seed-demo.sql
--
-- Safe to re-run: removes all sensor-N devices (and their data) before inserting.
-- ─────────────────────────────────────────────────────────────────────────────

\set ON_ERROR_STOP on

BEGIN;

SET LOCAL app.org_id = 'a0000000-0000-0000-0000-000000000001';

-- ── 0. Clean up previous seed run ────────────────────────────────────────────
-- Removes any device whose name matches sensor-<number> in the default org.

DO $$
DECLARE
  seed_ids UUID[];
BEGIN
  SELECT ARRAY(
    SELECT id FROM devices
    WHERE name ~ '^sensor-[0-9]+$'
      AND organization_id = 'a0000000-0000-0000-0000-000000000001'
  ) INTO seed_ids;

  IF array_length(seed_ids, 1) IS NOT NULL THEN
    DELETE FROM alerts                   WHERE device_id = ANY(seed_ids);
    DELETE FROM telemetry_hourly_aggregates WHERE device_id = ANY(seed_ids);
    DELETE FROM telemetry                WHERE device_id = ANY(seed_ids);
    DELETE FROM devices                  WHERE id        = ANY(seed_ids);
  END IF;
END;
$$;

-- ── 1. Devices (500) ─────────────────────────────────────────────────────────

INSERT INTO devices (id, name, status, description, location,
                     organization_id, lifecycle_status, firmware_version,
                     created_at, last_seen)
SELECT
  -- UUID: 00000001-0000-0000-0000-000000000001 … 000001f4-0000-0000-0000-0000000001f4
  ( lpad(to_hex(n), 8, '0') || '-0000-0000-0000-' || lpad(to_hex(n), 12, '0') )::uuid,

  'sensor-' || n,

  CASE WHEN n > 490 THEN 'OFFLINE' ELSE 'ONLINE' END,

  CASE n % 5
    WHEN 1 THEN 'Assembly Line Temperature & Smoke Monitor'
    WHEN 2 THEN 'Cold Storage Environmental Sensor'
    WHEN 3 THEN 'Engine Room Multi-Sensor (High-Risk)'
    WHEN 4 THEN 'Server Room Climate Control'
    ELSE        'Packaging Area Air Quality Monitor'
  END,

  'Building ' || ((n - 1) / 5 + 1) || ' — ' ||
  CASE n % 5
    WHEN 1 THEN 'Assembly Line'
    WHEN 2 THEN 'Cold Storage'
    WHEN 3 THEN 'Engine Room'
    WHEN 4 THEN 'Server Room'
    ELSE        'Packaging Area'
  END,

  'a0000000-0000-0000-0000-000000000001'::uuid,
  'ACTIVE',

  CASE n % 3
    WHEN 0 THEN '2.3.1'
    WHEN 1 THEN '2.3.0'
    ELSE        '2.2.9'
  END,

  NOW() - ((30 + n / 50) || ' days')::interval,

  CASE
    WHEN n > 490 THEN NOW() - ((n % 6 + 1) || ' hours')::interval
    ELSE              NOW() - ((n % 15 + 1) || ' minutes')::interval
  END

FROM generate_series(1, 500) AS n
ON CONFLICT (id) DO UPDATE SET
  status           = EXCLUDED.status,
  last_seen        = EXCLUDED.last_seen,
  firmware_version = EXCLUDED.firmware_version;

-- ── 2. Telemetry (~1 000 000 rows) ───────────────────────────────────────────
-- Strategy: generate_series for devices × time, compute values in CTEs,
-- insert in one statement.

WITH
devices AS (
  SELECT
    n,
    ( lpad(to_hex(n), 8, '0') || '-0000-0000-0000-' || lpad(to_hex(n), 12, '0') )::uuid AS device_id,
    n % 5 AS profile
  FROM generate_series(1, 500) n
  -- exclude offline devices from history (they have no recent data)
  WHERE n <= 490
),
times AS (
  SELECT ts
  FROM generate_series(
    NOW() - INTERVAL '7 days',
    NOW() - INTERVAL '5 minutes',
    INTERVAL '5 minutes'
  ) ts
),
raw AS (
  SELECT
    d.device_id,
    d.profile,
    t.ts,

    -- Temperature (°C) — profile-specific base + daily cycle + noise + rare spike
    CASE d.profile
      WHEN 1 THEN
        68 + 8  * SIN(2*PI()*(EXTRACT(HOUR FROM t.ts)-6)/24)
           + (RANDOM()-0.5)*6
           + CASE WHEN RANDOM()<0.015 THEN RANDOM()*18+10 ELSE 0 END
      WHEN 2 THEN
        20 + 2  * SIN(2*PI()*(EXTRACT(HOUR FROM t.ts)-6)/24)
           + (RANDOM()-0.5)*4
      WHEN 3 THEN
        76 + 8  * SIN(2*PI()*(EXTRACT(HOUR FROM t.ts)-4)/24)
           + (RANDOM()-0.5)*8
           + CASE WHEN RANDOM()<0.025 THEN RANDOM()*22+8 ELSE 0 END
      WHEN 4 THEN
        22 + 3  * SIN(2*PI()*(EXTRACT(HOUR FROM t.ts)-8)/24)
           + (RANDOM()-0.5)*3
           + CASE WHEN RANDOM()<0.008 THEN RANDOM()*10 ELSE 0 END
      ELSE
        28 + 5  * SIN(2*PI()*(EXTRACT(HOUR FROM t.ts)-6)/24)
           + (RANDOM()-0.5)*6
    END AS raw_temp,

    -- Humidity (%RH)
    CASE d.profile
      WHEN 1 THEN 50 - 5  * SIN(2*PI()*(EXTRACT(HOUR FROM t.ts)-6)/24) + (RANDOM()-0.5)*10
      WHEN 2 THEN 65 + 5  * SIN(2*PI()*(EXTRACT(HOUR FROM t.ts)-3)/24) + (RANDOM()-0.5)*10
                   + CASE WHEN RANDOM()<0.01 THEN RANDOM()*32 ELSE 0 END
      WHEN 3 THEN 38 - 5  * SIN(2*PI()*(EXTRACT(HOUR FROM t.ts)-6)/24) + (RANDOM()-0.5)*10
      WHEN 4 THEN 45 + (RANDOM()-0.5)*8
      ELSE        60 + 5  * SIN(2*PI()*(EXTRACT(HOUR FROM t.ts)-3)/24) + (RANDOM()-0.5)*10
    END AS raw_hum,

    -- Motion (boolean)
    CASE
      WHEN d.profile IN (1, 0) THEN
        (EXTRACT(HOUR FROM t.ts) BETWEEN 7 AND 19
          AND EXTRACT(DOW FROM t.ts) BETWEEN 1 AND 5
          AND RANDOM() < 0.45)
        OR (RANDOM() < 0.05)
      WHEN d.profile = 4 THEN RANDOM() < 0.10
      ELSE                    RANDOM() < 0.07
    END AS motion,

    -- Smoke (ppm)
    CASE d.profile
      WHEN 1 THEN 15 + 5  * SIN(2*PI()*(EXTRACT(HOUR FROM t.ts)-6)/24) + RANDOM()*12
                   + CASE WHEN RANDOM()<0.008 THEN RANDOM()*240+30 ELSE 0 END
      WHEN 2 THEN 3  + RANDOM()*7
      WHEN 3 THEN 30 + 10 * SIN(2*PI()*(EXTRACT(HOUR FROM t.ts)-6)/24) + RANDOM()*20
                   + CASE WHEN RANDOM()<0.015 THEN RANDOM()*190+50 ELSE 0 END
      WHEN 4 THEN 2  + RANDOM()*3
      ELSE        8  + RANDOM()*12
    END AS raw_smoke

  FROM devices d CROSS JOIN times t
),
processed AS (
  SELECT
    device_id, ts, motion,
    GREATEST(-40.0, LEAST(200.0, ROUND(raw_temp::numeric,  1))) AS temp,
    GREATEST(  0.0, LEAST(100.0, ROUND(raw_hum::numeric,   1))) AS hum,
    GREATEST(  0.0,             ROUND(raw_smoke::numeric,  1))  AS smoke
  FROM raw
)
INSERT INTO telemetry
  (id, device_id, temperature, humidity, motion, smoke_ppm, timestamp, schema_version, readings)
SELECT
  gen_random_uuid(),
  device_id,
  temp, hum, motion, smoke, ts, 1,
  jsonb_build_object(
    'TEMPERATURE', jsonb_build_object('value', temp,  'unit', '°C',  'quality', 'GOOD'),
    'HUMIDITY',    jsonb_build_object('value', hum,   'unit', '%RH', 'quality', 'GOOD'),
    'SMOKE_PPM',   jsonb_build_object('value', smoke, 'unit', 'ppm', 'quality', 'GOOD'),
    'MOTION',      jsonb_build_object(
                     'value', CASE WHEN motion THEN 1.0 ELSE 0.0 END,
                     'unit', 'bool', 'quality', 'GOOD')
  )
FROM processed;

-- ── 3. Hourly aggregates ──────────────────────────────────────────────────────

INSERT INTO telemetry_hourly_aggregates
  (id, device_id, hour_bucket,
   temp_avg, temp_min, temp_max,
   hum_avg,  hum_min,  hum_max,
   smoke_avg, smoke_max,
   motion_count, sample_count)
SELECT
  gen_random_uuid(),
  device_id,
  date_trunc('hour', timestamp)                 AS hour_bucket,
  ROUND(AVG(temperature)::numeric, 2),
  MIN(temperature),
  MAX(temperature),
  ROUND(AVG(humidity)::numeric, 2),
  MIN(humidity),
  MAX(humidity),
  ROUND(AVG(smoke_ppm)::numeric, 2),
  MAX(smoke_ppm),
  COUNT(*) FILTER (WHERE motion = true),
  COUNT(*)
FROM telemetry
WHERE device_id IN (
  SELECT ( lpad(to_hex(n), 8, '0') || '-0000-0000-0000-' || lpad(to_hex(n), 12, '0') )::uuid
  FROM generate_series(1, 490) n
)
GROUP BY device_id, date_trunc('hour', timestamp)
ON CONFLICT ON CONSTRAINT uq_telemetry_hourly DO NOTHING;

-- ── 4. Sample alerts (~120 rows) ─────────────────────────────────────────────
-- Engine Room (profile 3 = n%5=3: 3,8,13,18,...) and Assembly Line (n%5=1)
-- have the most spikes, so most alerts come from those profiles.

INSERT INTO alerts (id, device_id, level, message, created_at, acknowledged, organization_id)
SELECT
  gen_random_uuid(),
  ( lpad(to_hex(n), 8, '0') || '-0000-0000-0000-' || lpad(to_hex(n), 12, '0') )::uuid,
  level,
  message,
  created_at,
  acknowledged,
  'a0000000-0000-0000-0000-000000000001'::uuid
FROM (

  -- Engine Room critical temp alerts — one per engine-room sensor, spread over 7 days
  SELECT
    n,
    'CRITICAL'                                                          AS level,
    'Temperature exceeded threshold: ' ||
      ROUND((RANDOM()*12 + 81)::numeric, 1) ||
      ' °C (threshold: 80.0 °C)'                                       AS message,
    NOW() - (RANDOM()*7 || ' days')::interval                          AS created_at,
    RANDOM() < 0.6                                                     AS acknowledged
  FROM generate_series(3, 498, 5) n   -- n%5=3 → engine room sensors

  UNION ALL

  -- Assembly Line smoke spike alerts
  SELECT
    n,
    'WARNING'                                                          AS level,
    'Smoke PPM exceeded threshold: ' ||
      ROUND((RANDOM()*150 + 201)::numeric, 1) ||
      ' ppm (threshold: 200.0 ppm)'                                    AS message,
    NOW() - (RANDOM()*7 || ' days')::interval                         AS created_at,
    RANDOM() < 0.7                                                     AS acknowledged
  FROM generate_series(1, 496, 5) n   -- n%5=1 → assembly line sensors, every other one
  WHERE n % 10 = 1                    -- ~50 sensors out of 100 assembly-line sensors

  UNION ALL

  -- Cold Storage humidity alerts
  SELECT
    n,
    'WARNING'                                                          AS level,
    'Humidity exceeded threshold: ' ||
      ROUND((RANDOM()*8 + 91)::numeric, 1) ||
      ' % (threshold: 90.0 %)'                                         AS message,
    NOW() - (RANDOM()*7 || ' days')::interval                         AS created_at,
    RANDOM() < 0.8                                                     AS acknowledged
  FROM generate_series(2, 497, 5) n   -- n%5=2 → cold storage sensors
  WHERE n % 20 = 2                    -- ~25 sensors

  UNION ALL

  -- Offline device alerts (devices 491–500)
  SELECT
    n,
    'WARNING'                                                          AS level,
    'Device offline: no heartbeat for more than ' ||
      (n % 6 + 1) || ' hour(s)'                                        AS message,
    NOW() - ((n % 6 + 1) || ' hours')::interval                       AS created_at,
    FALSE                                                              AS acknowledged
  FROM generate_series(491, 500) n

) alerts_src
-- Only insert alerts for devices that exist (n <= 500)
WHERE n BETWEEN 1 AND 500;

COMMIT;

\echo ''
\echo 'Demo seed complete.'
\echo '  Devices   : 500  (sensor-1 … sensor-500, buildings 1-100)'
\echo '  Telemetry : ~1 000 000 rows over 7 days'
\echo '  Alerts    : ~120 (engine-room critical, smoke, humidity, offline)'
\echo ''
\echo 'Dashboard → http://localhost:3000'
