# Capacity Planning

This document maps device scale to infrastructure requirements and defines the
upgrade path for each layer. Use it to answer: "How many devices can we add before
something breaks?"

---

## Baseline Measurements (Single-Node Docker Compose)

Load tested on MacBook Pro M3, 16 GB RAM, Docker Compose.

| Metric | Value | Measurement method |
|--------|-------|-------------------|
| API read throughput (cached) | 1,003 req/s | k6 `ramping-arrival-rate`, `GET /api/telemetry/{id}/cache` |
| p95 API latency | 112 ms | k6 (SLO target: < 200 ms) |
| p99 API latency | 187 ms | k6 (SLO target: < 500 ms) |
| MQTT ingest throughput | ~400 events/s | Simulator burst test |
| PostgreSQL INSERT rate | ~1,000 rows/s | Before WAL bottleneck at 70% CPU |
| Redis read latency | < 1 ms | In-process measurement |
| Kafka consumer lag at 400 events/s | 0 (keeps up) | `kafka-consumer-groups.sh` |
| Replay queue max capacity | 10,000 messages | `TELEMETRY_REPLAY_MAX_QUEUE` config |
| WebSocket sessions per JVM | ~500 | Memory estimate, not load tested |

---

## Capacity Matrix

| Scale tier | Devices | Events/sec | Architecture needed |
|------------|---------|------------|---------------------|
| **Starter** | 1–50 | < 10 | Single Docker Compose node (current) |
| **Small** | 51–200 | 10–40 | Docker Compose + external PostgreSQL |
| **Medium** | 201–2,000 | 40–400 | Kubernetes, 2–3 backend replicas, PostgreSQL read replica |
| **Large** | 2,001–10,000 | 400–2,000 | Kubernetes, 3–9 backend replicas, Redis Cluster, EMQX broker |
| **XLarge** | 10,001–100,000 | 2,000–20,000 | Kafka partition increase (9→27), TimescaleDB, separate alert service |
| **Web-scale** | 100,000+ | 20,000+ | Dedicated ingest service (Rust/Go), Flink, Cassandra for raw telemetry |

---

## Per-Layer Capacity Limits and Upgrade Triggers

### 1. MQTT Broker (Mosquitto)

| Metric | Limit | Upgrade trigger |
|--------|-------|-----------------|
| Concurrent connections | ~100,000 | > 50,000 sustained connections |
| Message throughput | ~50,000 msg/s | > 10,000 msg/s sustained |
| In-flight QoS 1 messages | ~1,000 | Repeated `PUBACK` timeouts in client logs |

