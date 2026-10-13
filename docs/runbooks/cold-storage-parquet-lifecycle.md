# Runbook: DuckDB Lakehouse & Cold Storage Parquet Lifecycle Operations

**Service:** Sentinel IoT Platform — Lakehouse Analytics & Cold Storage (RFC-001 Phase 3)  
**Components:** `TelemetryArchivalService`, `LakehouseAnalyticsService`, `TelemetryRetentionService`, DuckDB  
**Primary Metrics:** `jvm.memory.used`, `disk.free.bytes`, `sentinel.retention.duration.seconds`, DuckDB query response time

---

## 1. Overview & Architecture

The Sentinel IoT Platform implements a two-tier storage model to balance sub-millisecond operational write latency with low-cost multi-year historical analytics:

```
┌────────────────────────────────────────────────────────────────────────┐
│                        Hot Storage (< 30 days)                         │
│  PostgreSQL 16 partitioned by month (telemetry_yYYYYmMM)               │
│  Redis 7 for real-time telemetry cache and dynamic baselines           │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                         Daily Archival (02:30 UTC)
                                    │
┌───────────────────────────────────▼────────────────────────────────────┐
│                        Cold Storage (> 30 days)                        │
│  Hive-partitioned Apache Parquet files (Snappy compressed)             │
│  Path: /data/archive/org={orgId}/year={YYYY}/month={MM}/telemetry.parquet
│  DuckDB Vectorized In-Memory OLAP Query Engine                         │
│  S3 / GCS Object Storage (Optional Long-term Glacier tiering)          │
└────────────────────────────────────────────────────────────────────────┘
```

### Scheduled Lifecycle Sequence (02:30 UTC daily)
1. **Acquire Redis Distributed Lock:** Key `lock:telemetry:retention` (TTL 2 hours) ensures only 1 backend pod executes the maintenance task.
2. **Phase 1 — Pre-computed Rollups:** Aggregates hourly, daily, and monthly statistical summaries into PostgreSQL rollups tables (`telemetry_daily_rollups`, `telemetry_monthly_rollups`).
3. **Phase 2 — Columnar Parquet Export:** Streams telemetry rows older than 30 days into Snappy-compressed Parquet files in the Hive partition path.
4. **Phase 3 — Raw Row Purge:** Deletes raw telemetry records older than `telemetry.retention-days` (default 30 days).
5. **Phase 4 — Partition Detach & Drop:** Detaches and drops empty monthly partition tables whose records were archived.

---

## 2. Partition Structure & Storage Layout

Parquet files are organized by tenant and date for efficient DuckDB partition pruning:

```
/data/archive/
  └── org=e4b02927-5645-42f2-8923-b1d75fefca91/
      ├── year=2026/
      │   ├── month=08/
      │   │   └── telemetry.parquet   (~12 MB per 1M rows)
      │   ├── month=09/
      │   │   └── telemetry.parquet
      │   └── month=10/
      │       └── telemetry.parquet
```

- **Compression:** Snappy (~80% compression ratio compared to raw PostgreSQL storage).
- **Parquet Schema:**
  - `id` (VARCHAR / UUID)
  - `device_id` (VARCHAR / UUID)
  - `organization_id` (VARCHAR / UUID)
  - `temperature` (DOUBLE)
  - `humidity` (DOUBLE)
  - `motion` (BOOLEAN)
  - `smoke_ppm` (DOUBLE)
  - `timestamp` (TIMESTAMP)

---

## 3. Incident Scenarios & Remediation

### Scenario 1: PVC Disk Space Warning (Lakehouse Volume $> 85\%$)

**Symptoms:**
- Storage alert: `disk_usage_percent{mount="/data/archive"} > 85`.
- Backend logs show `IOException: No space left on device` during archival export.

**Diagnosis:**
1. Check PVC disk usage in Kubernetes:
   ```bash
   kubectl exec -it deployment/sentinel-iot-backend -n sentinel-prod -- df -h /data/archive
   ```
2. Identify largest tenant archives:
   ```bash
   kubectl exec -it deployment/sentinel-iot-backend -n sentinel-prod -- du -sh /data/archive/*
   ```

**Remediation:**
1. **Expand PVC (online expansion supported on AWS EBS gp3 and EFS):**
   ```yaml
   # Patch PVC storage request
   kubectl patch pvc sentinel-iot-lakehouse-pvc -n sentinel-prod \
     -p '{"spec":{"resources":{"requests":{"storage":"200Gi"}}}}'
   ```
2. **Sync historical years (> 1 year old) to Cloud Object Storage (S3 / GCS):**
   ```bash
   # Sync archives older than 2025 to Amazon S3
   aws s3 sync /data/archive/ s3://sentinel-lakehouse-coldstorage/archive/ \
     --exclude "*" --include "*/year=2024/*" --include "*/year=2025/*"
   ```
3. After S3 sync is verified, remove the synced local directories to free space.

