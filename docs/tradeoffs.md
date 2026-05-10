# Design Tradeoffs

Every architectural decision involves a tradeoff. This document captures the key choices made in Sentinel IoT Platform, the alternatives considered, and the reasoning behind each decision.

---

## 1. Next.js 14 (App Router) vs Vite + React SPA

**Chosen:** Next.js 14 with App Router

| Aspect | Next.js | Vite + React SPA |
| --- | --- | --- |
| Routing | File-based, zero config | `react-router-dom`, manual setup |
| API proxy | `rewrites` in `next.config.mjs` | Requires Vite proxy + nginx in prod |
| Deployment | Native Vercel (zero config) | Static build + CDN + reverse proxy |
| SSR / SEO | Built-in | Not available |
| `'use client'` boundary | Server Components reduce JS sent | Everything is client-side |

**Reasoning:** The `rewrites` feature eliminates the need for a separate nginx container in production. Vercel's native Next.js support means the dashboard deploys with a single `git push`. Server Components keep the initial JS payload small — layout and static content ship with zero client JS.

**Tradeoff accepted:** Next.js adds build complexity and a larger `node_modules` footprint. The development feedback loop (`next dev`) is slightly slower than Vite HMR for large component trees.

---

## 2. MQTT vs HTTP Polling

**Chosen:** MQTT (Eclipse Mosquitto)

| Aspect | MQTT | HTTP Polling |
| --- | --- | --- |
| Connection model | Persistent TCP, event-driven | Stateless, request/response |
| Bandwidth per device | ~2 bytes fixed header | ~500+ bytes HTTP headers |
| Latency | Sub-second | Bounded by poll interval |
| QoS guarantees | QoS 0/1/2 (at-least-once available) | None (app-layer retry needed) |
| Device battery impact | Low (keep-alive only) | High (repeated HTTP overhead) |
| Bidirectional | Yes (SUBSCRIBE + PUBLISH) | Requires long-polling hack |

**Reasoning:** With 200 devices polling every 5 seconds, HTTP polling generates 2,400 requests/minute even when no values change. MQTT's persistent sessions also allow devices behind NAT to receive commands — a feature needed for the remote firmware update story.

**Tradeoff accepted:** MQTT requires running and maintaining a broker. For a pure read-only telemetry use case at small scale, HTTP polling is simpler.

---

## 3. Redis vs Memcached

**Chosen:** Redis 7

| Aspect | Redis | Memcached |
| --- | --- | --- |
| Data structures | Strings, Hashes, Lists, Sets, Sorted Sets | Strings only |
| List (RPUSH/LPOP) | Yes — used for replay queue | No |
| Persistence | RDB + AOF (configurable) | None |
| Pub/Sub | Built-in — used for future WS fan-out | Not available |
| Atomic ops | Yes (INCR, Lua scripts) | Limited |

**Reasoning:** The cache stores multi-field telemetry using `HSET/HGET` — one key per device, one hash field per sensor. The Redis List data structure powers the replay queue (`RPUSH` to enqueue, `LPOP` to drain). Memcached would require serializing the whole telemetry record to a string for both use cases, losing partial field update capability and adding CPU overhead.

**Tradeoff accepted:** Redis consumes more RAM than Memcached for equivalent datasets due to richer data structures.

---

## 4. PostgreSQL vs Time-Series Database (InfluxDB / TimescaleDB)

**Chosen:** PostgreSQL 16

| Aspect | PostgreSQL | InfluxDB | TimescaleDB |
| --- | --- | --- | --- |
| Data model | Relational | Tag/field model | Relational (PostgreSQL extension) |
| Write throughput | ~100k rows/sec (tuned) | ~1M points/sec | ~200k rows/sec |
| Spring Data JPA | Full support | Requires separate client | Full support |
| Operational overhead | Low | High (separate stack) | Low (same as PostgreSQL) |
| Range partitioning | Native (V3 migration) | Automatic | Automatic via hypertables |

**Reasoning:** At the current scale (<10M rows/month), PostgreSQL with declarative range partitioning handles all range queries under 50ms. Introducing InfluxDB would add a second database technology, a new query language (Flux), and a second persistence layer to maintain.

TimescaleDB is the preferred migration path beyond 500M rows because it is a PostgreSQL extension — all Spring Data JPA repositories continue to work unchanged.

**Tradeoff accepted:** PostgreSQL monthly partitions must be pre-created (see Known Limitations in the README). InfluxDB and TimescaleDB handle this automatically.

---

## 5. Spring Integration vs Raw Paho MQTT Client

