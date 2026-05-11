-- Row-Level Security (RLS) for multi-tenant isolation.
--
-- Strategy: PostgreSQL RLS policies filter every query by app.org_id, which is set
-- per-connection via a Hibernate interceptor (see TenantContext). This provides a
-- defence-in-depth layer on top of application-level org filtering: even if a bug in
-- the service layer omits an organizationId filter, the DB rejects cross-tenant reads.
--
-- Session variable set before each query: SET LOCAL app.org_id = '<uuid>';
-- (Hibernate EntityManagerFactory interceptor or JPA EntityListeners handle this.)
--
-- IMPORTANT: The app DB user must NOT be a superuser — superusers bypass RLS.
-- Create a least-privilege role: GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES
-- IN SCHEMA public TO sentinel_app_user;

-- ── devices ──────────────────────────────────────────────────────────────────

ALTER TABLE devices ENABLE ROW LEVEL SECURITY;
ALTER TABLE devices FORCE ROW LEVEL SECURITY;

-- Allow rows where organization_id matches the session variable
CREATE POLICY devices_tenant_isolation ON devices
    USING (organization_id = current_setting('app.org_id', true)::uuid);

-- ── alerts ───────────────────────────────────────────────────────────────────
-- Alerts are scoped by device ownership. We join via device to get the org.
-- For simplicity we add a denormalized organization_id to alerts for direct RLS.

ALTER TABLE alerts
    ADD COLUMN IF NOT EXISTS organization_id UUID REFERENCES organizations(id);

-- Backfill from the owning device (alerts created before this migration)
UPDATE alerts a
SET organization_id = d.organization_id
FROM devices d
WHERE a.device_id = d.id
  AND a.organization_id IS NULL;

ALTER TABLE alerts ENABLE ROW LEVEL SECURITY;
ALTER TABLE alerts FORCE ROW LEVEL SECURITY;

CREATE POLICY alerts_tenant_isolation ON alerts
    USING (organization_id = current_setting('app.org_id', true)::uuid);

CREATE INDEX IF NOT EXISTS idx_alerts_org_id ON alerts(organization_id);

-- ── audit_logs ───────────────────────────────────────────────────────────────
-- Audit logs are visible only to the tenant that owns them.

ALTER TABLE audit_logs
    ADD COLUMN IF NOT EXISTS organization_id UUID REFERENCES organizations(id);

UPDATE audit_logs SET organization_id = 'a0000000-0000-0000-0000-000000000001'
WHERE organization_id IS NULL;

ALTER TABLE audit_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_logs FORCE ROW LEVEL SECURITY;

CREATE POLICY audit_logs_tenant_isolation ON audit_logs
    USING (organization_id = current_setting('app.org_id', true)::uuid);

CREATE INDEX IF NOT EXISTS idx_audit_logs_org_id ON audit_logs(organization_id);
