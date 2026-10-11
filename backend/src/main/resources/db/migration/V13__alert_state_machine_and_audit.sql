-- V13: Alert State Machine & Audit Indexing
-- Adds state machine lifecycle columns to alerts table and indexes for enterprise audit trail

ALTER TABLE alerts ADD COLUMN IF NOT EXISTS state VARCHAR(20) NOT NULL DEFAULT 'FIRING';
ALTER TABLE alerts ADD COLUMN IF NOT EXISTS acknowledged_at TIMESTAMPTZ;
ALTER TABLE alerts ADD COLUMN IF NOT EXISTS acknowledged_by VARCHAR(100);
ALTER TABLE alerts ADD COLUMN IF NOT EXISTS resolved_at TIMESTAMPTZ;
ALTER TABLE alerts ADD COLUMN IF NOT EXISTS resolved_by VARCHAR(100);
ALTER TABLE alerts ADD COLUMN IF NOT EXISTS resolution_notes TEXT;
ALTER TABLE alerts ADD COLUMN IF NOT EXISTS silenced_until TIMESTAMPTZ;
ALTER TABLE alerts ADD COLUMN IF NOT EXISTS silenced_by VARCHAR(100);

-- Migrate existing rows: if acknowledged = true, set state to ACKNOWLEDGED
UPDATE alerts SET state = 'ACKNOWLEDGED' WHERE acknowledged = true AND state = 'FIRING';

CREATE INDEX IF NOT EXISTS idx_alerts_state ON alerts (state);
CREATE INDEX IF NOT EXISTS idx_alerts_org_state ON alerts (organization_id, state);
CREATE INDEX IF NOT EXISTS idx_alerts_device_state ON alerts (device_id, state);

-- Audit log query performance indexes
CREATE INDEX IF NOT EXISTS idx_audit_log_action ON audit_logs (action);
CREATE INDEX IF NOT EXISTS idx_audit_log_org_ts ON audit_logs (organization_id, timestamp DESC);
