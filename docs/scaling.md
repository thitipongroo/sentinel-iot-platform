# Scaling Discussion

## Current Baseline

The single-node Docker Compose stack sustains:

| Metric | Value |
|--------|-------|
| Telemetry throughput | ~1,000 events/sec (k6 verified) |
| p95 API latency | < 120 ms |
| Concurrent WebSocket sessions | ~500 (single JVM, `CopyOnWriteArraySet`) |
| PostgreSQL writes | ~1,000 INSERTs/sec before WAL bottleneck |
| Redis reads | < 1 ms (in-memory, single node) |

This is sufficient for a factory with up to ~200 devices publishing every 5 seconds (≈ 40 events/sec sustained, with headroom for bursts).

---

## Bottleneck Map

```text
[ Many IoT Devices ]
         │
         ▼
  [ Mosquitto MQTT ]  ← Bottleneck #1: single broker, single TCP port
         │
         ▼
  [ Spring Boot ]     ← Bottleneck #2: single instance, @ServiceActivator is single-threaded
         │
    ┌────┴────┐
    ▼         ▼
[ Redis ]  [ PostgreSQL ]  ← Bottleneck #3: single writer, table scans on large telemetry
    │
    ▼
[ WebSocket Handler ]  ← Bottleneck #4: CopyOnWriteArraySet in one JVM
```

---

## Scaling Each Layer

### 1. MQTT Broker — Horizontal Cluster

**Problem:** A single Mosquitto instance handles ~100k connections, but loses all in-flight messages on restart and cannot distribute load.

**Solution: EMQX Cluster**

