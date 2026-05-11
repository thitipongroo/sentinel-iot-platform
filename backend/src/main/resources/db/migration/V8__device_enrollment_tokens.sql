-- Device enrollment tokens — secure bootstrap for new IoT devices.
--
-- Flow:
--   1. ADMIN calls POST /api/v1/devices/{id}/enrollment-token
--      → generates a one-time token (expires in 24h)
--   2. Token is delivered to the physical device out-of-band (QR code, NFC, provisioning portal)
--   3. Device calls POST /api/v1/devices/enroll { token, deviceId, publicKey (optional) }
--      → token is validated and consumed (single-use)
--      → device transitions PROVISIONED → ACTIVE
--      → MQTT per-device credentials are returned (or mTLS cert if PKI is enabled)
--   4. Token is marked used — replaying the token returns HTTP 410 Gone
--
-- Security properties:
--   • Tokens are cryptographically random (256-bit, URL-safe Base64)
--   • Single-use — consumed on first successful enrollment
--   • Short TTL — expire after 24 hours by default
--   • Bound to a specific device ID — cannot be used for a different device
--   • Audit-logged — enrollment creates an audit_logs entry

CREATE TABLE IF NOT EXISTS device_enrollment_tokens (
    id              UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id       UUID          NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    organization_id UUID          NOT NULL REFERENCES organizations(id),
    token_hash      VARCHAR(128)  NOT NULL UNIQUE,     -- SHA-256 of the raw token (never store raw)
    expires_at      TIMESTAMPTZ   NOT NULL,
    used_at         TIMESTAMPTZ,
    used_by_ip      VARCHAR(64),
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by      VARCHAR(255)  NOT NULL              -- username of the ADMIN who generated it
);

CREATE INDEX IF NOT EXISTS idx_enrollment_tokens_device_id  ON device_enrollment_tokens(device_id);
CREATE INDEX IF NOT EXISTS idx_enrollment_tokens_expires_at ON device_enrollment_tokens(expires_at);
CREATE INDEX IF NOT EXISTS idx_enrollment_tokens_org_id     ON device_enrollment_tokens(organization_id);