**Chosen:** Spring Integration with `MqttPahoMessageDrivenChannelAdapter`

| Aspect | Spring Integration | Raw Paho Client |
| --- | --- | --- |
| Reconnect handling | Automatic | Manual `MqttCallback.connectionLost()` |
| Channel routing | Declarative (`@ServiceActivator`) | Imperative `messageArrived()` callback |
| Error / DLQ routing | Spring error channel → `@ServiceActivator` | Manual try/catch |
| Testing | Mock channels | Requires live broker |

**Reasoning:** `@ServiceActivator(inputChannel = "mqttInputChannel")` turns message consumption into a plain Spring bean method. The DLQ outbound adapter (`@ServiceActivator(inputChannel = "mqttDlqChannel")`) wires cleanly alongside. Without Spring Integration, the equivalent Paho implementation would require ~200 lines of reconnect, thread pool, and error handling boilerplate.

**Tradeoff accepted:** Spring Integration adds a learning curve. For a simple single-topic consumer, raw Paho is more transparent.

---

## 6. WebSocket vs Server-Sent Events (SSE)

**Chosen:** WebSocket (native Spring WS, `TextWebSocketHandler`)

| Aspect | WebSocket | Server-Sent Events |
| --- | --- | --- |
| Direction | Bidirectional | Server → Client only |
| Protocol | WS / WSS upgrade | HTTP/1.1 or HTTP/2 |
| Proxy / firewall | Some proxies block WS | Always works over HTTP |
| Reconnect | Manual (client handles) | Built-in `EventSource` retry |

**Reasoning:** SSE covers the current telemetry broadcast use case perfectly and with less complexity. WebSocket was chosen to leave the door open for bidirectional features: device command sending, remote configuration, in-browser admin actions. Retrofitting SSE to bidirectional would require a separate REST reverse channel.

**Tradeoff accepted:** WebSocket connections can be blocked by some corporate proxies. SSE over plain HTTP is universally routable.

---

## 7. JWT + Refresh Token vs Session-Based Authentication

**Chosen:** JWT (stateless access token) + opaque refresh token

| Aspect | JWT + Refresh Token | Server-side sessions |
| --- | --- | --- |
| State | Access token stateless; refresh token in DB | All state in session store |
| Horizontal scaling | Any replica validates access token independently | All replicas must share session store |
| Token revocation | Refresh token revocable (row delete); access token lives until expiry | Immediate (delete session) |
| Payload per request | ~300–500 bytes | ~50 bytes (session ID cookie) |

**Reasoning:** The 15-minute access token expiry limits the blast radius of a stolen token — an attacker has at most 15 minutes without needing to hit the refresh endpoint (which would reveal the theft via rotation). Refresh token rotation means using a stolen refresh token immediately invalidates it and logs the legitimate user out, creating a detectable security signal.

**Tradeoff accepted:** Access tokens cannot be revoked before the 15-minute expiry. A Redis-based blocklist would close this gap but adds statefulness.

---

## 8. Declarative Range Partitioning vs TimescaleDB Hypertables

**Chosen:** PostgreSQL native `PARTITION BY RANGE(timestamp)` (V3 migration)

**Reasoning:** Partitioning is a native PostgreSQL 10+ feature with zero additional dependencies. Monthly child tables (`telemetry_2025_01`, etc.) enable:

- Partition pruning on range queries (PostgreSQL eliminates irrelevant child tables automatically)
- Future `ALTER TABLE DETACH PARTITION / DROP TABLE` for cold storage archival without row-level deletes
- Clean separation of hot (current month) and warm (recent months) data

The tradeoff vs TimescaleDB: monthly partitions must be pre-created in migrations and extended before the range is exhausted. TimescaleDB creates chunks automatically. However, TimescaleDB also adds an extension management burden and a different upgrade path. If row counts exceed 500M, the migration to TimescaleDB requires only swapping the Docker image and running `SELECT create_hypertable(...)` — no application code changes.

**Tradeoff accepted:** The current partition range covers 2025–2026. New migrations must be added before December 2026 (see Known Limitations).

---

## 9. Redis List Replay Queue vs Kafka

**Chosen:** Redis List (`RPUSH` / `LPOP`) via `sentinel:replay:queue`

| Aspect | Redis List | Kafka |
| --- | --- | --- |
| Infrastructure | Already present (cache layer) | Separate deployment (Zookeeper or KRaft) |
| Consumer model | Single consumer (ReplayQueueService) | Consumer groups (multiple consumers) |
| At-least-once | Yes (RPUSH + transactional save) | Yes (offsets) |
| Replay / rewind | No (messages consumed once) | Yes (seek to offset) |
| Throughput ceiling | ~100k ops/sec single node | ~1M msgs/sec |
| Max buffered messages | 10,000 (configurable) | Effectively unlimited (disk-backed) |

