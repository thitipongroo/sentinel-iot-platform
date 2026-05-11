-- Allow temperature and humidity to be NULL in the telemetry table.
--
-- v2 payloads (schemaVersion >= 2) may carry only custom sensors via the
-- readings JSONB map and have no top-level temperature/humidity values.
-- Keeping these columns NOT NULL blocked v2-only device types from being
-- ingested at all.
--
-- For partitioned tables in PostgreSQL, ALTER TABLE on the parent propagates
-- the constraint change to all existing child partitions automatically.

ALTER TABLE telemetry ALTER COLUMN temperature DROP NOT NULL;
ALTER TABLE telemetry ALTER COLUMN humidity    DROP NOT NULL;
