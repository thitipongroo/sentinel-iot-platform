# ⚡ Sentinel IoT Platform

[![CI](https://github.com/your-github-username/sentinel-iot-platform/actions/workflows/ci.yml/badge.svg)](https://github.com/your-github-username/sentinel-iot-platform/actions/workflows/ci.yml)
[![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.2-green?logo=springboot)](https://spring.io/projects/spring-boot)
[![Next.js](https://img.shields.io/badge/Next.js-14-black?logo=nextdotjs)](https://nextjs.org/)
[![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?logo=docker)](https://docs.docker.com/compose/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

**Production-grade Industrial IoT Monitoring Platform** — real-time sensor data ingestion via MQTT, threshold alerting, LINE Notify integration, WebSocket dashboard, and full observability stack.

> Cache read path sustains **1,000 req/s** (60,000+ ops/min) — observed p95 **112 ms**, p99 **187 ms** under k6 load test (SLO targets: p95 < 200 ms, p99 < 500 ms) — MacBook Pro M3, 16 GB RAM, Docker Compose.

---

## Architecture Diagram

<!-- 
![Sentinel IoT Platform Architecture](docs/screenshots/sentinel-architecture-diagram.png)
-->

```text
┌───────────────────────────────────────────────────────────────────────────────┐
│                            Sentinel IoT Platform                              │
│                                                                               │
│  ┌──────────────┐    MQTT        ┌──────────────────┐                         │
│  │ IoT Devices  │───────────────▶│ Eclipse Mosquitto│                         │
│  │  (sensors)   │  factory/      │   MQTT Broker    │◀── DLQ ── factory/      │
│  └──────────────┘  telemetry     └───────┬──────────┘        telemetry/dlq    │
│                                          │ subscribe                          │
│  ┌──────────────┐              ┌─────────▼───────────┐    ┌─────────────────┐ │
│  │  Simulator   │── MQTT ─────▶│   Spring Boot       │──▶ │ Redis 7         │ │
│  │  (Node.js)   │              │   Backend           │    │ • Latest cache  │ │
│  └──────────────┘              │                     │    │ • Replay queue  │ │
│                                │  • JWT Auth         │    └─────────────────┘ │
│  ┌──────────────┐  REST/WS     │  • MQTT Consumer    │                        │
│  │  Next.js     │◀────────────▶│    + DLQ routing    │    ┌─────────────────┐ │
│  │  Dashboard   │              │  • Alert Engine     │──▶ │ PostgreSQL 16   │ │
│  └──────────────┘              │  • WebSocket GW     │    │ • Partitioned   │ │
│                                │  • Retry + CB       │    │   by month      │ │
│  ┌──────────────┐              │  • Replay Queue     │    │ • Hourly aggs   │ │
│  │   Grafana    │◀── scrape ───│  • Prometheus       │    └─────────────────┘ │
│  │  +Jaeger UI  │              └──────────┬──────────┘                        │
│  └──────────────┘                         │ OTLP traces                       │
│                                  ┌────────▼────────┐                          │
│  ┌──────────────┐                │ Jaeger (OTel)   │                          │
│  │ LINE Notify  │◀── webhook ─── │ Distributed     │                          │
│  └──────────────┘                │ Tracing         │                          │
│                                  └─────────────────┘                          │
└───────────────────────────────────────────────────────────────────────────────┘
```

### Data Flow — Normal Path

<!-- 
![Normal Ingestion Data Flow](docs/screenshots/sentinel-dataflow-normal-path.png)
-->

```text
Device/Simulator
  │── MQTT publish ──▶ Mosquitto
                          │── Spring Integration ──▶ MqttConsumerService
                                                          │── validate payload
                                                          │── resolve device (lifecycle gate)
                                                          │── TelemetryService.save()
                                                          │        │── PostgreSQL (retry + CB)
                                                          │        └── Redis cache (setLatestTelemetry)
                                                          │── AlertService.evaluate()
                                                          │        └── LINE Notify (if threshold exceeded)
                                                          └── WebSocket broadcast ──▶ React UI
```

### Data Flow — Failure Paths

<!--
![Failure Ingestion Data Flow](docs/screenshots/sentinel-dataflow-failure-path.png)
-->

```text
DB unavailable (circuit breaker OPEN):
  TelemetryService.saveFallback()
     │── Redis cache updated (dashboard stays live)
     └── Redis replay queue (RPUSH)  ←── drained every 30s by ReplayQueueService
                                              once circuit breaker recovers

Invalid MQTT payload / unknown device:
  MqttConsumerService
     └── mqttDlqChannel ──▶ factory/telemetry/dlq
           headers: dlq-error-code, dlq-error-detail, dlq-timestamp
```

---

## Tech Stack

<!--
![Sentinel Tech Stack](docs/screenshots/sentinel-tech-stack.png)
-->

| Layer           | Technology                                                                  |
|-----------------|-----------------------------------------------------------------------------|
| Backend         | Spring Boot 3.2, Java 21                                                    |
| Security        | Spring Security + JWT (jjwt 0.12), Bucket4j rate limiting, ApiVersionFilter |
| Messaging       | Eclipse Mosquitto MQTT + Spring Integration + Apache Kafka (KRaft)          |
| Schema Registry | Apache Avro + Confluent Schema Registry (BACKWARD compatibility enforcement)|
| Database        | PostgreSQL 16 + Spring Data JPA + Flyway + range partitioning               |
| HA Database     | CloudNativePG (1 primary + hot-standby, Barman WAL backup)                  |
| Cache           | Redis 7 (Lettuce) — latest value cache + replay queue + WS pub/sub          |
| Resiliency      | Resilience4j CircuitBreaker + Retry                                         |
| Realtime        | WebSocket (native Spring WS) + Redis pub/sub cross-replica fan-out          |
| Frontend        | Next.js 14 (App Router) + Tailwind CSS + Recharts                           |
| State mgmt      | React Query (@tanstack/react-query) + Zustand (normalised client state)     |
| UI performance  | @tanstack/react-virtual (virtualised table, 10 000+ devices)                |
| Design system   | Badge, Select, ErrorBoundary, OfflineBanner primitives                      |
| Observability   | Prometheus + Grafana + OTel/Micrometer + Jaeger                             |
| SLO             | Multi-window burn-rate rules + Grafana SLO dashboard                        |
| Logging         | Logstash-logback-encoder (JSON) + MDC request correlation                   |
| Testing         | JUnit 5, Testcontainers, schemathesis (contract fuzzing)                    |
| Load Test       | k6                                                                          |
| CI/CD           | GitHub Actions (ci.yml + api-contract.yml)                                  |
| Infra (local)   | Docker Compose                                                              |
| Infra (cloud)   | Kubernetes (EKS) via Helm + ArgoCD + Terraform (EKS/RDS/ElastiCache/MSK)    |
| Deployment      | Argo Rollouts (blue/green + canary), KEDA (Kafka-lag autoscaling)           |
| Backup/DR       | Velero (namespace backup) + pg_dump CronJob + DR restore script             |
| Notify          | Multi-provider: LINE Notify (deprecated), Slack webhook, generic webhook    |

---

## Quick Start

### Prerequisites

- Docker + Docker Compose v2
- (Optional) JDK 21 and Node 20 for local dev

### Run the full stack

```bash
git clone https://github.com/your-github-username/sentinel-iot-platform.git
cd sentinel-iot-platform
cp .env.example .env        # fill in JWT_SECRET, INIT_ADMIN_PASSWORD, INIT_OPERATOR_PASSWORD
docker compose up --build                          # core stack (fast local dev)
docker compose --profile observability up --build  # + Prometheus / Grafana / Jaeger
docker compose --profile full up --build           # everything
```

#### Compose profiles

| Profile | Services included |
|---|---|
| _(none)_ | postgres, redis, mosquitto, kafka, backend, frontend, simulator |
| `observability` | above + Prometheus, Grafana, Jaeger |
| `full` | all services |

| Service       | URL                                    |
|---------------|----------------------------------------|
| Dashboard     | <http://localhost:3000>                |
| Backend API   | <http://localhost:8080/api/v1>         |
| Swagger UI    | <http://localhost:8080/swagger-ui.html>|
| Prometheus    | <http://localhost:9090>                |
| Grafana       | <http://localhost:3001>                |
| Jaeger UI     | <http://localhost:16686>               |
| MQTT Broker   | `tcp://localhost:1883`                 |

**First-run credentials:**

Set `INIT_ADMIN_PASSWORD` and `INIT_OPERATOR_PASSWORD` in `.env` before the first `docker compose up` — the backend seeds the accounts on startup if they don't exist. No password defaults are provided; leaving these blank skips account creation (you will need to create users manually via a database client or migration).

- Dashboard: `admin` / _(value of `INIT_ADMIN_PASSWORD`)_
- Grafana: `admin` / _(value of `GRAFANA_PASSWORD`, default `changeme` — change before any internet-facing deployment)_

---

## API Reference

All API endpoints are versioned under `/api/v1/`. Responses always include an `API-Version: 1` header. See [`docs/api.md`](docs/api.md) for the full reference.

### Authentication

```http
POST /api/v1/auth/login
Content-Type: application/json

{ "username": "admin", "password": "<value of INIT_ADMIN_PASSWORD>" }

→ 200 { "accessToken": "eyJ...", "refreshToken": null, "role": "ADMIN", "username": "admin" }
     Set-Cookie: refreshToken=<token>; HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth; Max-Age=604800

POST /api/v1/auth/refresh         # Refresh token is read from HttpOnly cookie — no request body required
Cookie: refreshToken=<token>

POST /api/v1/auth/logout          # Revokes access token (Redis JTI blocklist) + all refresh tokens; clears cookie
Authorization: Bearer <accessToken>
```

> Tokens: access token expires in **15 minutes** (stored in JS memory, never in localStorage); refresh token expires in **7 days** with automatic rotation on every use — delivered as an `HttpOnly; Secure; SameSite=Strict` cookie, not in the response body.

### Devices

```http
POST   /api/v1/devices                                # ADMIN only
GET    /api/v1/devices                                # ADMIN + OPERATOR
GET    /api/v1/devices/{id}                           # ADMIN + OPERATOR
PATCH  /api/v1/devices/{id}/lifecycle                 # ADMIN only
PATCH  /api/v1/devices/{id}/firmware                  # ADMIN only
GET    /api/v1/devices/{id}/capabilities              # ADMIN + OPERATOR
PUT    /api/v1/devices/{id}/capabilities              # ADMIN only — per-sensor thresholds
POST   /api/v1/devices/{id}/enrollment-token          # ADMIN only — one-time token (24 h TTL)
POST   /api/v1/devices/enroll                         # Unauthenticated — device bootstrap
```

Valid lifecycle transitions: `PROVISIONED → ACTIVE → INACTIVE → DECOMMISSIONED`.  
`DECOMMISSIONED` is terminal — no further transitions accepted (HTTP 409).

**Device enrollment flow:** Admin calls `POST /devices/{id}/enrollment-token` → receives a single-use 256-bit token → delivers it out-of-band to the physical device → device calls `POST /devices/enroll` with the token → receives MQTT credentials + transitions to `ACTIVE`. The DB stores only the SHA-256 hash; replay or DB breach cannot recover the raw token.

### Telemetry

```http
GET /api/v1/telemetry/{deviceId}/latest?limit=50   # Last N raw rows (max 200)
GET /api/v1/telemetry/{deviceId}/cache             # Sub-ms Redis read
GET /api/v1/telemetry/{deviceId}/range?from=…&to=… # ISO-8601 time range
GET /api/v1/telemetry/{deviceId}/hourly?from=…&to=…# Hourly aggregates
GET /api/v1/telemetry/stats                        # { lastMinute, replayQueueSize }
```

Telemetry rows support two payload generations: `schemaVersion=1` (fixed scalar fields — `temperature` and `humidity` required) and `schemaVersion=2` (dynamic `readings` map + `edge` metadata — `temperature` and `humidity` columns are nullable in the DB). Both versions are handled transparently by the ingest pipeline.

### Alerts

```http
GET /api/v1/alerts
GET /api/v1/alerts/unacknowledged
PUT /api/v1/alerts/{id}/acknowledge    # ADMIN only
```

### WebSocket

```text
WS ws://localhost:8080/ws/telemetry?token=<accessToken>

Payload (JSON, per message):
{
  "deviceId": "sensor-1",
  "temperature": 72.4,
  "humidity": 58.2,
  "motion": false,
  "smokePpm": 12.5,
  "timestamp": 1717200000000
}
```

The `?token=<accessToken>` query parameter is required — the handshake is rejected (HTTP 401) without a valid JWT. Each connected session only receives telemetry for devices belonging to the authenticated user's organization (tenant-filtered broadcast).

---

## Threshold Rules

**Per-device capability thresholds (preferred):** Each device can carry a `capabilities` map (stored as JSONB) declaring per-sensor `warnThreshold`, `critThreshold`, and `ThresholdDirection` (ABOVE/BELOW). The alert engine uses these when present.

**Global fallback (legacy):** When a device has no capabilities configured, global environment variable thresholds apply:

| Variable              | Default | Description                                           |
|-----------------------|---------|-------------------------------------------------------|
| `TEMP_THRESHOLD`      | `80`    | °C — triggers CRITICAL alert                          |
| `SMOKE_THRESHOLD`     | `200`   | ppm — triggers CRITICAL alert + LINE Notify           |
| `HUMIDITY_THRESHOLD`  | `90`    | % — triggers WARNING alert                            |

Motion + elevated temperature (>70°C) also triggers a WARNING alert under the legacy engine.

---

## Failure Scenarios

### Database unavailable

The Resilience4j CircuitBreaker (`telemetryDB`) trips OPEN after **5 failures in a 10-call sliding window** (50% failure rate threshold). While OPEN:

- `TelemetryService.saveFallback()` is invoked instead.
- The Redis cache is updated — the dashboard continues showing live readings.
- The raw telemetry is serialized to JSON and pushed to the Redis replay queue (`sentinel:replay:queue`, max 10,000 entries).
- `ReplayQueueService` runs every 30 seconds. It checks the CB state first: if still OPEN, the drain is skipped entirely (no retry storm). Once the CB enters HALF_OPEN and then CLOSED, the queue drains in batches of 100, persisting to PostgreSQL. Failed entries are pushed to the back of the queue for the next cycle.
- **Zero telemetry loss** unless the queue overflows (configurable via `TELEMETRY_REPLAY_MAX_QUEUE`).

### Redis unavailable

Redis calls time out after 2 seconds (configurable via `spring.data.redis.timeout`). The timeout bubbles up as a `DataAccessException` and is swallowed at the service layer — PostgreSQL writes continue unaffected. The replay queue is Redis-backed, so offline-recovery buffering also stops during Redis downtime, but direct DB persistence still works.

### MQTT broker disconnection

Spring Integration's `MqttPahoMessageDrivenChannelAdapter` auto-reconnects with a built-in backoff. With QoS 1, the broker retains undelivered messages for connected subscribers and redelivers them on reconnect. In-flight messages that never reached the broker before disconnection are retransmitted by the device (QoS 1 guarantees at-least-once delivery).

### Invalid or malformed MQTT payload

The 5-stage ingestion pipeline in `MqttConsumerService` validates before any DB/cache write:

| Stage | Error code | Condition |
| --- | --- | --- |
| JSON parse | `PARSE_ERROR` | Payload is not valid JSON |
| Field validation | `VALIDATION_ERROR` | `deviceId` null; temp outside [-40,200]; humidity outside [0,100]; smokePpm < 0 |
| Device resolution | `UNKNOWN_DEVICE` | `deviceId` not found in DB |
| Lifecycle gate | `LIFECYCLE_REJECTED` | Device is `INACTIVE` or `DECOMMISSIONED` |
| Processing error | `PROCESSING_ERROR` | Unexpected exception in save/alert/broadcast |

Rejected messages are routed to `factory/telemetry/dlq` with DLQ headers (`dlq-error-code`, `dlq-error-detail`, `dlq-timestamp`). The main ingestion channel is never blocked. `sentinel_mqtt_dlq_total` counter tracks DLQ volume in Prometheus.

### Circuit breaker OPEN during replay

`ReplayQueueService` explicitly checks `cb.getState() == OPEN` before draining. If OPEN, the entire drain cycle is skipped and logged at DEBUG level. This prevents the replay job from amplifying DB pressure during an outage and triggering more CB trips.

---

## Security

| Feature | Implementation |
| --- | --- |
| Authentication | JWT (15 min access token, stored in JS module-level variable — never localStorage) + opaque refresh token (7 days, DB-persisted as **SHA-256 hash**, rotated on every use, delivered as `HttpOnly; Secure; SameSite=Strict` cookie — never in response body) |
| Access Token Revocation | `POST /auth/logout` adds the token's JTI to a Redis blocklist on DB 1 (TTL = remaining token lifetime). Every request checks the blocklist — stolen or logged-out tokens are rejected immediately. |
| Zero-Downtime Key Rotation | Set `JWT_PREVIOUS_SECRET=<old>` + `JWT_SECRET=<new>` and redeploy. Tokens signed with the old key remain valid until they expire (max 15 min); old `JWT_PREVIOUS_SECRET` can be cleared after that. |
| Refresh Token Reuse Detection | `rotateRefreshToken()` calls `revokeAllByUsername()` when a revoked token is presented — token family invalidation per RFC 6819. |
| Device Enrollment | One-time 256-bit SecureRandom tokens; only SHA-256 hash stored in DB; single-use; TTL-bound (default 24 h); bound to a specific device ID. Devices bootstrap via `POST /devices/enroll` (unauthenticated — token is the credential) and receive per-device MQTT credentials. |
| Rate Limiting | Bucket4j — tiered limits per IP: **10 req/min** for auth endpoints (`/api/v1/auth/*`), **100 req/min** for all other API endpoints. X-Forwarded-For trusted only from configured proxy IPs (`rate-limit.trusted-proxies`). In-process buckets — see Known Limitations. |
| RBAC | `ADMIN` + `OPERATOR` roles; method-level `@PreAuthorize` |
| CORS | Restricted to `CORS_ALLOWED_ORIGINS` env var (default: `http://localhost:3000`). Headers limited to `Authorization`, `Content-Type`, `X-Request-ID`. |
| CSRF | Disabled — correct for a stateless JWT API. |
| Secret Management | `JWT_SECRET` required at runtime — no default, no fallback. **Production upgrade path:** inject via HashiCorp Vault (`spring-cloud-vault`) or AWS Secrets Manager. |
| Audit Logging | Every auth event, alert acknowledgement, device mutation, and enrollment event persisted to `audit_logs` with username + IP. |
| Audit Retention | `audit_logs` purged daily at 03:30 (`AUDIT_RETENTION_DAYS`, default 90 days). |
| MQTT Auth | `allow_anonymous false` enforced. Per-device accounts via `MQTT_DEVICE_CREDENTIALS=sensor-1:pass1,sensor-2:pass2`. |
| MQTT TLS / mTLS | TLS on `:8883` when certs present. `MQTT_TLS_REQUIRED=true` removes plaintext `:1883`. `MQTT_MTLS_ENABLED=true` requires client certificates. |
| Multi-tenant Isolation | `organizationId` scoping + PostgreSQL Row Level Security (`V7__row_level_security.sql`) enforced by `TenantRlsAspect` (Spring AOP `@Before` on every `@Transactional` method — issues `SET LOCAL app.org_id` inside the transaction) + tenant-namespaced Redis keys (`device:{orgId}:{deviceId}`) |
| Secret Scanning | Gitleaks runs on every CI push — secrets committed to git fail the build |
| Dependency/Container Scan | Trivy scans filesystem (SCA) and backend container image on every CI push — CRITICAL/HIGH CVEs fail the build. Results in GitHub Security tab (SARIF) |
| Actuator Exposure | `/actuator/health` public; internal details shown only to authenticated users. |
| Request Correlation | `X-Request-ID` echoed; `requestId`, `method`, `path`, `username`, `durationMs` in MDC per log line |

### Known Security Limitations (remaining upgrade path)

| Gap | Current State | Production Fix |
| --- | --- | --- |
| Rate limiting is in-process | Each replica has independent bucket — effective limit is `10/100 × N` replicas | Swap `ConcurrentHashMap` for `bucket4j-redis` (`ProxyManager` backed by Redis atomic counters) |
| No refresh-token device binding | Refresh tokens bound to user only, not to issuing device or IP | Add fingerprint (IP + User-Agent hash) on issuance; reject reuse from different fingerprint |
| TLS MQTT is opt-in | Plaintext `:1883` active by default in dev | Set `MQTT_TLS_REQUIRED=true` after running `gen-mqtt-certs.sh`; enforce in production via Helm `values-prod.yaml` |
| mTLS is opt-in | `require_certificate false` unless `MQTT_MTLS_ENABLED=true` | Run `gen-mqtt-certs.sh --with-client-certs`, mount client certs into each service, set `MQTT_MTLS_ENABLED=true` |

---

## Observability

### Metrics

Prometheus scrapes `/actuator/prometheus` every 15s.

| Metric                              | Description                                                  |
|-------------------------------------|--------------------------------------------------------------|
| `sentinel_telemetry_received_total`  | Total MQTT messages ingested successfully                         |
| `sentinel_telemetry_dropped_total`   | Messages buffered to replay queue due to DB unavailability        |
| `sentinel_mqtt_messages_total`       | All MQTT messages received (before validation)                    |
| `sentinel_mqtt_dlq_total`            | Messages routed to DLQ (invalid payload / unknown device)         |
| `sentinel_mqtt_load_shed`            | Messages shed at ingestion concurrency limit (Counter)            |
| `sentinel_mqtt_active_permits`       | Current in-flight MQTT processing count (Gauge)                   |
| `sentinel_replay_queue_size`         | Current depth of the Redis replay queue (Gauge)                   |
| `sentinel_replay_success_total`      | Messages successfully replayed from queue to DB                   |
| `sentinel_replay_failure_total`      | Replay failures (re-queued for next cycle)                        |
| `sentinel.business.active_devices`  | Devices currently ONLINE (Gauge, refreshed every 30s)             |
| `sentinel.business.total_devices`   | Total registered devices (Gauge)                                  |
| `sentinel.business.unack_alerts`    | Unacknowledged alert count (Gauge)                                |
| `sentinel.business.alert_fired`     | Alerts fired since startup (Counter)                              |
| `http_server_requests_*`             | Request latency histogram (Spring Boot auto-instrumentation)      |
| `resilience4j_circuitbreaker_*`      | CB state, call counts, failure rates                              |
| `jvm_memory_used_bytes`              | JVM heap usage                                                    |

Import `monitoring/grafana/dashboard.json` into Grafana for the pre-built dashboard.

### Distributed Tracing

Every request is traced end-to-end via OpenTelemetry → Jaeger. Custom spans added:

- `telemetry.save` (tagged with `device.id`) — covers the full DB write + Redis cache update + CB overhead
- `alert.evaluate` (tagged with `device.id`, `device.name`) — covers threshold check + LINE Notify call

The `traceId` and `spanId` are injected into MDC via Micrometer Tracing, so every JSON log line is correlated with its Jaeger trace. Access traces at <http://localhost:16686>.

### Structured Logging

- **Non-prod profile**: human-readable console output including `[requestId]` in the pattern.
- **Prod profile**: Logstash JSON encoder with fields: `requestId`, `method`, `path`, `username`, `durationMs`, `traceId`, `spanId`. Suitable for ingestion into Elasticsearch / Loki.

---

## Load Testing

### Methodology

**Script:** `load-testing/telemetry.js` (k6)  
**Endpoint:** `GET /api/telemetry/{deviceId}/cache` — the Redis-backed hot read path used by the dashboard  
**Hardware:** MacBook Pro M3, 16 GB RAM, Docker Compose (no resource limits set)  
**Scenario:** `ramping-arrival-rate` — 10 → 1,000 req/s over 5 minutes, sustained at 1,000 req/s for 2 minutes  
**Pass thresholds:** p95 < 200ms, p99 < 500ms, success rate > 95%

> **Note:** k6 cannot drive MQTT traffic directly. This test measures the HTTP read path (Redis → Spring Boot → HTTP). MQTT ingestion throughput is exercised separately by the Node.js simulator (`simulator/`), which publishes at a configurable interval across N devices.

### Running the test

```bash
# Prerequisites: k6 (brew install k6), full stack running
docker compose up -d
k6 run load-testing/telemetry.js --env BASE_URL=http://localhost:8080
# Results written to load-testing/results.json
```

### Representative results (MacBook Pro M3, 16 GB RAM)

```text
  http_reqs............: 180,432  (1,003 req/s peak, 601 req/s avg)
  http_req_duration....: avg=48ms   p(95)=112ms   p(99)=187ms
  success_rate.........: 99.7%
  failed_requests......: 0.3%

  Peak: 1,003 req/s → 60,180 read ops/min  (observed p95=112ms; SLO target: p95 < 200ms)
```

### Observed bottleneck

HikariCP pool size defaults to 10. At 1,000 req/s, connection contention elevates p99. Increasing `spring.datasource.hikari.maximum-pool-size` to 20–30 extends the linear region before the DB connection pool becomes the limit.

---

## Running Tests

### Backend unit tests

```bash
cd backend
mvn test -Dtest="*Test"
```

### Backend integration tests (requires Docker)

```bash
cd backend
mvn verify -Dtest="*IntegrationTest"
```

All integration tests extend `BaseIntegrationTest`, which spins up Postgres, Redis, and Mosquitto via Testcontainers and wires connection properties via `@DynamicPropertySource`. No pre-existing local services required.

### Frontend E2E (Cypress)

```bash
cd frontend
npm install
npm run test       # headless
npx cypress open   # interactive
```

### Load test

```bash
# Install k6: brew install k6
k6 run load-testing/telemetry.js --env BASE_URL=http://localhost:8080
```

---

## Telemetry Retention

Raw telemetry is retained for 30 days (configurable via `TELEMETRY_RETENTION_DAYS`). The retention cron runs at 02:30 daily in three phases:

1. **Aggregate with late-arrival look-back**: `telemetry_hourly_aggregates` is upserted for the window `[today − lateArrivalLookbackDays, today)` (default: 2 days). Using `ON CONFLICT DO UPDATE` makes it idempotent, so late IoT messages are retroactively folded into the correct buckets on the next nightly run.
2. **Prune**: Raw rows older than the retention window are deleted from the partitioned `telemetry` table.
3. **Drop old partitions**: Empty monthly child tables (e.g. `telemetry_2025_01`) past the retention window are detached and dropped automatically, keeping the partition catalog from growing unbounded.

The dashboard's historical analytics mode uses the hourly aggregates for 24h and 7d windows, showing shaded min/max bands around the average line.

---

## Device Lifecycle

Devices follow a linear state machine: `PROVISIONED → ACTIVE → INACTIVE → DECOMMISSIONED`. Rules enforced in `DeviceService`:

- `DECOMMISSIONED` is terminal — no further transitions are accepted (HTTP 409).
- Transitioning to `INACTIVE` or `DECOMMISSIONED` forces `status = OFFLINE`.
- The MQTT ingestion pipeline rejects telemetry from `INACTIVE` or `DECOMMISSIONED` devices (routed to DLQ with code `LIFECYCLE_REJECTED`).
- Firmware version updates are rejected for decommissioned devices.

---

## CI/CD

GitHub Actions runs on every push and PR:

1. **Security scan** — Gitleaks (full git history, blocks on any secret found) + Trivy filesystem scan (SCA, SARIF → GitHub Security tab) + Trivy container image scan (CRITICAL/HIGH CVEs fail the build)
2. **Backend** — Checkstyle → unit tests → integration tests (Testcontainers, real Postgres + Redis + Mosquitto)
3. **Frontend** — ESLint → Next.js build
4. **Docker** — `docker compose config` validation (with `JWT_SECRET` placeholder) → parallel image build

All CI steps are hard-fails — no `|| true` overrides. A red build means a real problem.

---

## Notification Setup

The platform supports multiple notification providers. Enable exactly one (or none) per deployment.

### Slack (recommended)

```bash
# Create an Incoming Webhook at https://api.slack.com/messaging/webhooks
SLACK_WEBHOOK_URL=https://hooks.slack.com/services/...
SLACK_NOTIFY_ENABLED=true
```

### Generic Webhook (PagerDuty, Opsgenie, Teams, etc.)

```bash
NOTIFY_WEBHOOK_URL=https://your-endpoint/alert
NOTIFY_WEBHOOK_ENABLED=true
NOTIFY_WEBHOOK_SECRET=your-hmac-secret   # optional — signs payload with HMAC-SHA256
```

### LINE Notify (deprecated)

```bash
# Get token at https://notify-bot.line.me/my/
LINE_NOTIFY_TOKEN=your_token
LINE_NOTIFY_ENABLED=true
```

> **Warning:** LINE Notify was shut down on **March 31, 2025**. Tokens no longer work. Migrate to Slack webhook or the generic webhook provider.

---

## Device Simulator

The Node.js simulator publishes 4-sensor telemetry every 5 seconds per device with randomised spikes:

| Sensor        | Normal range | Spike condition        | Rate |
|---------------|--------------|------------------------|------|
| `temperature` | 60–78 °C     | 81–95 °C (CRITICAL)    | 5%   |
| `humidity`    | 35–85 %      | —                      | —    |
| `motion`      | false        | true (detected)        | 20%  |
| `smokePpm`    | 5–50 ppm     | 201–350 ppm (CRITICAL) | 3%   |

```bash
cd simulator
npm install
MQTT_BROKER=mqtt://localhost:1883 DEVICES=sensor-1,sensor-2 node index.js
```

---

## Deployment

| Service    | Platform           | Notes                                   |
|------------|--------------------|-----------------------------------------|
| Frontend   | Vercel             | `vercel --prod` from `frontend/`        |
| Backend    | Railway / Render   | Set env vars, port 8080                 |
| PostgreSQL | Railway / Supabase | Managed Postgres                        |
| Redis      | Upstash            | Serverless Redis (free tier works)      |
| MQTT       | HiveMQ Cloud       | Free tier: 100 connections              |
| Monitoring | Docker VM (VPS)    | `docker compose up prometheus grafana`  |

---

## Infrastructure Ownership

| Tool | Responsibility | Owner |
|---|---|---|
| Terraform | Cloud resource provisioning (EKS, RDS, ElastiCache, MSK) | Platform/Infra team |
| Helm | Application templating + Kubernetes manifests | App team |
| ArgoCD | GitOps deployment sync — pulls from Git and reconciles Helm releases | Platform/Infra team |
| Argo Rollouts | Blue/green and canary deployment strategies | App team |
| KEDA | Kafka-lag-based horizontal pod autoscaling | Platform/Infra team |

Lock all tool versions in `infra/terraform/versions.tf` and `infra/helm/sentinel-iot/Chart.yaml` before promoting to production.

---

---

## Design Tradeoffs

### Why Redis instead of Memcached?

Redis supports hash structures (`HSET/HGET`) which map naturally to multi-field telemetry (temperature + humidity + timestamp). Memcached only stores flat strings, requiring serialization overhead. Redis also provides the `RPUSH/LPOP` List operations used by the replay queue and Pub/Sub — neither of which Memcached supports.

### Why MQTT instead of HTTP polling?

HTTP polling at 5-second intervals from hundreds of devices generates `N × (60/5) = 12N` requests/minute even when nothing changed. MQTT is event-driven: devices push only when they have data. With QoS 1, messages are guaranteed delivered at least once. Broker fan-out also decouples producers from consumers cleanly.

### Why Spring Integration for MQTT instead of a raw Paho client?

Spring Integration's `MqttPahoMessageDrivenChannelAdapter` handles reconnection, channel routing, and error handling declaratively. Raw Paho requires manual reconnect loops and error callbacks. The integration also slots naturally into Spring's `@ServiceActivator` pattern, keeping consumer logic as plain Spring beans.

### Why PostgreSQL instead of a time-series DB (InfluxDB / TimescaleDB)?

For this platform's scale (<10M rows/month), indexed PostgreSQL with declarative range partitioning by month performs excellently. TimescaleDB adds operational overhead and a separate deployment. If the workload grows to 100M+ rows/month, migrating to TimescaleDB is straightforward since it is a PostgreSQL extension sharing the same wire protocol and JDBC driver.

### Why Next.js instead of Vite + React?

Next.js provides file-based routing, server-side API proxying via `next.config.mjs` rewrites (no separate nginx), and native Vercel deployment. The App Router's `'use client'` boundary keeps layout components as Server Components, reducing client bundle size.

### Why WebSocket instead of Server-Sent Events (SSE)?

SSE is one-directional (server → client). WebSocket is bidirectional, enabling future features like in-browser device command sending without architectural rework.

### Why partition telemetry by month rather than use TimescaleDB?

Declarative range partitioning (`PARTITION BY RANGE(timestamp)`) is a native PostgreSQL 10+ feature with no additional dependencies. The monthly child tables (`telemetry_2025_01`, etc.) enable efficient partition pruning on range queries and allow future `ALTER TABLE DETACH PARTITION / DROP TABLE` for old months instead of row-by-row deletion. The tradeoff is that the partition range must be extended manually via new migrations as time passes (see Known Limitations).

### Why a Redis List for the replay queue instead of Kafka?

Kafka would be the right answer at >1M buffered messages or with multiple consumers. At this scale (max 10,000 messages), a Redis List provides the same at-least-once semantics (`RPUSH` / `BLPOP`) with zero additional infrastructure. The `ReplayQueueService` is the only consumer, so competing consumers are not a concern.

### Why is DECOMMISSIONED a terminal lifecycle state?

DECOMMISSIONED signals that a device has been physically removed from service. Allowing re-activation would create ambiguity between "this device was briefly taken offline" (INACTIVE) and "this device no longer exists" (DECOMMISSIONED). Making it terminal in the service layer and the MQTT pipeline ensures that historical telemetry and alerts remain traceable to the device that produced them, without the risk of a re-registered device inadvertently inheriting alerts or firmware records from its predecessor.

### Why does ReplayQueueService call TelemetryRepository directly instead of TelemetryService.save()?

`TelemetryService.save()` is decorated with `@Retry` and `@CircuitBreaker`. Calling it from the replay loop would re-enter the retry/CB machinery — triggering up to 3 retries per message, potentially re-opening the CB during recovery, and inflating `sentinel.telemetry.dropped` counters incorrectly. Calling the repository directly bypasses the resilience layer, which is safe because `ReplayQueueService` already checks the CB state before draining: it only runs when the DB is believed to be healthy.

### Why OTel/Jaeger instead of Zipkin?

Both are CNCF-graduated projects. Jaeger's native OTel support (OTLP ingest on port 4318) means zero custom exporters — the same `opentelemetry-exporter-otlp` dependency works without Zipkin-specific formatting. Jaeger's Badger storage backend works well for single-node development deployments without Cassandra/Elasticsearch. For production, both can be backed by the same storage systems.

---

## Known Limitations

1. **Partition range is finite.** The V3 migration pre-creates monthly child tables from `telemetry_2025_01` through `telemetry_2026_12`. Telemetry outside this range lands in `telemetry_default`. New year migrations must be added before the range is exhausted.

2. **Rate limiting is in-process.** Bucket4j uses a local ConcurrentHashMap. With multiple backend replicas, each instance has its own bucket — the effective limit becomes `100 × replica_count` per IP. To fix: swap the `BandwidthLimiter` for `bucket4j-redis` which uses Redis atomic counters for shared state.

3. **WebSocket scales horizontally via Redis pub/sub (implemented).** `WebSocketBroadcastPublisher` publishes to the `ws:telemetry` Redis channel; `WebSocketBroadcastSubscriber` (on every replica) delivers to local sessions. Sticky-session routing (`upstream-hash-by: $remote_addr`) ensures WebSocket upgrades land on the same replica each time. Remaining gap: rate limiter is still in-process (see item 2).

4. **Replay queue overflow is silent.** When the queue reaches `TELEMETRY_REPLAY_MAX_QUEUE` (default: 10,000), new entries are dropped. The `sentinel.telemetry.dropped` counter increments, but no alert fires. For extended DB outages, increase `TELEMETRY_REPLAY_MAX_QUEUE` or set up a Prometheus alert rule on that counter.

5. **Hourly aggregation covers a 2-day look-back window.** Late-arriving IoT messages up to 2 days old are retroactively folded into the correct hourly buckets on the next nightly run (`lateArrivalLookbackDays`, configurable). Gaps older than the look-back window will not be backfilled automatically.

6. **LINE Notify is shut down.** LINE Corp shut down LINE Notify on **March 31, 2025**. Tokens no longer function. Migrate to the Slack webhook or generic webhook provider (`SLACK_NOTIFY_ENABLED` / `NOTIFY_WEBHOOK_ENABLED`).

7. **No refresh-token binding to device/IP.** Refresh tokens are bound only to the user, not to the issuing device or IP. A stolen refresh token can be used from any host until it expires (7 days) or is explicitly revoked via logout. Binding to a fingerprint (IP + User-Agent hash) would reduce the blast radius of a token theft.

---

## Project Structure

```text
sentinel-iot-platform/
├── backend/                    # Spring Boot application
│   ├── src/main/java/com/sentinel/iot/
│   │   ├── config/             # Security, MQTT (+ DLQ), WebSocket, Redis, RequestIdFilter
│   │   ├── controller/         # REST endpoints (auth, devices, telemetry, alerts)
│   │   ├── dto/                # Request/response DTOs + ReplayQueueMessage
│   │   ├── model/              # JPA entities (Device, Telemetry, TelemetryHourlyAggregate, ...)
│   │   ├── repository/         # Spring Data repositories
│   │   ├── security/           # JWT filter + token service
│   │   └── service/            # TelemetryService, AlertService, RedisService,
│   │                           #   ReplayQueueService, TelemetryRetentionService
│   ├── src/main/resources/
│   │   ├── application.yml     # All config; env-var overrides for every external dependency
│   │   ├── logback-spring.xml  # JSON (prod) / human-readable (dev) logging
│   │   └── db/migration/       # V1 schema, V2 hourly aggs, V3 partitioning, V4 lifecycle
│   └── src/test/               # Unit + integration tests (BaseIntegrationTest Testcontainers base)
├── frontend/                   # Next.js 14 (App Router) + Tailwind CSS
│   └── src/
│       ├── app/dashboard/page.jsx          # Protected dashboard (ADMIN sees DeviceManagement)
│       ├── api/client.js                   # Axios client — auth, devices, telemetry, alerts APIs
│       └── components/
│           ├── DeviceList.jsx              # Search filter + lifecycle badge + firmware version
│           ├── TelemetryChart.jsx          # Live / 1h / 6h / 24h / 7d + hourly min/max bands
│           ├── AlertList.jsx               # All / Unacknowledged filter tabs
│           ├── StatsBar.jsx                # 6 tiles including Buffered (replay queue size)
│           └── DeviceManagement.jsx        # ADMIN-only lifecycle + firmware panel
├── simulator/                  # Node.js MQTT publisher
├── monitoring/
│   ├── prometheus.yml
│   └── grafana/provisioning/   # Prometheus + Jaeger datasources + pre-built dashboard
├── mosquitto/                  # MQTT broker config
├── load-testing/               # k6 scripts
├── .github/workflows/ci.yml    # Checkstyle → unit tests → integration tests → Docker build
└── docker-compose.yml          # Full stack (backend, postgres, redis, mosquitto, jaeger, grafana, prometheus)
```

---

## Documentation

Detailed documentation lives in [`docs/`](docs/):

| Document | Contents |
| --- | --- |
| [Architecture](docs/architecture.md) | Component descriptions, data model, deployment topology |
| [API Reference](docs/api.md) | All endpoints, request/response examples, role matrix |
| [Sequence Diagrams](docs/sequence-diagrams.md) | 10 Mermaid diagrams — ingestion, DLQ paths, DB outage/replay, auth, JWT filter, alert (multi-provider), WebSocket, lifecycle, device registration, device enrollment |
| [Scaling Discussion](docs/scaling.md) | Bottleneck map, Kafka, TimescaleDB, Redis Cluster, WebSocket fan-out, scaling roadmap, SLO targets vs observed results |
| [Capacity Planning](docs/capacity-planning.md) | Device-to-infrastructure matrix, per-layer limits and upgrade triggers, AWS cost estimates, monitoring thresholds |
| [Design Tradeoffs](docs/tradeoffs.md) | Decisions — Next.js, MQTT, Redis, PostgreSQL, Spring Integration, WebSocket, JWT |
| [Incident Runbooks](docs/runbooks/) | Runbooks for all 9 SLO alerts + incident response flow (severity levels, post-mortem template) |
| [Chaos Testing](docs/runbooks/chaos-testing.md) | 5 chaos experiments — DB down, Redis down, pod kill, network partition, MQTT restart |
| [Failure Testing Checklist](docs/runbooks/failure-testing.md) | 6 failure scenarios with trigger commands, verification steps, and per-release sign-off table |

---

## Screenshots

> _(Add screenshots after first `docker compose up`)_

| Dashboard                                    | Alerts                                 | Grafana                                  |
|----------------------------------------------|----------------------------------------|------------------------------------------|
| ![dashboard](docs/screenshots/dashboard.png) | ![alerts](docs/screenshots/alerts.png) | ![grafana](docs/screenshots/grafana.png) |

---

## License

MIT © 2024 — Built as a flagship portfolio project demonstrating production IoT architecture.