**Reasoning:** The replay queue has exactly one producer (the `saveFallback` method) and one consumer (`ReplayQueueService`). Kafka's consumer group semantics are unnecessary at this cardinality. A Redis List reuses existing infrastructure with zero additional operational overhead. The 10,000-message cap is appropriate for short DB outages (at 1,000 events/sec, that covers a ~10-second outage window).

**Tradeoff accepted:** There is no replay/rewind capability — once drained, messages are gone. For longer outages, increase `TELEMETRY_REPLAY_MAX_QUEUE`. For multi-replica setups where DB recovery order matters, Kafka is the right answer.

---

## 10. DECOMMISSIONED as a Terminal Lifecycle State

**Chosen:** `DECOMMISSIONED` transitions are rejected at the service layer; the MQTT pipeline routes telemetry from decommissioned devices to the DLQ.

**Reasoning:** `DECOMMISSIONED` signals physical removal of a device. Allowing re-activation would create ambiguity between "temporarily offline" (`INACTIVE`) and "permanently gone" (`DECOMMISSIONED`). Making it terminal:

- Ensures historical telemetry and alerts remain traceable to a known, fixed device identity
- Prevents accidental re-registration from inheriting another device's alert history or firmware records
- Gives operators a clear irreversible action (requires creating a new device record if re-onboarding)

**Tradeoff accepted:** If an operator mistakenly sets a device to `DECOMMISSIONED`, recovery requires a new device registration and re-association of historical data by UUID in any downstream analytics.

---

## 11. ReplayQueueService Calls TelemetryRepository Directly (Bypasses @Retry + @CircuitBreaker)

**Chosen:** `ReplayQueueService` injects `TelemetryRepository` directly, not `TelemetryService`.

**Reasoning:** `TelemetryService.save()` is decorated with `@Retry` (3 attempts, 500ms wait) and `@CircuitBreaker`. Calling it from the replay loop would:

1. Re-enter the retry machinery — up to 3 retries per message, amplifying DB pressure during recovery
2. Potentially re-open the circuit breaker if retries fail, extending the outage window
3. Inflate `sentinel.telemetry.dropped` counters with replay-cycle failures

`ReplayQueueService` checks `cb.getState() == OPEN` before draining — it only runs when the DB is believed healthy. This check makes the retry decoration redundant and potentially harmful.

**Tradeoff accepted:** If the DB becomes unavailable again mid-drain, individual `save()` calls will throw uncaught `DataAccessException`s. Each failed message is caught, logged, and pushed back to the replay queue tail, so no data is lost.

---

## 12. OpenTelemetry + Jaeger vs Zipkin

**Chosen:** OpenTelemetry SDK + Jaeger (OTLP ingest)

| Aspect | OTel → Jaeger | OTel → Zipkin |
| --- | --- | --- |
| Ingest protocol | OTLP (native OTel protocol) | Zipkin JSON / Thrift (requires bridge) |
| Dependency | `opentelemetry-exporter-otlp` | `opentelemetry-exporter-zipkin` |
| Dev storage | Badger (Jaeger built-in) | In-memory (limited) |
| Production storage | Cassandra, Elasticsearch, Badger | Cassandra, Elasticsearch, MySQL |
| UI features | Service map, trace comparison, logs correlation | Basic trace view |
| Grafana datasource | Native Jaeger datasource | Native Zipkin datasource |

**Reasoning:** Jaeger's native OTLP support on port 4318 means the same `opentelemetry-exporter-otlp` dependency works with no Zipkin-specific formatting or bridges. The Badger embedded storage works well for development without needing Cassandra or Elasticsearch. Jaeger's service map view helps visualize the `telemetry.save → PostgreSQL` → `alert.evaluate → LINE Notify` trace paths clearly.

**Tradeoff accepted:** Zipkin is simpler to embed (no separate collector process) and has slightly broader ecosystem adoption for Java applications. Either backend accepts traces from the same OTel SDK.

---

## 13. API Versioning — URL Prefix vs Accept Header vs Query Parameter

**Chosen:** URL prefix (`/api/v1/`) with `ApiVersionFilter`

