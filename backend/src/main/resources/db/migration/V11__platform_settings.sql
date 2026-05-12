CREATE TABLE platform_settings (
    organization_id          UUID             PRIMARY KEY,
    temperature_threshold    DOUBLE PRECISION NOT NULL DEFAULT 80.0,
    humidity_threshold       DOUBLE PRECISION NOT NULL DEFAULT 90.0,
    smoke_threshold          DOUBLE PRECISION NOT NULL DEFAULT 200.0,
    telemetry_retention_days INT              NOT NULL DEFAULT 30,
    audit_retention_days     INT              NOT NULL DEFAULT 90,
    slack_enabled            BOOLEAN          NOT NULL DEFAULT false,
    line_enabled             BOOLEAN          NOT NULL DEFAULT false,
    webhook_enabled          BOOLEAN          NOT NULL DEFAULT false,
    updated_at               TIMESTAMPTZ               DEFAULT now(),
    updated_by               VARCHAR(100)
);
