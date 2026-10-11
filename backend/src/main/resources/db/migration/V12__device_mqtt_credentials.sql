-- V12: Add MQTT credentials to devices table for dynamic authentication and provisioning
ALTER TABLE devices
    ADD COLUMN IF NOT EXISTS mqtt_username VARCHAR(128),
    ADD COLUMN IF NOT EXISTS mqtt_password_hash VARCHAR(128);

CREATE INDEX IF NOT EXISTS idx_devices_mqtt_username ON devices(mqtt_username);