---

### Scenario 2: Stale Distributed Lock / Archival Job Skipped

**Symptoms:**
- Daily raw data older than 30 days is not being pruned; database table size keeps growing.
- Logs show: `Retention: another replica is already executing the daily retention job. Skipping.` repeatedly.

**Diagnosis:**
1. A previous pod died or was killed by OOM-killer while holding `lock:telemetry:retention`.
2. Check lock in Redis:
   ```bash
   redis-cli -h sentinel-redis TTL lock:telemetry:retention
   ```

**Remediation:**
1. Delete the stale lock in Redis:
   ```bash
   redis-cli -h sentinel-redis DEL lock:telemetry:retention
   ```
2. Trigger on-demand archival via REST API (see Section 4.1).

---

### Scenario 3: Corrupt Parquet File in Lakehouse Engine

**Symptoms:**
- DuckDB OLAP queries fail with: `Invalid Input Error: Parquet file has invalid magic bytes` or `EOF reached while reading footer`.
- Frontend `/lakehouse` page displays query error.

**Diagnosis:**
1. Identify the corrupt file path from backend log stack trace:
   ```bash
   kubectl logs deployment/sentinel-iot-backend -n sentinel-prod | grep "DuckDB Lakehouse query failed"
   ```
2. Verify Parquet header magic bytes (`PAR1`):
   ```bash
   head -c 4 /data/archive/org={id}/year={YYYY}/month={MM}/telemetry.parquet
   ```
   *Expected:* Output must be `PAR1`.

**Remediation:**
1. If file is truncated (e.g. abrupt pod termination during write):
   - Move corrupted file aside:
     ```bash
     mv /data/archive/org={id}/year={YYYY}/month={MM}/telemetry.parquet /data/archive/corrupted.parquet
     ```
   - Re-export the missing month range from PostgreSQL (if still within retention) or restore from S3 backup.
   - Run manual trigger for the affected month:
     ```bash
     curl -X POST -H "Authorization: Bearer $ADMIN_JWT" \
       "http://localhost:8080/api/v1/analytics/archive/trigger?from=2026-08-01T00:00:00Z&to=2026-09-01T00:00:00Z"
     ```

---

### Scenario 4: DuckDB Memory Pressure / Out of Memory

**Symptoms:**
- Backend pod OOMKilled during wide aggregations (`SELECT ... FROM read_parquet('/data/archive/**/*.parquet')`).
- Metric `jvm_memory_used_bytes` spikes due to off-heap native memory allocated by DuckDB.

**Diagnosis:**
1. Review DuckDB memory configuration: DuckDB defaults to 80% of system RAM if not limited.
2. Inspect queries requesting overly broad wildcard globs without date filters.

**Remediation:**
1. `LakehouseAnalyticsService` includes date range and tenant partition pruning (`read_parquet('/data/archive/org=.../year=.../**/*.parquet')`). Ensure callers supply `from` and `to` timestamps.
2. In `values-prod.yaml`, ensure backend memory limits allow headroom for DuckDB vectorized execution:
   ```yaml
   backend:
     resources:
       requests:
         memory: 768Mi
       limits:
         memory: 2048Mi
   ```

---

## 4. Emergency Operational Procedures

### 4.1. Trigger On-Demand Parquet Archival via REST API
```bash
# Archive telemetry rows between two timestamps
curl -X POST \
  -H "Authorization: Bearer $ADMIN_JWT" \
  "http://sentinel-backend:8080/api/v1/analytics/archive/trigger?from=2026-08-01T00:00:00Z&to=2026-09-01T00:00:00Z" | jq .
```
Expected response:
```json
{
  "recordsArchived": 45200,
  "bytesWritten": 542100,
  "filesCreated": [
    "/data/archive/org=e4b02927-5645-42f2-8923-b1d75fefca91/year=2026/month=08/telemetry.parquet"
  ],
  "durationMs": 842,
  "timestamp": 1791880200000
}
```

### 4.2. Verify Parquet Data using DuckDB CLI (Local or Container)
Inside backend container or local workstation:
```bash
# Install duckdb CLI (if local) or run inside container
duckdb

# In DuckDB interactive shell:
D SELECT COUNT(*) FROM read_parquet('/data/archive/**/*.parquet');
D SELECT date_trunc('month', timestamp) AS month, AVG(temperature), MAX(smoke_ppm)
  FROM read_parquet('/data/archive/**/*.parquet')
  GROUP BY 1 ORDER BY 1;
```

### 4.3. Manual Cloud Storage Sync (S3 / GCS)
```bash
# S3 Sync with AWS CLI
aws s3 sync /data/archive/ s3://sentinel-lakehouse-coldstorage/archive/ \
  --storage-class STANDARD_IA

# GCS Sync with gsutil
gsutil -m rsync -r /data/archive/ gs://sentinel-lakehouse-coldstorage/archive/
```
