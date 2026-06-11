# ⚡ Sentinel IoT Platform

[![CI](https://github.com/your-github-username/sentinel-iot-platform/actions/workflows/ci.yml/badge.svg)](https://github.com/your-github-username/sentinel-iot-platform/actions/workflows/ci.yml)
[![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.2-green?logo=springboot)](https://spring.io/projects/spring-boot)
[![Next.js](https://img.shields.io/badge/Next.js-14-black?logo=nextdotjs)](https://nextjs.org/)
[![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?logo=docker)](https://docs.docker.com/compose/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

**Production-grade Industrial IoT Monitoring Platform** — real-time sensor data ingestion via MQTT, threshold alerting, multi-channel notifications (LINE Messaging API, Telegram, Apprise, Slack), WebSocket dashboard, and full observability stack.

> Cache read path sustains **1,000 req/s** (60,000+ ops/min) — observed p95 **112 ms**, p99 **187 ms** under k6 load test (SLO targets: p95 < 200 ms, p99 < 500 ms) — MacBook Pro M3, 16 GB RAM, Docker Compose.

---

## Architecture Diagram

![Sentinel IoT Platform](/docs/screenshots/sentinel-architecture-diagram.png)

<!--
```text
┌───────────────────────────────────────────────────────────────────────────────┐
│                            Sentinel IoT Platform                              │
│                                                                               │
│  ┌──────────────┐    MQTT        ┌──────────────────┐                         │
│  │ IoT Devices  │───────────────▶│ Eclipse Mosquitto│                         │
│  │  (sensors)   │  factory/      │   MQTT Broker    │◀── DLQ ── factory/      │
│  └──────────────┘  telemetry     └───────┬──────────┘        telemetry/dlq    │
│                                          │ subscribe                          │
│                                ┌─────────▼───────────┐    ┌─────────────────┐ │
│                                │   Spring Boot       │──▶ │ Redis 7         │ │
│                                │   Backend           │    │ • Latest cache  │ │
│                                │                     │    │ • Replay queue  │ │
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
│  │ Notification │◀── webhook ─── │ Distributed     │                          │
│  └──────────────┘                │ Tracing         │                          │
│                                  └─────────────────┘                          │
└───────────────────────────────────────────────────────────────────────────────┘
```
-->

### Data Flow — Normal Path

![Data Flow - Normal Path](/docs/screenshots/sentinel-data-flow-normal-path.png)

<!--
```text
IoT Device
  │── MQTT publish ──▶ Mosquitto
                          │── Spring Integration ──▶ MqttConsumerService
                                                          │── validate payload
                                                          │── resolve device (lifecycle gate)
                                                          │── TelemetryService.save()
                                                          │        │── PostgreSQL (retry + CB)
                                                          │        └── Redis cache (setLatestTelemetry)
                                                          │── AlertService.evaluate()
                                                          │        └── Notification providers (if threshold exceeded, with deduplication)
                                                          └── WebSocket broadcast ──▶ React UI
```
-->

### Data Flow — Failure Paths

![Data Flow - Failure Paths](/docs/screenshots/sentinel-data-flow-failure-path.png)

<!--
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
-->

---

## Tech Stack

![Tech Stack](/docs/screenshots/sentinel-tech-stack.png)

<!--
| Layer           | Technology                                      | Tool                                                                                              |
|-----------------|-------------------------------------------------|---------------------------------------------------------------------------------------------------|
| Backend         | Java                                         | Spring Boot                                                                                   |
| Security        | JWT, Rate Limiting                              | Spring Security, JWT, Bucket4j, ApiVersionFilter                                            |
| Messaging       | MQTT, Event Streaming                           | Eclipse Mosquitto, Spring Integration, Apache Kafka                                       |
| Schema Registry | Avro                                            | Apache Avro, Confluent Schema Registry                                                            |
| Database        | ORM, Range Partitioning          | PostgreSQL, Spring Data JPA, Flyway                                                                           |
| HA Database     | HA Replication, WAL Archiving                   | CloudNativePG, Barman                                                                             |
| Cache           | In-memory Cache, Pub/Sub                        | Redis                                                                                 |
| Resiliency      | Circuit Breaker, Retry                          | Resilience4j                                                                                      |
| Realtime        | WebSocket, Pub/Sub Fan-out                      | Spring WebSocket, Redis pub/sub                                                                   |
| Frontend        | React, App Router, CSS Utility, Charting        | Next.js, Tailwind CSS, Recharts                                                                |
| State mgmt      | Server State, Client State                      | @tanstack/react-query, Zustand                                                      |
| UI performance  | List Virtualization                             | @tanstack/react-virtual                                                                           |
| Design system   | Custom UI Primitives                            | Badge, Select, ErrorBoundary, OfflineBanner                                                       |
| Observability   | Metrics, Distributed Tracing                    | Prometheus, Grafana, OTel/Micrometer, Jaeger                                                      |
| SLO             | Multi-window Burn-rate Alerting                 | Grafana SLO dashboard                                                                             |
| Logging         | Structured Logging, Request Correlation         | Logstash-logback-encoder, MDC                                                                     |
| Testing         | Unit, Integration, Contract, Mutation, Architecture, Benchmark | JUnit, Mockito, Testcontainers, Pact, JMH, Pitest, ArchUnit, schemathesis          |
| Load Test       | Load Testing                                    | k6                                                                                                |
| CI/CD           | Continuous Integration/Delivery                 | GitHub Actions                                                                                    |
| Infra (local)   | Container Orchestration                         | Docker Compose                                                                                    |
| Infra (cloud)   | Kubernetes, Infrastructure as Code, GitOps      | EKS, Helm, ArgoCD, Terraform                                                                      |
| Deployment      | Blue/Green, Canary, Event-driven Autoscaling    | Argo Rollouts, KEDA                                                                               |
| Backup/DR       | Backup, Disaster Recovery                       | Velero, pg_dump CronJob, DR restore script                                                        |
| Notify          | Multi-channel Notification, Deduplication       | LINE, Telegram, Apprise, Slack, webhook                             |
-->

---

## Project Structure

![Project Structure](/docs/screenshots/sentinel-project-structure.png)

<!--
```text
sentinel-iot-platform
├── backend                    # Spring Boot application
│   ├── src/main/java/com/sentinel/iot
│   │   ├── config             # Security, MQTT (+ DLQ), WebSocket, Redis, RequestIdFilter, RLS
│   │   ├── controller         # REST endpoints (auth, devices, telemetry, alerts, users, settings)
│   │   ├── converter          # JPA attribute converters (DeviceCapabilities, SensorReadings)
│   │   ├── dto                # Request/response DTOs + ReplayQueueMessage
│   │   ├── kafka              # KafkaTelemetryProducer, KafkaTelemetryConsumer, TelemetryDlqConsumer
│   │   ├── model              # JPA entities (Device, Telemetry, TelemetryHourlyAggregate, ...)
│   │   ├── repository         # Spring Data repositories
│   │   ├── security           # JWT filter + token service
│   │   ├── service            # AlertService, AuditService, DeviceEnrollmentService, JwtService, MqttConsumerService,
│   │   │   │                  # NotificationService, PlatformSettingsService, RedisService, ReplayQueueService, 
│   │   │   │                  # SchemaCompatibilityService, TelemetryService, TelemetryRetentionService, UserService
│   │   │   └── notification   # LINE, Telegram, Apprise, Slack, Webhook + AlertDeduplicator
│   │   └── websocket          # WebSocket broadcast publisher + subscriber
│   ├── src/main/resources
│   │   ├── application.yml    # All config; env-var overrides for every external dependency
│   │   ├── logback-spring.xml # JSON (prod) / human-readable (dev) logging
│   │   └── db/migration       # V1–V11: schema, partitioning, lifecycle, multi-tenancy,
│   │                          # RLS, schema evolution, enrollment tokens, nullable fields,
│   │                          # device name uniqueness (V10), platform settings (V11)
│   └── src/test               # Unit · Integration · Contract · Benchmark · Concurrent · Chaos · Architecture · Regression
├── frontend                   # Next.js 14 (App Router) + Tailwind CSS
│   └── src
│       ├── app                # App Router — dashboard, devices, alerts, users, settings, login, forgot-password
│       ├── api                # Axios client + generated API types
│       ├── components         # Shared UI components + ui / primitives
│       ├── hooks              # Custom React hooks
│       └── lib                # Utility functions
├── infra                      # Cloud infrastructure
│   ├── helm/sentinel-iot      # Helm chart — Kubernetes manifests, Argo Rollouts, KEDA, Velero
│   ├── argocd                 # ArgoCD Application + ApplicationSet (staging + prod)
│   ├── terraform              # EKS, RDS, ElastiCache, MSK modules
│   ├── monitoring             # SLO rules + Grafana SLO dashboard
│   └── scripts                # DR restore script
├── simulator                  # Node.js MQTT publisher — dev/demo only (docs/demo/README.md)
├── monitoring                 # Prometheus config + Grafana provisioning (Docker Compose)
├── mosquitto                  # MQTT broker config
├── tests                      # k6 system-level tests (load/, performance/)
├── scripts                    # seed-demo.sh, seed-industry.sh, unseed-demo.sh, unseed-industry.sql, gen-mqtt-certs.sh
├── deploy                     # nginx-lb.conf
├── docs                       # Documentation — see docs/README.md
├── .github/workflows          # ci.yml (main pipeline) + api-contract.yml (contract fuzzing)
├── run.sh                     # Docker Compose wrapper (alternative to make)
└── docker-compose.yml         # Full stack (backend, postgres, redis, mosquitto, kafka, jaeger, grafana, prometheus)
```
-->

---

## Quick Start

### Prerequisites

- Docker + Docker Compose v2
- JDK 21 and Node 20 or Above (for local dev)

### Clone และตั้งค่า `.env`

```bash
git clone https://github.com/your-github-username/sentinel-iot-platform.git
cd sentinel-iot-platform
cp .env.template .env
```

เปิด `.env` และกำหนดค่า:

```env
INIT_ADMIN_PASSWORD=<your-admin-password>
INIT_OPERATOR_PASSWORD=<your-operator-password>
COMPOSE_PROFILES=prod
```

---

จากนั้น:

```bash
# Linux / macOS / Git Bash
make up          # core
make up-obs      # core + Prometheus / Grafana / Jaeger

# Windows PowerShell
docker compose up -d
```

---

### Compose profiles reference

| `COMPOSE_PROFILES` | Services ที่รัน |
|---|---|
| `prod` | core เท่านั้น |
| `prod,observability` | core + Prometheus + Grafana + Jaeger |

> **core** = postgres, redis, mosquitto, kafka, backend, frontend
>
> สำหรับ `dev` profile (simulator) ดูที่ [docs/demo/README.md](docs/demo/README.md)

---

### Shortcut commands

`Makefile` และ `run.sh` เป็น wrapper ของ `docker compose` — ใช้แทนกันได้ทุกคำสั่ง

| คำสั่ง (`make`) | คำสั่ง (`./run.sh`) | ผลลัพธ์ |
|----------------|-------------------|---------|
| `make up` | `./run.sh up` | Start stack ตาม `COMPOSE_PROFILES` ใน `.env` |
| `make up-obs` | `./run.sh up-obs` | Start stack + เปิด Prometheus / Grafana / Jaeger |
| `make up-full` | `./run.sh up-full` | Start stack + monitoring + rebuild images ทั้งหมด |
| `make build` | `./run.sh build` | Rebuild images แล้ว start |
| `make down` | `./run.sh down` | หยุดทุก container |
| `make down-v` | `./run.sh down-v` | หยุดทุก container + ลบ volumes ทั้งหมด (ข้อมูลหาย) |
| `make logs` | `./run.sh logs` | Tail logs ทุก service |
| `make ps` | `./run.sh ps` | แสดงสถานะ container |

> **หมายเหตุ :**
>
> - `make` - ใช้ได้บน Linux / macOS และ Git Bash (Windows)
> - `./run.sh` — กรณีที่ไม่ได้ติดตั้ง `make`

| Service       | URL                                    |
|---------------|----------------------------------------|
| Dashboard     | <http://localhost:3000>                |
| Backend API   | <http://localhost:8080/api/v1>         |
| Swagger UI    | <http://localhost:8080/swagger>         |
| Prometheus    | <http://localhost:9090>                |
| Grafana       | <http://localhost:3001>                |
| Jaeger UI     | <http://localhost:16686>               |
| MQTT Broker   | `tcp://localhost:1883`                 |

**First-Run Credentials :**

1. ตั้งค่าใน `.env` ก่อน start stack ครั้งแรก :

   ```env
   INIT_ADMIN_PASSWORD=<your-admin-password>
   INIT_OPERATOR_PASSWORD=<your-operator-password>
   ```

   > **`JWT_SECRET`** — มี 2 วิธี:
   > - **อัตโนมัติ :** ตอนรันคำสั่ง `make <target>` หรือ `./run.sh <command>` จะ generate และบันทึกลง `.env` ให้เองหากยังไม่มีค่า
   > - **Manual :** รันคำสั่งนี้ใน terminal แล้วนำค่าที่ได้ไปใส่ใน `.env`
   >
   >   ```bash
   >   openssl rand -base64 48
   >   ```

2. Start หรือ recreate service ที่ต้องการ (กรณีที่รันไปแล้ว แล้วมีการแก้ไขค่าใน `.env` ที่มีผลต่อการทำงานของ `<service>`) :

   ```bash
   docker compose up -d --force-recreate `<service>`
   ```

3. Login ที่ <http://localhost:3000> ด้วย `admin` / _(ค่า `INIT_ADMIN_PASSWORD`)_

- Grafana: `admin` / _(value of `GRAFANA_PASSWORD`, default `changeme` — change before any internet-facing deployment)_

---

## API Reference

All API endpoints are versioned under `/api/v1/`. See [`docs/system-design/api.md`](docs/system-design/api.md) for the full reference.

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
GET /api/v1/alerts?page=0&size=50
GET /api/v1/alerts/unacknowledged
GET /api/v1/alerts/device/{deviceId}
PUT /api/v1/alerts/{id}/acknowledge    # ADMIN only
PUT /api/v1/alerts/acknowledge-all     # ADMIN only
```

### Users

```http
GET    /api/v1/users                          # ADMIN only — list all users in organization
POST   /api/v1/users                          # ADMIN only — create user (ADMIN or OPERATOR role)
DELETE /api/v1/users/{username}               # ADMIN only — cannot delete your own account
PATCH  /api/v1/users/{username}/role          # ADMIN only — change role; cannot change own
PATCH  /api/v1/users/{username}/password      # ADMIN only — reset password; cannot reset own
```

### Settings

```http
GET   /api/v1/settings    # ADMIN + OPERATOR — get platform settings for current organization
PATCH /api/v1/settings    # ADMIN only — update thresholds, retention days, notification toggles
```

Response shape:

```json
{
  "thresholds": { "temperatureCelsius": 80.0, "humidityPercent": 90.0, "smokePpm": 200.0 },
  "retention":  { "telemetryDays": 30, "auditDays": 90 },
  "notifications": { "slack": false, "line": false, "webhook": false }
}
```

Settings are stored per-organization in the `platform_settings` table (V11 migration). On first read the row is seeded from the global env-var defaults.

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
| `SMOKE_THRESHOLD`     | `200`   | ppm — triggers CRITICAL alert + notification          |
| `HUMIDITY_THRESHOLD`  | `90`    | % — triggers WARNING alert                            |

Motion + elevated temperature (>70°C) also triggers a WARNING alert under the legacy engine.

---

## Failure Scenarios

The platform handles DB unavailability (circuit breaker + Redis replay queue), Redis downtime (PostgreSQL writes continue unaffected), MQTT broker disconnection (auto-reconnect, QoS 1 redelivery), malformed payloads (5-stage validation → DLQ routing), and circuit-breaker-safe replay drain (drain skipped while CB is OPEN). See [`docs/system-design/architecture.md`](docs/system-design/architecture.md) for full failure documentation.

---

## Security

| Feature | Implementation |
| --- | --- |
| Authentication | JWT (15 min, JS memory only) + HttpOnly refresh token (7 days, SHA-256 hash in DB, rotated on every use) |
| Revocation | Redis JTI blocklist — revoked tokens rejected immediately on every request |
| RBAC | `ADMIN` + `OPERATOR` roles; method-level `@PreAuthorize` |
| Rate Limiting | Bucket4j — 10 req/min (auth), 100 req/min (API) per IP |
| Multi-tenant Isolation | `organizationId` scoping + PostgreSQL Row Level Security + tenant-namespaced Redis keys |
| MQTT Auth | `allow_anonymous false`; per-device credentials; TLS on `:8883`, mTLS opt-in |
| Secret Scanning | Gitleaks on every CI push; Trivy SCA + container image scan (CRITICAL/HIGH fail build) |

See [`docs/system-design/security.md`](docs/system-design/security.md) for full feature list and known limitations.

---

## Observability

Prometheus scrapes `/actuator/prometheus` every 15s. Custom `sentinel_*` metrics cover MQTT ingestion, DLQ routing, replay queue depth, circuit breaker state, and business counters (active devices, unacknowledged alerts). Every request is traced end-to-end via OpenTelemetry → Jaeger with custom spans on `telemetry.save` and `alert.evaluate`. JSON structured logs include `requestId`, `traceId`, and `spanId` per line. Import `monitoring/grafana/dashboard.json` for the pre-built dashboard. See [`docs/runbooks/`](docs/runbooks/) for operational guidance.

---

## Telemetry Retention

Raw telemetry is retained for 30 days (configurable via `TELEMETRY_RETENTION_DAYS`). The retention cron runs at 02:30 daily: aggregates hourly buckets with a 2-day late-arrival look-back, prunes raw rows outside the retention window, and drops empty monthly partition tables. The dashboard uses hourly aggregates for 24h and 7d chart modes. See [`docs/system-design/telemetry-retention.md`](docs/system-design/telemetry-retention.md).

---

## Device Lifecycle

Devices follow a linear state machine: `PROVISIONED → ACTIVE → INACTIVE → DECOMMISSIONED`. Rules enforced in `DeviceService`:

- `DECOMMISSIONED` is terminal — no further transitions are accepted (HTTP 409).
- Transitioning to `INACTIVE` or `DECOMMISSIONED` forces `status = OFFLINE`.
- The MQTT ingestion pipeline rejects telemetry from `INACTIVE` or `DECOMMISSIONED` devices (routed to DLQ with code `LIFECYCLE_REJECTED`).
- Firmware version updates are rejected for decommissioned devices.

---

## CI/CD

GitHub Actions runs on every push and PR: Gitleaks + Trivy scan → Checkstyle + backend tests (Testcontainers) → ESLint + Next.js build → Docker Compose validation + parallel image build. All steps are hard-fails. See [`docs/system-design/cicd.md`](docs/system-design/cicd.md).

---

## MQTT TLS / mTLS

By default the broker listens on port **1883 (plain TCP)**. For production, enable TLS on port **8883**:

```bash
bash scripts/gen-mqtt-certs.sh mqtt.yourdomain.com           # TLS only
bash scripts/gen-mqtt-certs.sh mqtt.yourdomain.com --with-client-certs  # mTLS
```

Certificates are written to `mosquitto/certs/`. Set `MQTT_TLS_REQUIRED=true` to disable plaintext port 1883; set `MQTT_MTLS_ENABLED=true` to require client certificates. Restart Mosquitto to apply (`docker compose restart mosquitto`).

> Self-signed certificates are suitable for dev and portfolio demonstration only.

See [`docs/system-design/mqtt-tls.md`](docs/system-design/mqtt-tls.md) for certificate file reference and connection testing.

---

## Notification Setup

Multiple providers can be enabled simultaneously. Repeated alerts are deduplicated per device/sensor within a configurable cooldown (default 5 minutes).

| Provider | Env vars | Notes |
|---|---|---|
| LINE Messaging API | `LINE_MESSAGING_CHANNEL_TOKEN`, `LINE_MESSAGING_TO`, `LINE_MESSAGING_ENABLED=true` | Free tier: 200 msg/month (per-recipient). Replaces LINE Notify (shut down March 2025) |
| Telegram | `TELEGRAM_BOT_TOKEN`, `TELEGRAM_CHAT_ID`, `TELEGRAM_ENABLED=true` | Free, no monthly quota, 30 msg/sec |
| Apprise (self-hosted) | `APPRISE_URL`, `APPRISE_ENABLED=true` | 130+ services via single endpoint — docker run caronc/apprise |
| Slack | `SLACK_WEBHOOK_URL`, `SLACK_NOTIFY_ENABLED=true` | Create webhook at api.slack.com/messaging/webhooks |
| Generic Webhook | `NOTIFY_WEBHOOK_URL`, `NOTIFY_WEBHOOK_ENABLED=true`, `NOTIFY_WEBHOOK_SECRET` (optional HMAC-SHA256) | PagerDuty, Opsgenie, Teams, etc. |

See [`docs/system-design/notification.md`](docs/system-design/notification.md).

---

## Deployment

Production runs on AWS (ap-southeast-1) deployed via ArgoCD GitOps. For local development and demo, see [docs/demo/README.md](docs/demo/README.md).

| Service    | Platform                      | Notes                                              |
|------------|-------------------------------|----------------------------------------------------|
| Frontend   | EKS                           | 2 replicas, PodDisruptionBudget minAvailable: 1    |
| Backend    | EKS (KEDA)                    | 3–20 replicas — scales on Kafka consumer lag       |
| PostgreSQL | RDS (db.t3.medium)            | ap-southeast-1, external to Helm chart             |
| Redis      | ElastiCache (cache.t3.micro)  | ap-southeast-1, external to Helm chart             |
| Kafka      | MSK (kafka.t3.small)          | ap-southeast-1, external to Helm chart             |
| MQTT       | Mosquitto on EKS              | In-cluster (`mosquitto.enabled=true`)              |
| Deploy     | ArgoCD                        | GitOps — syncs Helm releases to staging + prod     |

---

## Infrastructure Ownership

| Tool | Responsibility | Owner |
|---|---|---|
| Terraform | Cloud resource provisioning (EKS, RDS, ElastiCache, MSK) | Platform/Infra team |
| Helm | Application templating + Kubernetes manifests | App team |
| ArgoCD | GitOps deployment sync — reconciles Helm releases across staging + prod | Platform/Infra team |
| Argo Rollouts | Blue/green and canary deployment strategies | App team |
| KEDA | Kafka-lag-based horizontal pod autoscaling | Platform/Infra team |
| Velero | Namespace backup + scheduled snapshot to object storage | Platform/Infra team |

Lock all tool versions in `infra/terraform/versions.tf` and `infra/helm/sentinel-iot/Chart.yaml` before promoting to production.

---

## Design Tradeoffs

Key decisions: Redis over Memcached (hash structures + Pub/Sub + List for replay queue), MQTT over HTTP polling (event-driven, QoS 1, broker fan-out), Spring Integration over raw Paho (declarative reconnect + error routing), PostgreSQL over TimescaleDB (sufficient at <10M rows/month, same wire protocol for future migration), Next.js over Vite+React (file-based routing, SSR, native Vercel deployment), WebSocket over SSE (bidirectional), Redis List over Kafka for replay queue (zero extra infra at ≤10,000 messages). See [`docs/system-design/tradeoffs.md`](docs/system-design/tradeoffs.md).

---

## Known Limitations

1. **Partition range is finite** — monthly child tables pre-created through 2026-12; add new year migrations before range exhausts.
2. **Rate limiting is in-process** — each replica has its own Bucket4j bucket; fix with `bucket4j-redis` for shared state.
3. **Replay queue overflow is silent** — entries dropped at `TELEMETRY_REPLAY_MAX_QUEUE` (default 10,000); set a Prometheus alert on `sentinel_telemetry_dropped_total` for extended outages.
4. **Alert deduplication uses Redis DB 0** — `AlertDeduplicator` uses `SET NX PX` so all replicas share the same cooldown state. If Redis is unavailable, dedup is skipped (fail-open) so notifications still reach operators — the Kafka consumer treats Redis as best-effort and has no error handling around `alertService.evaluate()`.

See [`docs/system-design/tradeoffs.md`](docs/system-design/tradeoffs.md) for the full list.

---

## Demo Seed Data

### Default demo (500 factory devices)

```bash
./scripts/seed-demo.sh       # seeds 500 devices + ~1 M telemetry rows
./scripts/unseed-demo.sh     # removes all sensor-N devices
```

Login: `admin` / `$INIT_ADMIN_PASSWORD`

---

### Industry Device Catalog (47 devices across 8 industries)

```bash
./scripts/seed-industry.sh       # seeds 8 industries, 47 devices, 48 h telemetry + sample alerts
docker exec -i sentinel-postgres psql -U sentinel -d sentinel \
  < scripts/unseed-industry.sql  # removes industry data only
```

Full catalog — all 47 devices with per-sensor thresholds across 8 industries: **[docs/system-design/device-catalog.md](docs/system-design/device-catalog.md)**

---

## Documentation

Detailed documentation lives in [`docs/`](docs/). See [`docs/README.md`](docs/README.md) for the full directory index.

### Design & Architecture (`docs/system-design/`)

| Document | Contents |
| --- | --- |
| [Architecture](docs/system-design/architecture.md) | Component descriptions, data model, deployment topology |
| [API Reference](docs/system-design/api.md) | All endpoints, request/response examples, role matrix |
| [Security](docs/system-design/security.md) | JWT, RBAC, multi-tenant isolation, rate limiting, audit logging, known limitations |
| [Telemetry Retention](docs/system-design/telemetry-retention.md) | 30-day raw retention, hourly aggregates, partition lifecycle |
| [CI/CD](docs/system-design/cicd.md) | GitHub Actions pipeline — security scan, Testcontainers, contract testing |
| [MQTT TLS / mTLS](docs/system-design/mqtt-tls.md) | Certificate generation, env vars, connection testing |
| [Notification Setup](docs/system-design/notification.md) | LINE Messaging API, Telegram, Apprise, Slack, generic webhook — with deduplication |
| [Sequence Diagrams](docs/system-design/sequence-diagrams.md) | 10 Mermaid diagrams — ingestion, DLQ paths, DB outage/replay, auth, JWT filter, alert (multi-provider), WebSocket, lifecycle, device registration, device enrollment |
| [Scaling Discussion](docs/system-design/scaling.md) | Bottleneck map, Kafka, TimescaleDB, Redis Cluster, WebSocket fan-out, scaling roadmap, SLO targets vs observed results |
| [Capacity Planning](docs/system-design/capacity-planning.md) | Device-to-infrastructure matrix, per-layer limits and upgrade triggers, AWS cost estimates, monitoring thresholds |
| [Design Tradeoffs](docs/system-design/tradeoffs.md) | Decisions — Next.js, MQTT, Redis, PostgreSQL, Spring Integration, WebSocket, JWT |

### Runbooks (`docs/runbooks/`)

| Document | Contents |
| --- | --- |
| [Incident Runbooks](docs/runbooks/) | Runbooks for all 9 SLO alerts + incident response flow (severity levels, post-mortem template) |
| [Chaos Testing](docs/runbooks/chaos-testing.md) | 5 chaos experiments — DB down, Redis down, pod kill, network partition, MQTT restart |
| [Failure Testing Checklist](docs/runbooks/failure-testing.md) | 6 failure scenarios with trigger commands, verification steps, and per-release sign-off table |

### Test Plans (`docs/test-plans/`)

| Document | Contents |
| --- | --- |
| [Security Test Plan](docs/test-plans/security-test-plan.md) | 45 test cases — JWT auth, RBAC, multi-tenant isolation, rate limit, WebSocket, error handling |
| [Performance Test Plan](docs/test-plans/performance-test-plan.md) | 16 test cases — normal load (50 VU), Kafka throughput, Redis cache, WebSocket broadcast |
| [Load Test Plan](docs/test-plans/load-test-plan.md) | 22 test cases — ramp-up (0→500 VU), spike, soak (2 hr), Kafka consumer, multi-tenant |
| [Regression Test Plan](docs/test-plans/regression-test-plan.md) | 55 test cases — API contract, HTTP status, auth, RBAC, multi-tenant, migration, rate limit, WebSocket |
| [E2E Test Plan](docs/test-plans/e2e-test-plan.md) | Full user journeys (Playwright) — device lifecycle, alert, WebSocket |
| [Backend Integration Test Plan](docs/test-plans/backend-integration-test-plan.md) | Spring MVC + Testcontainers integration tests |

### Reports (`docs/test-reports/`)

| Document | Contents |
| --- | --- |
| [Test Report](docs/test-reports/README.md) | Test execution summary — 335 tests across backend unit/integration/contract/benchmark/concurrent/chaos/security/regression and frontend |
| [Load Test Report](docs/test-reports/load-test-report.md) | Cache read path baseline — 1,000 RPS, p95 112 ms, p99 187 ms |

### Demo & Development (`docs/demo/`)

| Document | Contents |
| --- | --- |
| [Demo Guide](docs/demo/README.md) | Node.js Simulator, Demo Data seeding, Development Quick Start |

---

## Screenshots

| Dashboard                                    | Alerts                                 | Grafana                                  |
|----------------------------------------------|----------------------------------------|------------------------------------------|
| ![dashboard](docs/screenshots/dashboard.png) | ![alerts](docs/screenshots/alerts.png) | ![grafana](docs/screenshots/grafana.png) |

---

## License

MIT © 2026 — Project demonstrating production IoT architecture.
