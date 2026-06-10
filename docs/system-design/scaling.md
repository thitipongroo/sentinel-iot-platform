# Scaling Discussion

## Current Baseline

The single-node Docker Compose stack sustains:

| Metric | SLO target | Observed baseline | Notes |
| --- | --- | --- | --- |
| API read throughput | — | 1,003 req/s | k6 `ramping-arrival-rate`, Redis-backed endpoint |
| p95 API latency | < 200 ms | **112 ms** | 88 ms headroom before SLO breach |
| p99 API latency | < 500 ms | **187 ms** | 313 ms headroom |
| Concurrent WebSocket sessions | — | ~500 (single JVM) | `CopyOnWriteArraySet`; Redis pub/sub fan-out at scale |
| PostgreSQL writes | — | ~1,000 INSERTs/sec | WAL bottleneck at 70% CPU on 4-core host |
| Redis reads | — | < 1 ms | In-memory, single node |
| Replay queue drain | — | 100 msg / 30 s | Bounded at 10,000 messages max |

> **SLO targets** are the pass/fail thresholds in `tests/load/telemetry.js` and `infra/monitoring/slo-rules.yaml`.
> **Observed baseline** values are from a single k6 run on a local Docker Compose node — not a guaranteed production SLO.

This is sufficient for a factory with up to ~200 devices publishing every 5 seconds (≈ 40 events/sec sustained, with headroom for bursts).

At 1,000 req/s in load testing, PostgreSQL CPU saturated first (~70% on a 4-core host). Redis and the JVM had significant headroom remaining.

---

## Bottleneck Map

```text
[ Many IoT Devices ]
         │
         ▼
  [ Mosquitto MQTT ]        ← Bottleneck #1: single broker, single TCP port
         │
         ▼
  [ Kafka ]                 ← factory.telemetry topic (Avro-encoded)
  (sentinel-backend              consumer group partitions across replicas;
   consumer group)               schema enforced by Schema Registry at startup
         │
    ┌────┴────┐
    ▼         ▼
[ Spring Boot ] [ Spring Boot ] ← Each replica owns a subset of partitions
  │ TelemetryService           ←   @Retry + @CircuitBreaker per call
  │ ReplayQueueService          ←   30s drain cycle, 100/batch
         │
    ┌────┴────┐
    ▼         ▼
[ Redis ]  [ PostgreSQL ]   ← Bottleneck #2: single writer; telemetry
                                 partitioned by month but still one WAL stream
    │
    ▼
[ WebSocket Handler ]       ← Bottleneck #3: CopyOnWriteArrayList in one JVM

[ Rate Limiter ]            ← Bottleneck #4: Bucket4j in-process
                                 100 req/min per IP, per replica
```

---

## Scaling Each Layer

### 1. MQTT Broker — Horizontal Cluster

**Problem:** A single Mosquitto instance handles ~100k connections, but loses in-flight messages on restart and cannot distribute load.

Solution: EMQX Cluster

```yaml
emqx:
  image: emqx/emqx:5.6
  environment:
    EMQX_CLUSTER__DISCOVERY_STRATEGY: static
    EMQX_CLUSTER__STATIC__SEEDS: "emqx@node1.example.com"
  deploy:
    replicas: 3
```

Each EMQX node handles ~1M connections. Built-in rule engine routes messages to Kafka or directly to consumers. Retained messages and sessions survive node failure.

**Alternative for smaller scale:** Mosquitto with multi-broker bridge replication.

---

### 2. Backend — Stateless Horizontal Scale

Spring Boot is **already stateless** — JWT is self-contained, and all session state lives in Redis. Scale by adding replicas:

```yaml
backend:
  deploy:
    replicas: 3
  # add a load balancer in front (nginx, Traefik, or cloud LB)
```

**MQTT Consumer scaling (implemented):** Mosquitto publishes all inbound telemetry to the `factory.telemetry` Kafka topic (Avro-encoded, 10 partitions). The `sentinel-backend` consumer group distributes partitions across replicas — each message is processed by exactly one instance:

```text
Mosquitto → Kafka topic (factory.telemetry) → Consumer Group (sentinel-backend)
                                                    │
                            ┌───────────────────────┤
                            ▼                       ▼
                    Backend replica 1       Backend replica 2
                    (partition 0–4)         (partition 5–9)
```

Kafka committed offsets provide durable replay — if a replica restarts, it picks up from the last committed offset with no message loss.

**Replay queue:** The Redis replay queue (`sentinel:replay:queue`) handles the DB circuit-breaker fallback path — buffering processed messages when PostgreSQL is unavailable and draining them on recovery. This is a separate concern from Kafka partitioning.

---

### 3. PostgreSQL — Partitioning (Done) + Read Replicas + TimescaleDB

**Partitioning is already implemented (V3 migration).** The `telemetry` table uses `PARTITION BY RANGE(timestamp)` with monthly child tables from 2025-01 through 2026-12 plus a `telemetry_default` catch-all. PostgreSQL automatically prunes irrelevant partitions from range queries.

