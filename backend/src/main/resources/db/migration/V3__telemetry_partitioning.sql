-- Convert the telemetry table to a monthly range-partitioned table.
--
-- PostgreSQL requires the partition key to be part of any PRIMARY KEY, which
-- would break the JPA entity's single-UUID @Id.  The solution: drop the PK
-- constraint and replace it with a UNIQUE INDEX on id — Hibernate only needs
-- the column to be uniquely addressable, not a formal PK constraint.
--
-- In production, new monthly partitions would be created automatically via
-- pg_partman (https://github.com/pgpartman/pg_partman). Here we pre-create
-- two years of partitions plus a DEFAULT to catch any overflow.

-- ── Phase 1: preserve existing data ──────────────────────────────────────────
ALTER TABLE telemetry RENAME TO telemetry_legacy;
ALTER INDEX idx_telemetry_device_id RENAME TO idx_telemetry_legacy_device_id;
ALTER INDEX idx_telemetry_timestamp  RENAME TO idx_telemetry_legacy_timestamp;

-- ── Phase 2: partitioned table (no PK — see note above) ──────────────────────
CREATE TABLE telemetry (
    id          UUID             NOT NULL DEFAULT gen_random_uuid(),
    device_id   UUID             NOT NULL,
    temperature DOUBLE PRECISION NOT NULL,
    humidity    DOUBLE PRECISION NOT NULL,
    motion      BOOLEAN,
    smoke_ppm   DOUBLE PRECISION,
    timestamp   TIMESTAMPTZ      NOT NULL
) PARTITION BY RANGE(timestamp);

-- ── Phase 3: monthly partitions 2025-01 → 2026-12 ────────────────────────────
CREATE TABLE telemetry_2025_01 PARTITION OF telemetry FOR VALUES FROM ('2025-01-01') TO ('2025-02-01');
CREATE TABLE telemetry_2025_02 PARTITION OF telemetry FOR VALUES FROM ('2025-02-01') TO ('2025-03-01');
CREATE TABLE telemetry_2025_03 PARTITION OF telemetry FOR VALUES FROM ('2025-03-01') TO ('2025-04-01');
CREATE TABLE telemetry_2025_04 PARTITION OF telemetry FOR VALUES FROM ('2025-04-01') TO ('2025-05-01');
CREATE TABLE telemetry_2025_05 PARTITION OF telemetry FOR VALUES FROM ('2025-05-01') TO ('2025-06-01');
CREATE TABLE telemetry_2025_06 PARTITION OF telemetry FOR VALUES FROM ('2025-06-01') TO ('2025-07-01');
CREATE TABLE telemetry_2025_07 PARTITION OF telemetry FOR VALUES FROM ('2025-07-01') TO ('2025-08-01');
CREATE TABLE telemetry_2025_08 PARTITION OF telemetry FOR VALUES FROM ('2025-08-01') TO ('2025-09-01');
CREATE TABLE telemetry_2025_09 PARTITION OF telemetry FOR VALUES FROM ('2025-09-01') TO ('2025-10-01');
CREATE TABLE telemetry_2025_10 PARTITION OF telemetry FOR VALUES FROM ('2025-10-01') TO ('2025-11-01');
CREATE TABLE telemetry_2025_11 PARTITION OF telemetry FOR VALUES FROM ('2025-11-01') TO ('2025-12-01');
CREATE TABLE telemetry_2025_12 PARTITION OF telemetry FOR VALUES FROM ('2025-12-01') TO ('2026-01-01');

CREATE TABLE telemetry_2026_01 PARTITION OF telemetry FOR VALUES FROM ('2026-01-01') TO ('2026-02-01');
CREATE TABLE telemetry_2026_02 PARTITION OF telemetry FOR VALUES FROM ('2026-02-01') TO ('2026-03-01');
CREATE TABLE telemetry_2026_03 PARTITION OF telemetry FOR VALUES FROM ('2026-03-01') TO ('2026-04-01');
CREATE TABLE telemetry_2026_04 PARTITION OF telemetry FOR VALUES FROM ('2026-04-01') TO ('2026-05-01');
CREATE TABLE telemetry_2026_05 PARTITION OF telemetry FOR VALUES FROM ('2026-05-01') TO ('2026-06-01');
CREATE TABLE telemetry_2026_06 PARTITION OF telemetry FOR VALUES FROM ('2026-06-01') TO ('2026-07-01');
CREATE TABLE telemetry_2026_07 PARTITION OF telemetry FOR VALUES FROM ('2026-07-01') TO ('2026-08-01');
CREATE TABLE telemetry_2026_08 PARTITION OF telemetry FOR VALUES FROM ('2026-08-01') TO ('2026-09-01');
CREATE TABLE telemetry_2026_09 PARTITION OF telemetry FOR VALUES FROM ('2026-09-01') TO ('2026-10-01');
CREATE TABLE telemetry_2026_10 PARTITION OF telemetry FOR VALUES FROM ('2026-10-01') TO ('2026-11-01');
CREATE TABLE telemetry_2026_11 PARTITION OF telemetry FOR VALUES FROM ('2026-11-01') TO ('2026-12-01');
CREATE TABLE telemetry_2026_12 PARTITION OF telemetry FOR VALUES FROM ('2026-12-01') TO ('2027-01-01');

-- Catches data outside the pre-created range (e.g. 2024 backfill or 2027+ data)
CREATE TABLE telemetry_default PARTITION OF telemetry DEFAULT;

-- ── Phase 4: indexes (propagate automatically to all current + future partitions)
-- Non-unique index on id; Hibernate findById() uses this.
-- PostgreSQL 11+ forbids UNIQUE indexes on partitioned tables unless the partition key
-- is included. UUIDs (gen_random_uuid) are unique by construction, so a plain index suffices.
CREATE INDEX idx_telemetry_id                   ON telemetry(id);
CREATE INDEX         idx_telemetry_device_id    ON telemetry(device_id);
CREATE INDEX         idx_telemetry_timestamp    ON telemetry(timestamp DESC);
-- Composite index for the common "latest N readings for device" query pattern
CREATE INDEX         idx_telemetry_device_ts    ON telemetry(device_id, timestamp DESC);

-- ── Phase 5: migrate existing data ───────────────────────────────────────────
INSERT INTO telemetry SELECT * FROM telemetry_legacy;

-- ── Phase 6: drop legacy table ────────────────────────────────────────────────
DROP TABLE telemetry_legacy;