| Aspect | URL prefix `/v1/` | Accept header `application/vnd.api.v1+json` | Query param `?version=1` |
| --- | --- | --- | --- |
| Discoverability | Obvious in browser, logs, network tabs | Invisible without header inspection | Visible but stripped by some proxies |
| CDN caching | CDN caches `/v1/` and `/v2/` separately | Requires `Vary: Accept` header | Cache-Control must include `Vary` |
| Routing | Simple path-based routing | Content negotiation (complex middleware) | Leaks into query string |
| Breaking change isolation | Separate URL tree per version | Same URL, different serialisation | Same URL, different content |

**Reasoning:** URL prefix is the most explicit and debuggable choice — version is visible in curl output, browser network tabs, and server logs with zero extra tooling. `ApiVersionFilter` adds `API-Version: 1` to every versioned response and emits `Deprecation`, `Sunset: Sat, 01 Jan 2027`, and `Link` headers for any caller still hitting the unversioned `/api/` path, giving a graceful migration window.

**Tradeoff accepted:** Every client must include the version prefix in its base URL. Unversioned paths (`/api/auth/login`) remain functional until 2027-01-01 but receive deprecation headers.

---

## 14. Schema Evolution — Dual Payload Shapes (v1 scalar + v2 dynamic readings)

**Chosen:** Branched schema versions with Avro as the canonical Kafka wire format

| Aspect | v1 (fixed scalar fields) | v2 (dynamic `readings` map) | Avro + Schema Registry |
| --- | --- | --- | --- |
| Supported sensor types | 4 hard-coded | Any `SensorType` enum value | Enforced by schema at publish time |
| Adding a new sensor type | Requires DB migration + code change | Zero code change | New field in Avro schema (BACKWARD-compatible) |
| DB storage | Fixed columns (`temperature`, `humidity`, ...) | `readings JSONB` column | Decoded at ingest, stored as v2 |
| Alert engine | Hard-coded field access | Map lookup by `SensorType` | Same as v2 |

**Reasoning:** Field devices cannot be updated simultaneously. The ingest pipeline branches on `schemaVersion`: v1 payloads decode scalar fields and synthesise a `readings` map for the alert engine; v2 payloads carry the full readings map natively. Avro enforces the schema on every Kafka message; `SchemaCompatibilityService` runs on startup, checks BACKWARD compatibility against the Schema Registry, and aborts startup if any registered schema is incompatible.

**Tradeoff accepted:** Two code paths increase ingest complexity. The v1 path will be removed once all field devices have upgraded to v2 firmware.

---

## 15. KEDA vs Kubernetes HPA for Autoscaling

**Chosen:** KEDA (Kubernetes Event-Driven Autoscaling) with Kafka lag trigger; HPA as fallback when KEDA is disabled

| Aspect | KEDA | HPA |
| --- | --- | --- |
| Scale trigger | Kafka consumer lag (leading indicator) | CPU / memory (lagging indicator) |
| Minimum replicas | 0 (scale-to-zero in staging) | 1 |
| Custom metrics | Native (Kafka, Redis, 60+ scalers) | Requires custom metrics adapter |
| Automated cooldown | Configurable per ScaledObject | 5-minute default |
| Complexity | KEDA operator required | Built-in to Kubernetes |

**Reasoning:** CPU usage is a lagging indicator for a Kafka consumer — a spike in consumer lag is a leading indicator that shows a replica is falling behind before CPU saturates. KEDA's Kafka scaler triggers scale-out when `consumerLag > lagThreshold`, adding replicas before messages accumulate. Scale-to-zero reduces staging cluster costs.

**Tradeoff accepted:** KEDA requires an operator in the cluster. The Helm chart falls back to the standard HPA on CPU when `keda.enabled=false`.

---

## 16. React Query + Zustand vs useState + useEffect for Frontend State

**Chosen:** `@tanstack/react-query` for server state; Zustand for UI client state

| Aspect | React Query + Zustand | useState + useEffect |
| --- | --- | --- |
| Cache invalidation | Automatic (staleTime, refetchOnFocus) | Manual (ref juggling, cleanup) |
| Optimistic updates | Built-in (onMutate / onError rollback) | Complex custom logic |
| Background refetch | Yes (configurable interval) | setInterval in useEffect |
| Request deduplication | Automatic (same queryKey shares one request) | Race condition-prone |
| Global client state | Zustand normalized store (selectedDeviceId, filters) | Context + reducer (prop-drilling) |
| DevTools | React Query Devtools | None |

**Reasoning:** Dashboard panels (devices, alerts, telemetry stats) share cache keys via `queryKey` factories. `invalidateQueries({ queryKey: qk.alerts() })` instantly refreshes every panel that depends on alerts. `useMutation` with `onMutate` gives instant optimistic feedback on alert acknowledgement; `onError` rolls back to the previous snapshot. Zustand holds only derived UI state — no server data is duplicated in client state.