Replace Mosquitto with [EMQX](https://www.emqx.io/) which supports native horizontal clustering:

```yaml
# docker-compose addition
emqx:
  image: emqx/emqx:5.6
  environment:
    EMQX_CLUSTER__DISCOVERY_STRATEGY: static
    EMQX_CLUSTER__STATIC__SEEDS: "emqx@node1.example.com"
  deploy:
    replicas: 3
```

- Each EMQX node handles ~1M connections
- Built-in rule engine routes messages to Kafka or directly to consumers
- Retained messages and sessions survive node failure

**Alternative for smaller scale:** Mosquitto with a shared-nothing multi-broker topology using bridge replication.

---

### 2. Backend — Stateless Horizontal Scale

Spring Boot is **already stateless** — JWT is self-contained, and all session state lives in Redis. Scale by adding replicas:

```yaml
# docker-compose with replicas
backend:
  deploy:
    replicas: 3
  # add a load balancer in front (nginx, Traefik, or cloud LB)
```

**MQTT Consumer scaling issue:** Each backend replica creates its own MQTT subscription. All replicas receive all messages and write duplicate rows.

**Fix — Use a message queue as a buffer:**

```text
Mosquitto → Kafka topic (factory.telemetry) → Consumer Group
                                                    │
                            ┌───────────────────────┤
                            ▼                       ▼
                    Backend replica 1       Backend replica 2
                    (partition 0-4)         (partition 5-9)
```

Kafka consumer groups ensure each message is processed by exactly one backend instance. This also gives replay capability and backpressure handling.

**Minimum Kafka setup:**

```yaml
kafka:
  image: confluentinc/cp-kafka:7.6.1
  environment:
    KAFKA_NUM_PARTITIONS: 10
    KAFKA_DEFAULT_REPLICATION_FACTOR: 2
```

---

### 3. PostgreSQL — Read Replicas + Partitioning

**Problem:** The `telemetry` table grows at ~1,000 rows/sec. After ~100M rows, range queries slow down even with indexes.

**Step 1 — Read replicas** (immediate win, zero code change):

```text
Primary  ──── write ────▶ (writes: telemetry INSERTs, device UPDATEs)
    │
    └─── replicate ────▶ Replica 1 (reads: /latest, /range queries)
                   ────▶ Replica 2 (reads: alerts, reporting)
```

Spring Data JPA with `@Transactional(readOnly=true)` routes to replicas via `AbstractRoutingDataSource`.

**Step 2 — Table partitioning by timestamp** (when rows exceed 500M):

```sql
-- Range partition by month
CREATE TABLE telemetry (
    id UUID, device_id UUID, temperature DOUBLE PRECISION,
    humidity DOUBLE PRECISION, motion BOOLEAN, smoke_ppm DOUBLE PRECISION,
    timestamp TIMESTAMPTZ NOT NULL
) PARTITION BY RANGE (timestamp);

CREATE TABLE telemetry_2024_06 PARTITION OF telemetry
    FOR VALUES FROM ('2024-06-01') TO ('2024-07-01');
```

Old partitions can be detached and archived to cold storage (S3) without locking the hot partition.

**Step 3 — Migrate to TimescaleDB** (when partitioning isn't enough):

TimescaleDB is a PostgreSQL extension with automatic time partitioning (hypertables), continuous aggregates, and data tiering. Because it speaks the PostgreSQL wire protocol, migration requires zero application code change — only swap the Docker image:

```yaml
postgres:
  image: timescale/timescaledb:latest-pg16
```

```sql
SELECT create_hypertable('telemetry', 'timestamp', chunk_time_interval => INTERVAL '1 day');
```

---

### 4. Redis — Cluster Mode

**Problem:** A single Redis node is limited to ~25 GB RAM and has a single write thread.

**Solution:** Redis Cluster with hash slot sharding:

```yaml
redis:
  image: redis:7-alpine
  command: redis-server --cluster-enabled yes --cluster-config-file nodes.conf
  deploy:
    replicas: 6   # 3 primaries + 3 replicas
```

`device:status:{id}` and `device:telemetry:{id}` keys are already designed with device UUID as the slot key, so all hash fields for one device land on the same node (consistent hashing).

For managed Redis, **Upstash** (serverless) or **ElastiCache** handle cluster management automatically.

---

### 5. WebSocket Gateway — Pub/Sub Fan-out

**Problem:** `CopyOnWriteArraySet<WebSocketSession>` only holds sessions local to one JVM. When backend scales to 3 replicas, a message processed by replica 1 is not broadcast to browsers connected to replicas 2 and 3.

**Solution: Redis Pub/Sub as a cross-node bus:**

```text
Replica 1 processes MQTT message
    │
    ├──▶ publish to Redis channel "ws:telemetry"
    │
Redis broadcasts to all subscribers
    │
    ├──▶ Replica 1 WebSocket Handler → browser sessions on replica 1
    ├──▶ Replica 2 WebSocket Handler → browser sessions on replica 2
    └──▶ Replica 3 WebSocket Handler → browser sessions on replica 3
```

Implementation change in `TelemetryWebSocketHandler`:

```java
// Instead of direct broadcast, publish to Redis
redisTemplate.convertAndSend("ws:telemetry", payload);

// Each instance subscribes and broadcasts locally
@Bean
public MessageListenerAdapter listenerAdapter(TelemetryWebSocketHandler handler) {
    return new MessageListenerAdapter(handler, "onMessage");
}
```

---

### 6. Frontend — CDN and Edge

Next.js on Vercel already distributes static assets via global CDN. For the WebSocket connection:

- In development: direct to `ws://localhost:8080`
- In production: use a WebSocket-aware load balancer (AWS ALB with sticky sessions, or Cloudflare with WebSocket support) pointing to the backend cluster

```text
Browser ──▶ Vercel CDN (static/SSR pages)
        ──▶ ALB (sticky session by cookie) ──▶ Backend replica with open WS session
```

---

## Scaling Roadmap

| Scale | Devices | Events/sec | Actions needed |
|-------|---------|-----------|----------------|
| **Current** | ~200 | ~40 | Docker Compose, single node |
| **Small** | ~2,000 | ~400 | Add PostgreSQL read replica |
| **Medium** | ~10,000 | ~2,000 | Add Kafka, scale backend ×3, Redis Cluster |
| **Large** | ~100,000 | ~20,000 | EMQX cluster, TimescaleDB, Redis Pub/Sub WS, separate alert microservice |
| **Web-scale** | ~1,000,000 | ~200,000 | Dedicated ingestion service (Rust/Go), Flink for stream processing, Cassandra for raw telemetry |

---

## Load Test Results (Baseline)

Scenario: ramp 10 → 1,000 concurrent requests/sec over 5 minutes against a single Docker Compose node.

```text
http_reqs................: 180,432  (1,003/s peak)
http_req_duration........: avg=48ms  p(95)=112ms  p(99)=187ms
success_rate.............: 99.7%
sentinel_telemetry_received_total: 10,800/min sustained
```

At 1,000 req/s, PostgreSQL CPU was the first resource to saturate (~70% on a 4-core host). Redis and the JVM had significant headroom remaining. Adding a read replica would push the bottleneck past 3,000 req/s without any code change.