**Upgrade path:** Mosquitto → [EMQX Cluster](./scaling.md#1-mqtt-broker--horizontal-cluster) (3 nodes, 3M connections total)

---

### 2. Backend (Spring Boot)

| Metric | Per-replica limit | Scale trigger |
|--------|------------------|---------------|
| Kafka consumer throughput | ~500 msg/s | Consumer lag > 10,000 (KEDA fires) |
| HTTP requests | ~1,000 req/s | CPU > 70% for 2+ minutes (HPA fires) |
| WebSocket sessions | ~500 | Memory > 80% (HPA fires) |
| DB connections (Hikari) | 20 (default) | `hikaricp_connections_pending > 0` for 1 min |

**Horizontal scale limit:** 3 replicas (bounded by 3 Kafka partitions).
To scale beyond 3, increase Kafka partitions to 9 (see `KafkaConfig.java`).

**Vertical upgrade trigger:** JVM heap > 85% sustained → increase container memory limit.

---

### 3. PostgreSQL

| Metric | Limit | Upgrade trigger |
|--------|-------|-----------------|
| INSERT throughput | ~1,000 rows/s | CPU > 70% on primary |
| Active connections | 100 (default `max_connections`) | `hikaricp_connections_pending > 0` |
| Disk growth | ~50 GB/year at 200 devices, 5s intervals, 30-day retention | Disk > 70% full |
| Query latency (p95) | < 10 ms | `mean_exec_time > 50 ms` in `pg_stat_statements` |

**Upgrade path:**
1. Add read replica for `SELECT` queries (zero code change — use `@Transactional(readOnly=true)`)
2. Extend partition table to 2027+ (Flyway migration)
3. Migrate to TimescaleDB for automatic chunking
4. Move to managed service: AWS RDS PostgreSQL with Multi-AZ

---

### 4. Redis

| Metric | Limit | Upgrade trigger |
|--------|-------|-----------------|
| Read throughput | ~100,000 ops/s | Rarely a bottleneck |
| Memory | ~25 GB (single node) | Used memory > 70% of `maxmemory` |
| Latency | < 1 ms | Latency > 5 ms in `redis-cli latency history` |
| Pub/sub message rate | ~100,000 msg/s | WebSocket broadcast lag |

**Upgrade path:** Single node → [Redis Cluster](./scaling.md#4-redis--cluster-mode) (3 primary + 3 replica)

**Note:** Rate limiter uses in-process Bucket4j. Effective limit is `100 × replicas` req/min per IP.
Migrate to `bucket4j-redis` when > 2 backend replicas are deployed.

---

### 5. Kafka

| Metric | Limit | Upgrade trigger |
|--------|-------|-----------------|
| Partitions (`telemetry.raw`) | 3 | Consumer count needed > 3 |
| Max consumers in group | 3 (= partition count) | KEDA wants to scale > 3 replicas |
| Throughput | ~50 MB/s per broker | Producer throughput > 10 MB/s |
| Retention | 7 days, ~50 GB at baseline | Disk > 60% full |

**Upgrade path:** Increase partitions from 3 → 9 → 27 as needed.
Partition count cannot be decreased — plan ahead.

---

## Cost Estimates (AWS, us-east-1, on-demand)

| Scale tier | PostgreSQL | Redis | EKS nodes | Kafka (MSK) | Monthly est. |
|------------|-----------|-------|-----------|------------|-------------|
| Small (200 devices) | `db.t3.medium` ($0.068/h) | `cache.t3.micro` ($0.017/h) | 2× `t3.medium` | Not needed | ~$120 |
| Medium (2k devices) | `db.t3.large` ($0.136/h) | `cache.t3.small` ($0.034/h) | 3× `t3.large` | `kafka.t3.small` ($0.093/h) | ~$350 |
| Large (10k devices) | `db.r6g.large` ($0.260/h) + replica | `cache.r7g.large` ($0.157/h) | 6× `m5.large` | `kafka.m5.large` ×2 ($0.317/h) | ~$1,200 |

All estimates exclude data transfer, storage, and support.

---

## Monitoring Thresholds for Proactive Scaling

Configure these alert thresholds in Grafana to act before hitting hard limits:

| Component | Metric | Warning | Action |
|-----------|--------|---------|--------|
| Backend | CPU | > 60% for 5 min | Manual review; KEDA/HPA will fire at 70% |
| Backend | Heap | > 75% for 5 min | Increase `JAVA_TOOL_OPTIONS=-Xmx2g` |
| PostgreSQL | CPU | > 50% | Plan read replica; add index |
| PostgreSQL | Connections | > 15 active / replica | Increase Hikari pool or add replica |
| Redis | Memory | > 60% | Plan cluster upgrade |
| Kafka | Consumer lag | > 5,000 | Verify KEDA is scaling; check partition count |
| Mosquitto | Connections | > 30,000 | Plan EMQX migration |

---

## Capacity Review Cadence

| Trigger | Action |
|---------|--------|
| Device count crosses 150 | Review PostgreSQL WAL headroom; plan read replica |
| Device count crosses 500 | Evaluate Kafka partition increase (3 → 9) |
| Device count crosses 2,000 | Evaluate Redis Cluster and EMQX migration |
| Monthly billing > $500 | Review Reserved Instance pricing for all services |
| Any component consistently > 70% utilisation for 1 week | Initiate upgrade sprint |