**Tradeoff accepted:** React Query adds ~47 KB to the bundle. `staleTime` and `gcTime` must be tuned per endpoint or stale data will be served after cache expiry.

---

## 17. Argo Rollouts vs Kubernetes Rolling Update for Deployments

**Chosen:** Argo Rollouts with blue/green deployment strategy

| Aspect | Argo Rollouts (Blue/Green) | Kubernetes Rolling Update |
| --- | --- | --- |
| Traffic shift | Instant cutover after health check | Gradual pod replacement |
| Rollback speed | Instant (flip active service selector) | Waits for pod termination + startup |
| Schema migration safety | Old pods fully stopped before new pods take traffic | Old + new pods run simultaneously |
| Canary option | Yes (weight-based traffic splitting) | Requires a service mesh |
| Complexity | Argo Rollouts operator required | Built-in |

**Reasoning:** Flyway schema migrations run in an init container before new pods start. With a rolling update, old pods (expecting the previous schema) and new pods (expecting the new schema) can run simultaneously during the rollout window, risking schema mismatch errors. Blue/green ensures only one schema version is active at any time — old pods are fully replaced before new pods begin serving traffic.

**Tradeoff accepted:** Blue/green requires ~2× compute during the transition window. Canary deployments (gradual weight shift) are available as an alternative for releases with no DB migrations.

---

## 18. CloudNativePG vs Self-Managed PostgreSQL for HA

**Chosen:** CloudNativePG (CNPG) Kubernetes operator

| Aspect | CloudNativePG | Self-managed Postgres + Patroni |
| --- | --- | --- |
| Automatic failover | Yes (<30s via Kubernetes leader election) | Yes (requires etcd / Consul / ZooKeeper) |
| WAL archival / backup | Built-in (S3 / GCS / Azure Blob) | pg_basebackup + custom cron |
| Connection pooling | PgBouncer sidecar (built-in CRD option) | Separate PgBouncer deployment |
| Kubernetes-native | Yes (CRD-based, `status.conditions`) | Manual Helm chart wiring |
| Operational overhead | Low | High (manage Patroni + etcd separately) |

**Reasoning:** The CNPG `Cluster` CRD handles primary election, streaming replication, and WAL archival in a single spec. Spring Data JPA's `AbstractRoutingDataSource` routes `@Transactional(readOnly=true)` calls to the CNPG read service and write transactions to the primary service — no application code changes. Point-in-time recovery is built in, essential for the replicated telemetry data.

**Tradeoff accepted:** CloudNativePG is a newer operator (GA 2023) with a smaller ecosystem than self-managed Patroni. Requires the CNPG operator to be installed in the cluster before deploying the Helm chart.

---

## Summary Table

| Decision | Chosen | Main Alternative | Key Reason |
| --- | --- | --- | --- |
| Frontend | Next.js 14 | Vite + React | API proxy rewrites, Vercel deploy |
| Messaging | MQTT | HTTP Polling | Event-driven, low bandwidth |
| Cache + Queue | Redis | Memcached | Hash structures, List for replay queue, Pub/Sub |
| Database | PostgreSQL | InfluxDB | SQL, JPA, zero extra stack |
| Partitioning | Native RANGE | TimescaleDB | Zero new dependencies, same Postgres |
| MQTT consumer | Spring Integration | Raw Paho | Declarative, DLQ routing |
| Realtime | WebSocket | SSE | Bidirectional for future commands |
| Auth | JWT + Refresh token | Server sessions | Stateless, 15-min blast radius, rotation |
| Replay queue | Redis List | Kafka | Reuses existing infra, single consumer |
| Lifecycle terminal | DECOMMISSIONED blocked | Allow re-activation | Audit integrity, unambiguous removal |
| Replay loop | Direct repository | TelemetryService | Avoid double CB/retry during recovery |
| Tracing backend | Jaeger (OTLP) | Zipkin | Native OTLP, no bridge, Badger dev storage |
| API versioning | URL prefix `/v1/` | Accept header | Visible in logs, CDN cache isolation |
| Schema evolution | v1 scalar + v2 readings + Avro | Single fixed schema | Field device upgrade without downtime |
| Autoscaling | KEDA (Kafka lag) | HPA (CPU) | Leading indicator, scale-to-zero |
| Frontend state | React Query + Zustand | useState + useEffect | Cache invalidation, optimistic updates |
| Deployments | Argo Rollouts blue/green | Rolling update | Schema migration safety, instant rollback |
| HA database | CloudNativePG operator | Patroni + etcd | CRD-native, built-in WAL archival |
