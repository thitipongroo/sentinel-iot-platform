# Architecture

## System Overview

Sentinel IoT Platform is a production-grade industrial monitoring system built around an event-driven architecture. IoT devices publish sensor readings over MQTT every 5 seconds. A Spring Boot backend consumes these messages through a validated 5-stage ingestion pipeline, persists them to a partitioned PostgreSQL table, caches the latest values in Redis, evaluates threshold rules, and broadcasts updates to connected browsers via WebSocket. A Next.js dashboard renders real-time charts and historical analytics without polling.

---

## High-Level Diagram

<!--
![Sentinel IoT Platform High Level Diagram](screenshots/sentinel-high-level-diagram.png)
-->

```text
                    ┌──────────────────────────────────────────────────────────┐
                    │                  Sentinel IoT Platform                   │
                    │                                                          │
 ┌─────────────┐   │  ┌───────────────────────────────────────────────────┐   │
 │ IoT Devices │──▶│  │              Eclipse Mosquitto 2.0                │   │
 │ (sensors)   │   │  │              MQTT Broker                          │   │
 └─────────────┘   │  │  tcp/1883 (devices)  ←── DLQ ──── factory/       │   │
                   │  │                              telemetry/dlq        │   │
 ┌─────────────┐   │  └──────────────────┬──────────────────────────────┘   │
 │  Node.js    │──▶│                     │ subscribe factory/telemetry       │
 │  Simulator  │   │  ┌──────────────────▼──────────────────────────────┐   │
 └─────────────┘   │  │               Spring Boot 3.2 Backend            │   │
                   │  │                                                   │   │
                   │  │  ┌──────────────────────────────────────────┐    │   │
                   │  │  │  MqttConsumerService (Spring Integration) │    │   │
                   │  │  │  ① Parse → ② Validate → ③ Resolve Device  │    │   │
                   │  │  │  ④ Lifecycle Gate → ⑤ Process            │    │   │
                   │  │  │  Failures → mqttDlqChannel → DLQ topic   │    │   │
                   │  │  └──────┬──────────────────┬────────────────┘    │   │
                   │  │         │                  │                      │   │
                   │  │  ┌──────▼──────┐  ┌────────▼──────┐             │   │
                   │  │  │ Telemetry   │  │ AlertService  │             │   │
                   │  │  │ Service     │  │ + LINE Notify │             │   │
                   │  │  │ @Retry+@CB  │  └───────────────┘             │   │
                   │  │  └──────┬──────┘                                │   │
                   │  │         │ DB unavailable → saveFallback()       │   │
                   │  │         │                                        │   │
                   │  │  ┌──────▼──────┐  ┌─────────────────────────┐  │   │
                   │  │  │ Redis 7     │  │ PostgreSQL 16            │  │   │
                   │  │  │ • telemetry │  │ • telemetry (partitioned │  │   │
                   │  │  │   cache     │  │   by month, V3)          │  │   │
                   │  │  │ • replay    │  │ • telemetry_hourly_aggs  │  │   │
                   │  │  │   queue     │  │ • devices (lifecycle)    │  │   │
                   │  │  └─────────────┘  └─────────────────────────┘  │   │
                   │  │         ↑ drained by ReplayQueueService (30s)   │   │
                   │  │                                                   │   │
                   │  │  ┌──────────────────────────────────────────┐    │   │
                   │  │  │  WebSocket Gateway (TelemetryWSHandler)  │    │   │
                   │  │  └──────────────────────────────────────────┘    │   │
                   │  │                                                   │   │
                   │  │  ┌──────────────────────────────────────────┐    │   │
                   │  │  │  RequestIdFilter (MDC: requestId, method, │    │   │
                   │  │  │  path, username, durationMs)             │    │   │
                   │  │  └──────────────────────────────────────────┘    │   │
                   │  └──────────────────┬────────────────────────────┘   │
                   │                     │ REST + WS                       │
                   │  ┌──────────────────▼────────────────────────────┐   │
                   │  │         Next.js 14 Dashboard                   │   │
                   │  │  DeviceList (search + lifecycle badge)          │   │
                   │  │  TelemetryChart (Live/1h/6h/24h/7d)            │   │
                   │  │  AlertList (All/Unacknowledged tabs)            │   │
                   │  │  DeviceManagement (ADMIN: lifecycle + firmware) │   │
                   │  └────────────────────────────────────────────────┘   │
                   │                                                          │
                   │  ┌────────────┐  scrape  ┌──────────┐  traces          │
                   │  │ Prometheus │─────────▶│  Grafana │                  │
                   │  └────────────┘          └──────────┘                  │
                   │  ┌──────────────────┐  ← OTLP (port 4318)             │
                   │  │ Jaeger (OTel)    │  Distributed tracing             │
                   │  └──────────────────┘                                  │
                   └──────────────────────────────────────────────────────────┘
```

