-- Schema evolution: telemetry versioning, dynamic sensor readings, edge metadata,
-- and device sensor capability declarations.
--
-- Design decisions:
--   • schema_version INT DEFAULT 1  — distinguishes legacy v1 payloads from v2+.
--     Consumers must treat unknown versions as v2 to stay forward-compatible.
--
--   • readings JSONB  — arbitrary sensor map keyed by SensorType name (e.g.
--     "TEMPERATURE", "CO2_PPM", "CUSTOM_TANK_PRESSURE"). GIN index enables
--     querying for specific sensor keys without a table scan.
--     Example: {"TEMPERATURE":{"value":25.5,"unit":"°C","quality":"GOOD"}, ...}
--
--   • edge_* columns  — scalar columns (not JSONB) for edge diagnostics because
--     they are used in WHERE/ORDER BY clauses (battery monitoring, RSSI trending).
--     Adding them as scalars avoids JSONB extraction overhead on hot queries.
--
--   • capabilities JSONB on devices  — stores the SensorCapability map keyed by
--     SensorType name. The alert engine reads this once per device resolution batch;
--     it is not queried for individual sensor values, so JSONB is appropriate.

-- ── Telemetry: schema version ─────────────────────────────────────────────────

ALTER TABLE telemetry
    ADD COLUMN IF NOT EXISTS schema_version INT NOT NULL DEFAULT 1;

-- ── Telemetry: dynamic sensor readings (JSONB) ────────────────────────────────

ALTER TABLE telemetry
    ADD COLUMN IF NOT EXISTS readings JSONB;

-- GIN index for JSONB containment queries, e.g.:
--   SELECT * FROM telemetry WHERE readings @> '{"CO2_PPM": {"quality": "BAD"}}';
CREATE INDEX IF NOT EXISTS idx_telemetry_readings
    ON telemetry USING gin (readings)
    WHERE readings IS NOT NULL;

-- ── Telemetry: edge node diagnostics (scalar columns) ─────────────────────────

ALTER TABLE telemetry
    ADD COLUMN IF NOT EXISTS edge_firmware_version VARCHAR(50),
    ADD COLUMN IF NOT EXISTS edge_ip               VARCHAR(50),
    ADD COLUMN IF NOT EXISTS edge_uptime_seconds   BIGINT,
    ADD COLUMN IF NOT EXISTS edge_rssi             INT,
    ADD COLUMN IF NOT EXISTS edge_snr              INT,
    ADD COLUMN IF NOT EXISTS edge_battery_voltage  DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS edge_battery_pct      INT,
    ADD COLUMN IF NOT EXISTS edge_free_heap_bytes  INT,
    ADD COLUMN IF NOT EXISTS edge_protocol         VARCHAR(20);

-- Index for battery-level monitoring queries across all devices:
--   SELECT device_id, AVG(edge_battery_pct) FROM telemetry
--   WHERE timestamp > NOW() - INTERVAL '1 hour' GROUP BY device_id;
CREATE INDEX IF NOT EXISTS idx_telemetry_battery_pct
    ON telemetry (device_id, timestamp DESC, edge_battery_pct)
    WHERE edge_battery_pct IS NOT NULL;

-- Index for RSSI trending / connectivity analysis:
CREATE INDEX IF NOT EXISTS idx_telemetry_rssi
    ON telemetry (device_id, timestamp DESC, edge_rssi)
    WHERE edge_rssi IS NOT NULL;

-- ── Devices: sensor capability declarations (JSONB) ───────────────────────────

ALTER TABLE devices
    ADD COLUMN IF NOT EXISTS capabilities JSONB;

-- Partial GIN index — only devices that have declared capabilities.
-- Used when the alert engine checks whether a device has per-sensor thresholds.
CREATE INDEX IF NOT EXISTS idx_devices_capabilities
    ON devices USING gin (capabilities)
    WHERE capabilities IS NOT NULL;

-- ── Backfill: synthesize readings for existing v1 telemetry ──────────────────
-- This is idempotent; rows that already have readings are left unchanged.
-- Only updates rows where the fixed-field values are present (NOT NULL check on
-- temperature which is the required v1 field).
UPDATE telemetry
SET readings = jsonb_build_object(
    'TEMPERATURE', jsonb_build_object('value', temperature, 'unit', '°C',    'quality', 'GOOD'),
    'HUMIDITY',    jsonb_build_object('value', humidity,    'unit', '%RH',   'quality', 'GOOD'),
    'SMOKE_PPM',   jsonb_build_object('value', smoke_ppm,   'unit', 'ppm',   'quality', 'GOOD'),
    'MOTION',      jsonb_build_object('value', CASE WHEN motion THEN 1.0 ELSE 0.0 END,
                                       'unit', 'boolean', 'quality', 'GOOD')
)
WHERE readings IS NULL
  AND temperature IS NOT NULL;
