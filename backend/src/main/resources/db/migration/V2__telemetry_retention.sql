-- Hourly pre-aggregated telemetry for historical analytics.
-- Raw telemetry is retained for ${telemetry.retention-days} days (default 30),
-- then pruned by TelemetryRetentionService. This table keeps summaries indefinitely.
CREATE TABLE IF NOT EXISTS telemetry_hourly_aggregates (
    id           UUID             PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id    UUID             NOT NULL,
    hour_bucket  TIMESTAMPTZ      NOT NULL,
    temp_avg     DOUBLE PRECISION NOT NULL,
    temp_min     DOUBLE PRECISION NOT NULL,
    temp_max     DOUBLE PRECISION NOT NULL,
    hum_avg      DOUBLE PRECISION NOT NULL,
    hum_min      DOUBLE PRECISION NOT NULL,
    hum_max      DOUBLE PRECISION NOT NULL,
    smoke_avg    DOUBLE PRECISION,
    smoke_max    DOUBLE PRECISION,
    motion_count INTEGER          NOT NULL DEFAULT 0,
    sample_count INTEGER          NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ      NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_telemetry_hourly UNIQUE (device_id, hour_bucket)
);

CREATE INDEX IF NOT EXISTS idx_telemetry_hourly_device_hour
    ON telemetry_hourly_aggregates(device_id, hour_bucket DESC);