---

## Data Flow

### Normal Ingestion Path

<!--
![Sentinel IoT Platform Normal Ingestion Path](screenshots/sentinel-data-flow-normal.png)
-->

```text
Device/Simulator
  │── MQTT publish ──▶ Mosquitto
                          │── Spring Integration channel ──▶ MqttConsumerService
                                                                │── ① JSON parse
                                                                │── ② field validation (range checks)
                                                                │── ③ device resolution (DB lookup)
                                                                │── ④ lifecycle gate (INACTIVE/DECOMMISSIONED → DLQ)
                                                                │── ⑤ TelemetryService.save()  [@Retry + @CircuitBreaker]
                                                                │        │── PostgreSQL INSERT (partitioned telemetry)
                                                                │        └── Redis HSET (latest cache)
                                                                │── AlertService.evaluate()
                                                                │        └── LINE Notify (threshold exceeded)
                                                                └── WebSocket broadcast ──▶ React UI
```

### Failure Paths

<!--
![Sentinel IoT Platform Failure Ingestion Path](screenshots/sentinel-data-flow-failure.png)
-->

```text
Stage ①–④ validation failure or LIFECYCLE_REJECTED:
  MqttConsumerService ──▶ mqttDlqChannel ──▶ factory/telemetry/dlq
                          headers: dlq-error-code, dlq-error-detail, dlq-timestamp

DB unavailable (circuit breaker opens after 5/10 failures):
  TelemetryService.saveFallback()
    │── Redis HSET (dashboard cache stays live)
    └── Redis RPUSH sentinel:replay:queue (up to 10,000 entries)

Replay queue drain (every 30 seconds):
  ReplayQueueService
    │── check circuit breaker state → skip if OPEN
    └── LPOP batchSize entries ──▶ TelemetryRepository.save() ──▶ PostgreSQL
          failures ──▶ RPUSH back to tail of queue
```

---

## Component Descriptions

### Eclipse Mosquitto (MQTT Broker)

- Protocol: MQTT 3.1.1 over TCP port 1883 and WebSocket port 9001
- `allow_anonymous true` for development (swap to password file for production)
- Persistence enabled so retained messages survive restarts
- QoS 1 used by both simulator and backend subscriber (at-least-once delivery)
- Receives DLQ messages on `factory/telemetry/dlq` from backend (outbound adapter)

### Spring Boot Backend

The backend is a single deployable JAR with the following internal layers:

| Layer | Package | Responsibility |
| --- | --- | --- |
| Security | `security/` | JWT filter, BCrypt password encoding |
| Request Correlation | `config/RequestIdFilter` | MDC: requestId, method, path, username, durationMs |
| Config | `config/` | MQTT + DLQ channels, WebSocket, Redis, Security beans |
| Controller | `controller/` | REST endpoints — auth, devices, telemetry, alerts |
| Service | `service/` | TelemetryService, AlertService, RedisService, ReplayQueueService, TelemetryRetentionService, DeviceService |
| Repository | `repository/` | Spring Data JPA — devices, telemetry, hourly aggregates, alerts, users |
| WebSocket | `websocket/` | `CopyOnWriteArrayList` of active sessions, broadcast |
| DTOs | `dto/` | Request/response DTOs, ReplayQueueMessage |

