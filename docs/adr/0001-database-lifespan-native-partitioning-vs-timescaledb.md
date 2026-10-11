# ADR-0001: Telemetry Database Lifespan: PostgreSQL Native Range Partitioning vs. TimescaleDB

- **Status:** Accepted & Implemented
- **Date:** 2026-10-11
- **Deciders:** Sentinel IoT Core Architecture Team
- **Consulted:** DevOps, Data Platform, Security
- **Informed:** Backend Engineers, Site Reliability Engineers

---

## 1. Context and Problem Statement

The Sentinel IoT platform ingests high-frequency telemetry from hundreds of industrial devices. The primary table `telemetry` was originally partitioned monthly using declarative range partitioning up to `2026-12` (migration `V3__telemetry_partitioning.sql`).

As the platform approached the 2027 boundary, unpartitioned telemetry would automatically fall into `telemetry_default`. Storing unpartitioned rows in a single default partition causes:
1. Loss of partition pruning in query plans, degrading p95 latency on dashboard queries.
2. Inability to detach and drop monthly tables in $O(1)$ constant time without heavy `DELETE` table scans.
3. Severe locking issues when attempting to attach new partitions retroactively while `telemetry_default` contains matching data.

The team needed to decide between two strategic architectural options:
- **Option A:** Continue with PostgreSQL Native Range Partitioning, extended with Flyway migrations and an automated background provisioning service.
- **Option B:** Migrate the telemetry storage engine to TimescaleDB (hypertable auto-chunking).

---

## 2. Decision Drivers

- **Managed Cloud Compatibility:** Must run on AWS RDS PostgreSQL / Aurora PostgreSQL Multi-AZ without custom infrastructure maintenance.
- **Kubernetes Native (EKS):** Must remain compatible with CloudNativePG (CNPG) operator and standard container images (`postgres:16-alpine`).
- **Data Lifecycle Alignment:** Raw telemetry is subject to a 30-day retention window (`TELEMETRY_RETENTION_DAYS`), after which it is summarized into `telemetry_hourly_aggregates` and raw partitions are pruned.
- **Zero-Downtime Operations:** The solution must not require database restarts, proprietary extensions, or license risks.
- **Operational Simplicity:** Avoid manual intervention before every new calendar year.

---

## 3. Considered Options

### Option A: PostgreSQL Native Range Partitioning + Dynamic Auto-Provisioning (Selected)
- Use standard PostgreSQL declarative range partitioning (`PARTITION BY RANGE (timestamp)`).
- Provide static runway through 2028-12 via Flyway migration `V14__telemetry_partitions_2027_2028.sql`.
- Add a scheduled proactive partition worker in `TelemetryRetentionService.ensureUpcomingPartitions()` that creates monthly partitions 6 months in advance.
- Coordinate execution across multiple horizontal replicas using a Redis distributed lock (`lock:telemetry:partition-provision`).

### Option B: TimescaleDB Hypertables
- Replace standard PostgreSQL with TimescaleDB.
- Convert `telemetry` to a hypertable (`SELECT create_hypertable('telemetry', 'timestamp', chunk_time_interval => INTERVAL '1 month')`).
- Rely on TimescaleDB engine to generate chunks on the fly and handle data retention.

---

## 4. Evaluation & Tradeoff Matrix

| Criterion | Option A: Native Range Partitioning + Auto-Worker | Option B: TimescaleDB |
|---|---|---|
| **AWS RDS / Aurora** | **100% Native & Supported**. Works on any standard RDS/Aurora instance. | **Not Available**. AWS RDS does not support TimescaleDB due to Timescale License restrictions. Requires self-hosted EC2. |
| **Kubernetes (CNPG)** | **Zero extra overhead**. Runs on standard official Postgres images. | **High maintenance**. Requires maintaining custom container images with Timescale C-extensions. |
| **Local CI / Testcontainers** | **Seamless**. Compatible with standard `postgres:16-alpine`. | **Friction**. All integration tests and CI runners must swap images to `timescale/timescaledb`. |
| **Retention Policy** | Detaches & drops child tables in $O(1)$ time via native retention job. | Uses TimescaleDB `drop_chunks()` policy. |
| **Licensing** | PostgreSQL License (Open Source / Permissive). | Timescale License (TSL) with non-compete clauses for cloud hosting. |
| **Lifecycle Fit** | Perfectly matches 30-day raw retention + hourly rollup model (<50M active rows). | Overkill for 30-day retention; designed for multi-billion persistent timeseries. |
| **Maintenance Burden** | Fully automated via `ensureUpcomingPartitions(6)` + Flyway runway. | Automated chunking, but high operational maintenance on upgrades and extensions. |

---

## 5. Decision Outcome

**Chosen: Option A — PostgreSQL Native Range Partitioning with Dynamic Auto-Provisioning.**

### Implementation Details:
1. **Migration Runway:** Created [`V14__telemetry_partitions_2027_2028.sql`](file:///Users/thitipongroo/workspace/github/sentinel-iot-platform/backend/src/main/resources/db/migration/V14__telemetry_partitions_2027_2028.sql) to pre-create all monthly tables from `telemetry_2027_01` through `telemetry_2028_12`, safely detaching and re-attaching `telemetry_default`.
2. **Proactive Background Provisioning:** Added `ensureUpcomingPartitions(int monthsAhead)` in [`TelemetryRetentionService.java`](file:///Users/thitipongroo/workspace/github/sentinel-iot-platform/backend/src/main/java/com/sentinel/iot/service/TelemetryRetentionService.java) running on the 1st of every month at 03:00 UTC. It checks `pg_inherits` and provisions upcoming partitions 6 months in advance.
3. **Multi-Replica Mutual Exclusion:** Protected both retention purging and partition creation using Redis distributed locks (`SET lock:telemetry:... NX EX 7200`) so only a single pod executes DDL operations on the cluster.

### Positive Consequences:
- 100% compatibility with AWS RDS Aurora Multi-AZ and Kubernetes CloudNativePG.
- Zero licensing ambiguity or vendor lock-in.
- Lifespan is fully automated: no human intervention required for future years (2029+).
- Constant time $O(1)$ partition drops for pruned months outside the 30-day retention window.

### Negative Consequences / Mitigations:
- *PostgreSQL DDL lock during partition creation:* Mitigated because partition creation runs 6 months before any queries or writes touch that range, taking < 5ms with zero data lock contention.

---

## 6. References
- [Telemetry Retention Architecture](file:///Users/thitipongroo/workspace/github/sentinel-iot-platform/docs/system-design/telemetry-retention.md)
- [Design Tradeoffs Document](file:///Users/thitipongroo/workspace/github/sentinel-iot-platform/docs/system-design/tradeoffs.md)
- Flyway Migration: [`V14__telemetry_partitions_2027_2028.sql`](file:///Users/thitipongroo/workspace/github/sentinel-iot-platform/backend/src/main/resources/db/migration/V14__telemetry_partitions_2027_2028.sql)
