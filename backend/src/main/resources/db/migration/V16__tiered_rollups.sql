-- ==============================================================================
-- Sentinel IoT Platform - Phase 3: Tiered Rollup Tables (Daily & Monthly)
-- ==============================================================================
-- Pre-computed daily and monthly aggregates for multi-year historical analytics.
-- Allows sub-5ms dashboard queries across 2+ year horizons without scanning raw rows.

-- ── 1. Daily Rollups ─────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS telemetry_daily_rollups (
    id              UUID             PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id       UUID             NOT NULL,
    organization_id UUID,
    day_bucket      TIMESTAMPTZ      NOT NULL,
    temp_avg        DOUBLE PRECISION NOT NULL,
    temp_min        DOUBLE PRECISION NOT NULL,
    temp_max        DOUBLE PRECISION NOT NULL,
    hum_avg         DOUBLE PRECISION NOT NULL,
    hum_min         DOUBLE PRECISION NOT NULL,
    hum_max         DOUBLE PRECISION NOT NULL,
    smoke_avg       DOUBLE PRECISION,
    smoke_max       DOUBLE PRECISION,
    motion_count    INTEGER          NOT NULL DEFAULT 0,
    sample_count    INTEGER          NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ      NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_telemetry_daily UNIQUE (device_id, day_bucket)
);

CREATE INDEX IF NOT EXISTS idx_telemetry_daily_device_bucket
    ON telemetry_daily_rollups(device_id, day_bucket DESC);

CREATE INDEX IF NOT EXISTS idx_telemetry_daily_org_bucket
    ON telemetry_daily_rollups(organization_id, day_bucket DESC);

-- ── 2. Monthly Rollups ───────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS telemetry_monthly_rollups (
    id              UUID             PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id       UUID             NOT NULL,
    organization_id UUID,
    month_bucket    TIMESTAMPTZ      NOT NULL,
    temp_avg        DOUBLE PRECISION NOT NULL,
    temp_min        DOUBLE PRECISION NOT NULL,
    temp_max        DOUBLE PRECISION NOT NULL,
    hum_avg         DOUBLE PRECISION NOT NULL,
    hum_min         DOUBLE PRECISION NOT NULL,
    hum_max         DOUBLE PRECISION NOT NULL,
    smoke_avg       DOUBLE PRECISION,
    smoke_max       DOUBLE PRECISION,
    motion_count    INTEGER          NOT NULL DEFAULT 0,
    sample_count    INTEGER          NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ      NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_telemetry_monthly UNIQUE (device_id, month_bucket)
);

CREATE INDEX IF NOT EXISTS idx_telemetry_monthly_device_bucket
    ON telemetry_monthly_rollups(device_id, month_bucket DESC);

CREATE INDEX IF NOT EXISTS idx_telemetry_monthly_org_bucket
    ON telemetry_monthly_rollups(organization_id, month_bucket DESC);

-- ── 3. Row-Level Security (Tenant Isolation) ─────────────────────────────────
ALTER TABLE telemetry_daily_rollups ENABLE ROW LEVEL SECURITY;
ALTER TABLE telemetry_monthly_rollups ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS tenant_isolation_policy_daily ON telemetry_daily_rollups;
CREATE POLICY tenant_isolation_policy_daily ON telemetry_daily_rollups
    FOR ALL
    USING (
        current_setting('app.current_tenant', true) IS NULL
        OR current_setting('app.current_tenant', true) = ''
        OR organization_id IS NULL
        OR organization_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid
    );

DROP POLICY IF EXISTS tenant_isolation_policy_monthly ON telemetry_monthly_rollups;
CREATE POLICY tenant_isolation_policy_monthly ON telemetry_monthly_rollups
    FOR ALL
    USING (
        current_setting('app.current_tenant', true) IS NULL
        OR current_setting('app.current_tenant', true) = ''
        OR organization_id IS NULL
        OR organization_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid
    );
