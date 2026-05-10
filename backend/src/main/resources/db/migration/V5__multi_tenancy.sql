-- Multi-tenancy foundation: organizations table + tenant FK on users and devices.
--
-- Isolation model: application-level row filtering via TenantContext (ThreadLocal).
-- Every API request carries an orgId in the JWT; DeviceService scopes all queries
-- to that orgId. Telemetry and alerts are indirectly isolated through device ownership.
--
-- Device names remain globally unique (single MQTT topic factory/telemetry — devices
-- are resolved by name. Per-org uniqueness is deferred until per-device MQTT auth
-- (mTLS or username/password per device) is implemented.)
--
-- Upgrade path to PostgreSQL RLS: add ENABLE ROW LEVEL SECURITY on devices + alerts,
-- create a policy using current_setting('app.org_id'), and set it before each query
-- via a Hibernate interceptor or JPA EntityListeners.

CREATE TABLE IF NOT EXISTS organizations (
    id          UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    slug        VARCHAR(100)  NOT NULL UNIQUE,
    name        VARCHAR(255)  NOT NULL,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_organizations_slug ON organizations(slug);

-- Seed a default organization so existing users and devices can be migrated.
-- DataInitializer uses this slug to look up the org when seeding users.
INSERT INTO organizations (id, slug, name)
VALUES ('a0000000-0000-0000-0000-000000000001', 'default', 'Default Organization')
ON CONFLICT (slug) DO NOTHING;

-- ── app_users ────────────────────────────────────────────────────────────────
ALTER TABLE app_users
    ADD COLUMN IF NOT EXISTS organization_id UUID REFERENCES organizations(id);

CREATE INDEX IF NOT EXISTS idx_users_org_id ON app_users(organization_id);

-- Assign any pre-existing users to the default org (idempotent on re-run)
UPDATE app_users SET organization_id = 'a0000000-0000-0000-0000-000000000001'
WHERE organization_id IS NULL;

ALTER TABLE app_users ALTER COLUMN organization_id SET NOT NULL;

-- ── devices ──────────────────────────────────────────────────────────────────
ALTER TABLE devices
    ADD COLUMN IF NOT EXISTS organization_id UUID REFERENCES organizations(id);

CREATE INDEX IF NOT EXISTS idx_devices_org_id ON devices(organization_id);

UPDATE devices SET organization_id = 'a0000000-0000-0000-0000-000000000001'
WHERE organization_id IS NULL;

ALTER TABLE devices ALTER COLUMN organization_id SET NOT NULL;