**Resiliency:** `TelemetryService.save()` is decorated with `@Retry(name="telemetryDB")` (3 attempts, 500ms wait) and `@CircuitBreaker(name="telemetryDB")` (opens at 50% failure rate in a 10-call window; waits 30s before HALF_OPEN). The fallback buffers to Redis.

### Redis

Three key spaces:

| Key pattern | Type | TTL | Purpose |
| --- | --- | --- | --- |
| `device:status:{id}` | String | 10 min | Online/Offline status |
| `device:telemetry:{id}` | Hash | 10 min | Latest temperature, humidity, motion, smokePpm, ts |
| `sentinel:replay:queue` | List | none | Buffered telemetry during DB outages (RPUSH/LPOP) |

Sub-millisecond reads from the `device:telemetry` hash power the `/cache` API endpoint that the dashboard polls for the current reading.

### PostgreSQL

Five domain tables:

```text
app_users         devices               telemetry (partitioned by month)
──────────        ──────────────────    ─────────────────────────
id UUID           id UUID               id UUID (UNIQUE INDEX, not PK)
username          name UNIQUE           device_id UUID FK
password          status                temperature DOUBLE
role              description           humidity DOUBLE
                  location              motion BOOLEAN
                  created_at            smoke_ppm DOUBLE
                  last_seen             timestamp TIMESTAMPTZ  ← partition key
                  lifecycle_status
                  firmware_version      telemetry_hourly_aggregates
                  firmware_updated_at   ─────────────────────────────
                                        id UUID PK
alerts                                  device_id UUID FK
──────────────    ──────────────────    hour_bucket TIMESTAMPTZ
id UUID PK        refresh_tokens        temp_avg/min/max DOUBLE
device_id FK                            hum_avg/min/max DOUBLE
level VARCHAR                           smoke_avg/max DOUBLE
message TEXT                            motion_count / sample_count
acknowledged BOOL                       UNIQUE(device_id, hour_bucket)
created_at
```

**Partitioning:** The `telemetry` table uses `PARTITION BY RANGE(timestamp)` with monthly child tables (`telemetry_2025_01` through `telemetry_2026_12`) plus `telemetry_default` for rows outside the range. PostgreSQL partition pruning eliminates child tables from range queries automatically.

**Indexes:** `idx_telemetry_id` (UNIQUE on `id`), `idx_telemetry_device_id`, `idx_telemetry_timestamp`, `idx_telemetry_device_ts` (composite on `device_id, timestamp DESC`).

**Lifecycle:** The `lifecycle_status` column uses a PostgreSQL `VARCHAR` column backed by a Java `DeviceLifecycleStatus` enum (`PROVISIONED`, `ACTIVE`, `INACTIVE`, `DECOMMISSIONED`). `DECOMMISSIONED` is terminal — the service layer rejects further transitions.

### Next.js 14 Dashboard

- **App Router** with `'use client'` boundaries only where browser APIs are needed
- API calls proxied through `next.config.mjs` rewrites (`/api/*` → backend) — no CORS configuration required
- WebSocket connects directly to backend port 8080 via `NEXT_PUBLIC_WS_URL`
- Auto-reconnects with 3-second backoff on disconnect
- **DeviceList**: search/filter by name or location; lifecycle status badge; firmware version
- **TelemetryChart**: Live / 1h / 6h / 24h / 7d time window selector; 24h and 7d use hourly aggregates with shaded min/max bands
- **AlertList**: All / Unacknowledged filter tabs
- **StatsBar**: 6 tiles including Buffered (replay queue depth, orange when > 0)
- **DeviceManagement**: ADMIN-only panel for lifecycle transitions and firmware version updates

### Observability Stack

**Prometheus + Grafana:** Prometheus scrapes `/actuator/prometheus` every 15 seconds. Key metrics:

