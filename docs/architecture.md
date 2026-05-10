# Architecture

## System Overview

Sentinel IoT Platform is a production-grade industrial monitoring system designed around an event-driven architecture. IoT devices publish sensor readings over MQTT every 5 seconds. A Spring Boot backend consumes these messages, persists them to PostgreSQL, caches the latest values in Redis, evaluates threshold rules, and broadcasts updates to connected browsers via WebSocket. A Next.js dashboard renders realtime charts without polling.

---

## High-Level Diagram

```text
                          ┌──────────────────────────────────────────┐
                          │           Sentinel IoT Platform           │
                          │                                           │
  ┌─────────────┐  MQTT   │  ┌──────────────────────────────────┐    │
  │ IoT Devices │────────▶│  │      Eclipse Mosquitto 2.0        │    │
  │ (sensors)   │         │  │        MQTT Broker                │    │
  └─────────────┘         │  └────────────────┬─────────────────┘    │
                          │                   │ subscribe             │
  ┌─────────────┐  MQTT   │  ┌────────────────▼─────────────────┐    │
  │  Node.js    │────────▶│  │         Spring Boot 3.2           │    │
  │  Simulator  │         │  │                                   │    │
  └─────────────┘         │  │  ┌─────────────────────────────┐ │    │
                          │  │  │  MqttConsumerService        │ │    │
                          │  │  │  (Spring Integration)        │ │    │
                          │  │  └──────┬──────────────┬────────┘ │    │
                          │  │         │              │           │    │
                          │  │  ┌──────▼──────┐ ┌────▼────────┐ │    │
                          │  │  │ Telemetry   │ │ Alert       │ │    │
                          │  │  │ Service     │ │ Service     │ │    │
                          │  │  └──────┬──────┘ └────┬────────┘ │    │
                          │  │         │              │           │    │
                          │  │  ┌──────▼──┐  ┌───────▼──┐       │    │
                          │  │  │ Redis   │  │ LINE     │        │    │
                          │  │  │ Cache   │  │ Notify   │        │    │
                          │  │  └─────────┘  └──────────┘       │    │
                          │  │         │                          │    │
                          │  │  ┌──────▼──────┐                  │    │
                          │  │  │ PostgreSQL  │                  │    │
                          │  │  │ (JPA)       │                  │    │
                          │  │  └─────────────┘                  │    │
                          │  │                                   │    │
                          │  │  ┌──────────────────────────────┐ │    │
                          │  │  │  WebSocket Gateway           │ │    │
                          │  │  │  (TelemetryWebSocketHandler) │ │    │
                          │  │  └──────────────────────────────┘ │    │
                          │  └──────────────────┬────────────────┘    │
                          │                     │ WS + REST           │
                          │  ┌──────────────────▼────────────────┐    │
                          │  │        Next.js 14 Dashboard        │    │
                          │  │   (App Router + Recharts)          │    │
                          │  └────────────────────────────────────┘    │
                          │                                           │
                          │  ┌────────────┐   ┌────────────┐         │
                          │  │ Prometheus │──▶│  Grafana   │         │
                          │  │ (metrics)  │   │ (dashboards│         │
                          │  └────────────┘   └────────────┘         │
                          └──────────────────────────────────────────┘
```

---

## Component Descriptions

### Eclipse Mosquitto (MQTT Broker)

- Protocol: MQTT 3.1.1 over TCP port 1883 and WebSocket port 9001
- `allow_anonymous true` for development (swap to password file for production)
- Persistence enabled so retained messages survive restarts
- QoS 1 used by both simulator and backend subscriber (at-least-once delivery)

### Spring Boot Backend

The backend is a single deployable JAR with the following internal layers:

| Layer | Package | Responsibility |
|-------|---------|---------------|
| Security | `security/` | JWT filter, BCrypt password encoding |
| Config | `config/` | MQTT, WebSocket, Redis, Spring Security beans |
| Controller | `controller/` | REST endpoints — auth, devices, telemetry, alerts |
| Service | `service/` | Business logic — ingest, alert evaluation, LINE Notify |
| Repository | `repository/` | Spring Data JPA — devices, telemetry, alerts, users |
| WebSocket | `websocket/` | `CopyOnWriteArraySet` of active sessions, broadcast |

### Redis Cache

Two key spaces:

| Key pattern | Type | TTL | Purpose |
|-------------|------|-----|---------|
| `device:status:{id}` | String | 10 min | Online/Offline status |
| `device:telemetry:{id}` | Hash | 10 min | Latest temperature, humidity, motion, smokePpm |

Reads from Redis first; falls back to PostgreSQL only on cache miss.

### PostgreSQL

Three domain tables plus a users table:

```text
app_users      devices        telemetry            alerts
──────────     ──────────     ──────────────────   ──────────────────
id UUID PK     id UUID PK     id UUID PK           id UUID PK
username       name UNIQUE    device_id UUID FK     device_id UUID FK
password       status         temperature DOUBLE    level VARCHAR
role           description    humidity DOUBLE       message TEXT
               location       motion BOOLEAN        acknowledged BOOL
               created_at     smoke_ppm DOUBLE      created_at
               last_seen      timestamp
```

Indexes on `telemetry(device_id)` and `telemetry(timestamp)` keep range queries fast at high row counts.

### Next.js 14 Dashboard

- **App Router** with `'use client'` boundaries only where browser APIs are needed
- API calls proxied through `next.config.mjs` rewrites (`/api/*` → backend) — no CORS configuration required in the browser
- WebSocket connects directly to backend port 8080 via `NEXT_PUBLIC_WS_URL` (set at build/run time)
- Auto-reconnects with 3-second backoff on disconnect

### Prometheus + Grafana

Prometheus scrapes `/actuator/prometheus` every 15 seconds. Key custom metrics:

| Metric | Type | Description |
|--------|------|-------------|
| `sentinel_telemetry_received_total` | Counter | MQTT messages processed |
| `sentinel_mqtt_messages_total` | Counter | Raw MQTT events |
| `http_server_requests_seconds` | Histogram | Latency per endpoint |
| `jvm_memory_used_bytes` | Gauge | Heap usage |

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

| Field | Type | Range | Threshold |
|-------|------|-------|-----------|
| `temperature` | Double | 0–150 °C | > 80 °C → CRITICAL |
| `humidity` | Double | 0–100 % | > 90 % → WARNING |
| `motion` | Boolean | true/false | true + temp > 70 °C → WARNING |
| `smokePpm` | Double | 0–500 ppm | > 200 ppm → CRITICAL |

---

## Deployment Topology

```text
Internet
    │
    ▼
┌──────────────────┐
│  Vercel CDN      │  ← Next.js frontend (static + SSR)
│  (frontend)      │
└────────┬─────────┘
         │ HTTPS /api/*  (rewritten to backend)
         │ WSS /ws/telemetry (direct)
         ▼
┌──────────────────┐
│  Railway/Render  │  ← Spring Boot backend (stateless, scalable)
│  (backend)       │
└────────┬─────────┘
         │
    ┌────┴────┬──────────┬────────────┐
    ▼         ▼          ▼            ▼
Supabase   Upstash    HiveMQ      Docker VM
(Postgres) (Redis)    (MQTT)   (Prometheus+Grafana)
```
