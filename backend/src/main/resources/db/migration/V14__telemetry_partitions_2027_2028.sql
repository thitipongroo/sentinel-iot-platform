-- V14: Extend Telemetry Partition Range through 2027-2028
-- Extends monthly range partitions for 2027-01 to 2028-12 to prevent
-- telemetry landing in telemetry_default, maintaining partition pruning performance.

DO $$
BEGIN
    -- 1. Detach default partition if currently attached
    IF EXISTS (
        SELECT 1 FROM pg_inherits
        JOIN pg_class parent ON pg_inherits.inhparent = parent.oid
        JOIN pg_class child  ON pg_inherits.inhrelid = child.oid
        WHERE parent.relname = 'telemetry' AND child.relname = 'telemetry_default'
    ) THEN
        ALTER TABLE telemetry DETACH PARTITION telemetry_default;
    END IF;
END $$;

-- ── 2027 monthly partitions ──────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS telemetry_2027_01 PARTITION OF telemetry FOR VALUES FROM ('2027-01-01') TO ('2027-02-01');
CREATE TABLE IF NOT EXISTS telemetry_2027_02 PARTITION OF telemetry FOR VALUES FROM ('2027-02-01') TO ('2027-03-01');
CREATE TABLE IF NOT EXISTS telemetry_2027_03 PARTITION OF telemetry FOR VALUES FROM ('2027-03-01') TO ('2027-04-01');
CREATE TABLE IF NOT EXISTS telemetry_2027_04 PARTITION OF telemetry FOR VALUES FROM ('2027-04-01') TO ('2027-05-01');
CREATE TABLE IF NOT EXISTS telemetry_2027_05 PARTITION OF telemetry FOR VALUES FROM ('2027-05-01') TO ('2027-06-01');
CREATE TABLE IF NOT EXISTS telemetry_2027_06 PARTITION OF telemetry FOR VALUES FROM ('2027-06-01') TO ('2027-07-01');
CREATE TABLE IF NOT EXISTS telemetry_2027_07 PARTITION OF telemetry FOR VALUES FROM ('2027-07-01') TO ('2027-08-01');
CREATE TABLE IF NOT EXISTS telemetry_2027_08 PARTITION OF telemetry FOR VALUES FROM ('2027-08-01') TO ('2027-09-01');
CREATE TABLE IF NOT EXISTS telemetry_2027_09 PARTITION OF telemetry FOR VALUES FROM ('2027-09-01') TO ('2027-10-01');
CREATE TABLE IF NOT EXISTS telemetry_2027_10 PARTITION OF telemetry FOR VALUES FROM ('2027-10-01') TO ('2027-11-01');
CREATE TABLE IF NOT EXISTS telemetry_2027_11 PARTITION OF telemetry FOR VALUES FROM ('2027-11-01') TO ('2027-12-01');
CREATE TABLE IF NOT EXISTS telemetry_2027_12 PARTITION OF telemetry FOR VALUES FROM ('2027-12-01') TO ('2028-01-01');

-- ── 2028 monthly partitions ──────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS telemetry_2028_01 PARTITION OF telemetry FOR VALUES FROM ('2028-01-01') TO ('2028-02-01');
CREATE TABLE IF NOT EXISTS telemetry_2028_02 PARTITION OF telemetry FOR VALUES FROM ('2028-02-01') TO ('2028-03-01');
CREATE TABLE IF NOT EXISTS telemetry_2028_03 PARTITION OF telemetry FOR VALUES FROM ('2028-03-01') TO ('2028-04-01');
CREATE TABLE IF NOT EXISTS telemetry_2028_04 PARTITION OF telemetry FOR VALUES FROM ('2028-04-01') TO ('2028-05-01');
CREATE TABLE IF NOT EXISTS telemetry_2028_05 PARTITION OF telemetry FOR VALUES FROM ('2028-05-01') TO ('2028-06-01');
CREATE TABLE IF NOT EXISTS telemetry_2028_06 PARTITION OF telemetry FOR VALUES FROM ('2028-06-01') TO ('2028-07-01');
CREATE TABLE IF NOT EXISTS telemetry_2028_07 PARTITION OF telemetry FOR VALUES FROM ('2028-07-01') TO ('2028-08-01');
CREATE TABLE IF NOT EXISTS telemetry_2028_08 PARTITION OF telemetry FOR VALUES FROM ('2028-08-01') TO ('2028-09-01');
CREATE TABLE IF NOT EXISTS telemetry_2028_09 PARTITION OF telemetry FOR VALUES FROM ('2028-09-01') TO ('2028-10-01');
CREATE TABLE IF NOT EXISTS telemetry_2028_10 PARTITION OF telemetry FOR VALUES FROM ('2028-10-01') TO ('2028-11-01');
CREATE TABLE IF NOT EXISTS telemetry_2028_11 PARTITION OF telemetry FOR VALUES FROM ('2028-11-01') TO ('2028-12-01');
CREATE TABLE IF NOT EXISTS telemetry_2028_12 PARTITION OF telemetry FOR VALUES FROM ('2028-12-01') TO ('2029-01-01');

-- ── 2. Migrate existing data from detached default partition if any and re-attach ──
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_class WHERE relname = 'telemetry_default') THEN
        INSERT INTO telemetry
        SELECT * FROM telemetry_default
        WHERE timestamp >= '2027-01-01' AND timestamp < '2029-01-01';

        DELETE FROM telemetry_default
        WHERE timestamp >= '2027-01-01' AND timestamp < '2029-01-01';

        ALTER TABLE telemetry ATTACH PARTITION telemetry_default DEFAULT;
    ELSE
        CREATE TABLE telemetry_default PARTITION OF telemetry DEFAULT;
    END IF;
END $$;