Remaining PostgreSQL scaling steps:

**Step 1 — Read replicas** (zero code change):

```text
Primary ──── write ──▶ (telemetry INSERTs, device UPDATEs)
    │
    └── replicate ──▶ Replica 1 (reads: /latest, /range, /hourly queries)
               ──▶ Replica 2 (reads: alerts, reporting)
```

Spring Data JPA with `@Transactional(readOnly=true)` routes to replicas via `AbstractRoutingDataSource`.

**Step 2 — Extend partition range** (when approaching 2026-12):

Add a new Flyway migration to pre-create 2027+ monthly partitions:

```sql
CREATE TABLE telemetry_2027_01 PARTITION OF telemetry
    FOR VALUES FROM ('2027-01-01') TO ('2027-02-01');
```

**Step 3 — Drop old partitions** (cold archival):

Old partitions can be detached and archived without locking the hot partition:

```sql
ALTER TABLE telemetry DETACH PARTITION telemetry_2025_01;
-- export to S3 / cold storage, then:
DROP TABLE telemetry_2025_01;
```

This should be automated in the `TelemetryRetentionService` cron after data is confirmed aggregated.

**Step 4 — Migrate to TimescaleDB** (when partitioning is not enough):

```yaml
postgres:
  image: timescale/timescaledb:latest-pg16
```

```sql
SELECT create_hypertable('telemetry', 'timestamp', chunk_time_interval => INTERVAL '1 day');
```

No application code changes required — same wire protocol, same JPA repositories.

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

`device:telemetry:{id}` keys are already scoped by device UUID, so all hash fields for one device land on the same node (consistent hashing by `{id}` key slot).

**Replay queue in cluster mode:** `sentinel:replay:queue` is a single key, so it always lands on one node — cluster mode doesn't help here. Migrate to Kafka for the replay queue if multi-replica backend scale is needed.

For managed Redis: **Upstash** (serverless) or **ElastiCache** handle cluster management automatically.

---

### 5. Rate Limiter — Shared State

**Problem:** Bucket4j uses a local in-memory store. With 3 backend replicas, each instance has its own bucket — the effective rate limit is `100 × 3 = 300 req/min` per IP, not 100.

**Solution:** Switch to `bucket4j-redis`:

```java
// Replace: BandwidthLimiter backed by ConcurrentHashMap
// With:    BucketProxyManager backed by RedisClient
ProxyManager<String> proxyManager = Bucket4jRedis.casBasedBuilder(redisClient).build();
Bucket bucket = proxyManager.builder()
    .addLimit(Bandwidth.classic(100, Refill.greedy(100, Duration.ofMinutes(1))))
    .build(ipAddress);
```

All replicas share bucket state via Redis atomic `GETSET` operations. No additional infrastructure required beyond the existing Redis instance.

---

### 6. WebSocket Gateway — Pub/Sub Fan-out

**Problem:** `TelemetryWebSocketHandler` holds sessions in a local `CopyOnWriteArrayList`. A message processed by replica 1 is not broadcast to browsers connected to replicas 2 and 3.

**Solution:** Redis Pub/Sub as a cross-node bus:

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

### 7. Frontend — CDN and Edge

Next.js on Vercel already distributes static assets via global CDN. For the WebSocket connection:

- Development: direct to `ws://localhost:8080`
- Production: WebSocket-aware load balancer (AWS ALB with sticky sessions, or Cloudflare with WebSocket support) pointing to the backend cluster

```text
Browser ──▶ Vercel CDN (static/SSR pages)
        ──▶ ALB (sticky session by cookie) ──▶ Backend replica with open WS session
```

---

## Scaling Roadmap

| Scale | Devices | Events/sec | Actions needed |
| --- | --- | --- | --- |
| **Current** | ~200 | ~40 | Docker Compose, single node, partitioned telemetry; Kafka consumer group (sentinel-backend) |
| **Small** | ~2,000 | ~400 | Add PostgreSQL read replica; bucket4j-redis rate limiting |
| **Medium** | ~10,000 | ~2,000 | Scale backend ×3; Redis Cluster; Redis Pub/Sub WS fan-out |
| **Large** | ~100,000 | ~20,000 | EMQX cluster; TimescaleDB; separate alert microservice; drop partition archival |
| **Web-scale** | ~1M | ~200,000 | Dedicated ingestion service (Rust/Go); Flink for stream processing; Cassandra for raw telemetry |

---

## Load Test Results (Baseline)

Scenario: ramp 10 → 1,000 concurrent requests/sec over 5 minutes against a single Docker Compose node.

```text
http_reqs................: 180,432  (1,003/s peak)
http_req_duration........: avg=48ms  p(95)=112ms  p(99)=187ms
success_rate.............: 99.7%
sentinel_telemetry_received_total: 10,800/min sustained
```

PostgreSQL CPU was the first resource to saturate (~70% on a 4-core host). Redis and the JVM had significant headroom. Adding a read replica would push the bottleneck past 3,000 req/s without any code change.