| Metric | Type | Description |
| --- | --- | --- |
| `sentinel_telemetry_received_total` | Counter | MQTT messages ingested successfully |
| `sentinel_telemetry_dropped_total` | Counter | Messages buffered to replay queue (DB unavailable) |
| `sentinel_mqtt_messages_total` | Counter | All MQTT messages received (before validation) |
| `sentinel_mqtt_dlq_total` | Counter | Messages routed to DLQ |
| `sentinel_replay_queue_size` | Gauge | Current replay queue depth in Redis |
| `sentinel_replay_success_total` | Counter | Messages successfully replayed from queue |
| `sentinel_replay_failure_total` | Counter | Replay failures (re-queued) |
| `resilience4j_circuitbreaker_state` | Gauge | CB state (0=CLOSED, 1=OPEN, 2=HALF_OPEN) |
| `http_server_requests_seconds` | Histogram | Latency per endpoint |

**Jaeger (Distributed Tracing):** All requests are traced end-to-end via OpenTelemetry → Jaeger (OTLP port 4318, UI port 16686). Custom spans:

- `telemetry.save` tagged with `device.id` — covers DB write + Redis update + CB overhead
- `alert.evaluate` tagged with `device.id` and `device.name` — covers threshold check + LINE Notify call

The `traceId` and `spanId` are injected into MDC via Micrometer Tracing so every JSON log line can be correlated with its Jaeger trace.

**Structured Logging:** `logback-spring.xml` selects by profile:

- `prod`: Logstash JSON encoder — fields: `requestId`, `method`, `path`, `username`, `durationMs`, `traceId`, `spanId`
- default: human-readable console with `[requestId]` in the pattern

---

## Sensor Data Schema

Each MQTT message on `factory/telemetry` is a JSON object:

```json
{
  "deviceId": "sensor-1",
  "temperature": 72.4,
  "humidity": 58.2,
  "motion": false,
  "smokePpm": 12.5,
  "timestamp": 1717200000000
}
```

Validation rules enforced by `MqttConsumerService` before any DB/cache write:

| Field | Type | Valid range | Alert threshold |
| --- | --- | --- | --- |
| `deviceId` | String | Not null/blank | — |
| `temperature` | Double | -40 to 200 °C | > 80 °C → CRITICAL |
| `humidity` | Double | 0 to 100 % | > 90 % → WARNING |
| `motion` | Boolean | true / false | true + temp > 70 °C → WARNING |
| `smokePpm` | Double | ≥ 0 ppm | > 200 ppm → CRITICAL |

---

## Telemetry Retention

The `TelemetryRetentionService` runs at 02:30 daily:

1. **Aggregate**: Upserts `telemetry_hourly_aggregates` for the previous day using a native SQL `INSERT ... SELECT ... ON CONFLICT DO UPDATE` — idempotent, safe to re-run.
2. **Prune**: Deletes raw rows older than `TELEMETRY_RETENTION_DAYS` (default 30).

Hourly aggregates have no expiry — they are the long-term analytics record. The dashboard's 24h and 7d chart modes read from this table.

---

## Deployment Topology

<!--
![Sentinel IoT Platform Deployment Topology Diagram](screenshots/sentinel-deployment-topology.png)
-->

```text
Internet
    │
    ▼
┌──────────────────┐
│  Vercel CDN      │  ← Next.js frontend (static + SSR)
└────────┬─────────┘
         │ HTTPS /api/*  (rewritten to backend)
         │ WSS /ws/telemetry (direct to backend)
         ▼
┌──────────────────┐
│  Railway/Render  │  ← Spring Boot backend (stateless, scalable)
│  (backend)       │
└────────┬─────────┘
         │
    ┌────┴──────┬──────────┬────────────┬────────────┐
    ▼           ▼          ▼            ▼            ▼
Supabase    Upstash    HiveMQ      Docker VM     Jaeger
(Postgres)  (Redis)    (MQTT)   (Prometheus    (traces)
                                 + Grafana)
```
