-- Enable pgcrypto for UUID generation
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- Users
CREATE TABLE IF NOT EXISTS app_users (
    id       UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role     VARCHAR(50)  NOT NULL
);

-- Devices
CREATE TABLE IF NOT EXISTS devices (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(255) NOT NULL UNIQUE,
    status      VARCHAR(50)  NOT NULL DEFAULT 'OFFLINE',
    description VARCHAR(500),
    location    VARCHAR(255),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    last_seen   TIMESTAMPTZ
);

-- Telemetry
CREATE TABLE IF NOT EXISTS telemetry (
    id          UUID             PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id   UUID             NOT NULL,
    temperature DOUBLE PRECISION NOT NULL,
    humidity    DOUBLE PRECISION NOT NULL,
    motion      BOOLEAN,
    smoke_ppm   DOUBLE PRECISION,
    timestamp   TIMESTAMPTZ      NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_telemetry_device_id ON telemetry(device_id);
CREATE INDEX IF NOT EXISTS idx_telemetry_timestamp  ON telemetry(timestamp DESC);

-- Alerts
CREATE TABLE IF NOT EXISTS alerts (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id    UUID        NOT NULL,
    level        VARCHAR(50) NOT NULL,
    message      TEXT        NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    acknowledged BOOLEAN     NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_alert_device_id  ON alerts(device_id);
CREATE INDEX IF NOT EXISTS idx_alert_created_at ON alerts(created_at DESC);

-- Refresh tokens
CREATE TABLE IF NOT EXISTS refresh_tokens (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    token      VARCHAR(512) NOT NULL UNIQUE,
    username   VARCHAR(255) NOT NULL,
    expires_at TIMESTAMPTZ  NOT NULL,
    revoked    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_refresh_token_token    ON refresh_tokens(token);
CREATE INDEX IF NOT EXISTS idx_refresh_token_username ON refresh_tokens(username);

-- Audit logs
CREATE TABLE IF NOT EXISTS audit_logs (
    id         UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    username   VARCHAR(255),
    action     VARCHAR(100)  NOT NULL,
    resource   VARCHAR(255),
    detail     VARCHAR(1000),
    ip_address VARCHAR(50),
    timestamp  TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_audit_log_username  ON audit_logs(username);
CREATE INDEX IF NOT EXISTS idx_audit_log_timestamp ON audit_logs(timestamp DESC);
