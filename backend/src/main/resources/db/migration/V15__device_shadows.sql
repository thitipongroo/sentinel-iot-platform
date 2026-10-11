-- ==============================================================================
-- V15: Device Twin / Shadow Architecture
--
-- Supports:
--   1. Desired vs Reported state synchronization
--   2. Optimistic Concurrency Control (OCC) versioning
--   3. Sub-millisecond delta computation & offline configuration buffering
--   4. Multi-tenant Row-Level Security (RLS)
-- ==============================================================================

CREATE TABLE IF NOT EXISTS device_shadows (
    device_id       UUID PRIMARY KEY REFERENCES devices(id) ON DELETE CASCADE,
    organization_id UUID NOT NULL REFERENCES organizations(id),
    version         BIGINT NOT NULL DEFAULT 1,
    desired_state   JSONB NOT NULL DEFAULT '{}'::jsonb,
    reported_state  JSONB NOT NULL DEFAULT '{}'::jsonb,
    metadata        JSONB NOT NULL DEFAULT '{}'::jsonb,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Indexes for performance and multi-tenant queries
CREATE INDEX IF NOT EXISTS idx_device_shadows_org ON device_shadows(organization_id);
CREATE INDEX IF NOT EXISTS idx_device_shadows_gin_desired ON device_shadows USING GIN (desired_state);
CREATE INDEX IF NOT EXISTS idx_device_shadows_gin_reported ON device_shadows USING GIN (reported_state);

-- Enable and Force Row-Level Security (RLS)
ALTER TABLE device_shadows ENABLE ROW LEVEL SECURITY;
ALTER TABLE device_shadows FORCE ROW LEVEL SECURITY;

-- Tenant isolation policy (scopes queries to tenant session variable app.org_id)
CREATE POLICY device_shadows_tenant_isolation ON device_shadows
    USING (organization_id = current_setting('app.org_id', true)::uuid);
