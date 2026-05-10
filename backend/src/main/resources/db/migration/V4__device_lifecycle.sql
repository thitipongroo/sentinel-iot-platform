-- Device lifecycle management: operational state + firmware tracking.
-- lifecycleStatus is separate from the real-time connectivity status (ONLINE/OFFLINE).
ALTER TABLE devices
    ADD COLUMN IF NOT EXISTS lifecycle_status  VARCHAR(50)  NOT NULL DEFAULT 'PROVISIONED',
    ADD COLUMN IF NOT EXISTS firmware_version  VARCHAR(100),
    ADD COLUMN IF NOT EXISTS firmware_updated_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_devices_lifecycle ON devices(lifecycle_status);
