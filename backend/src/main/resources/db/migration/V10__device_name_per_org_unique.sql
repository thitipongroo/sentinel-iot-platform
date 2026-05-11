-- Change device name uniqueness from global to per-organisation.
--
-- The original UNIQUE constraint on devices.name was a v1 assumption when
-- multi-tenancy did not exist. After V5 (multi-tenancy), DeviceService already
-- enforces per-org uniqueness in application code, but the DB constraint still
-- blocked two orgs from having a device with the same name.
--
-- New invariant: (organization_id, name) must be unique within an org.
--
-- NOTE: MQTT device resolution via KafkaTelemetryConsumer uses device name as
-- the lookup key (devices are addressed by the MQTT client-ID / topic which
-- matches device.name). If two orgs have a device with the same name the batch
-- lookup will return both rows, causing a Collectors.toMap duplicate-key error.
-- Until per-device MQTT authentication is implemented (mTLS or per-device
-- username/password), device names should remain globally unique in practice
-- even though the DB constraint now only enforces per-org uniqueness.

ALTER TABLE devices DROP CONSTRAINT IF EXISTS devices_name_key;

ALTER TABLE devices
    ADD CONSTRAINT uq_device_org_name UNIQUE (organization_id, name);
