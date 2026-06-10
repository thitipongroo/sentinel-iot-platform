# Performance Test Plan — Sentinel IoT Platform

**ขอบเขต:** Backend REST API · Kafka Telemetry Pipeline · WebSocket · Redis Cache  
**วิธีทดสอบ:** k6 (JavaScript) พร้อม Prometheus remote-write output  
**สถานะ:** ✅ Implemented — `performance/scenarios/normal-load.js` (1.1+1.3), `kafka-load.js` (1.2), `websocket.js` (1.4)  
**Environment:** `docker compose --profile full up` (core + observability)  
**เป้าหมาย:** วัด response time, throughput และ resource utilization ภายใต้ **normal operating load**

---

## System Profile

| Component | Configuration | ข้อจำกัดที่ควรทดสอบ |
|-----------|--------------|-------------------|
| Spring Boot API | HikariCP pool = 20 connections | Connection pool exhaustion |
| PostgreSQL | Single-node, Flyway V1–V10 | Query latency under concurrent load |
| Redis (DB-0) | Telemetry cache + replay queue | Eviction policy = `noeviction` |
| Redis (DB-1) | JWT revocation blocklist (isolated) | Latency spike กระทบ auth filter |
| Kafka | 3 partitions · max-poll-records=500 · lz4 · linger-ms=5 | Consumer lag accumulation |
| Rate Limiter | Bucket4j in-process · auth 10/min · API 100/min | Per-IP bucket (ไม่ได้ distributed) |
| WebSocket | Redis pub/sub broadcast channel | Concurrent session fan-out |
| Resilience4j | Circuit breaker: 50% failure rate · 30s open wait | Trip under DB pressure |

---

## SLO (Service Level Objectives)

เกณฑ์ตัดสิน PASS/FAIL ที่ใช้ร่วมกันกับ Load Test Plan

| Endpoint Group | P50 | P95 | P99 | Error Rate |
|---------------|-----|-----|-----|------------|
| `POST /auth/login` | < 100 ms | < 300 ms | < 500 ms | < 0.1% |
| `GET /api/v1/devices` | < 80 ms | < 200 ms | < 400 ms | < 0.1% |
| `GET /api/v1/telemetry/{id}/latest` | < 100 ms | < 300 ms | < 600 ms | < 0.1% |
| `GET /api/v1/telemetry/{id}/range` | < 200 ms | < 800 ms | < 1500 ms | < 0.5% |
| `POST /api/v1/devices` | < 150 ms | < 400 ms | < 800 ms | < 0.1% |
| `PATCH /api/v1/devices/{id}/lifecycle` | < 150 ms | < 400 ms | < 800 ms | < 0.1% |
| Kafka telemetry ingestion throughput | ≥ 1,000 msg/sec sustained | — | — | < 0.5% DLQ rate |
| WebSocket broadcast latency (1 → N) | < 50 ms per message | < 150 ms | < 300 ms | — |

---

## 1.1 API Response Time Under Normal Load

**Scenario:** 50 virtual users (VU) รัน 10 นาที  
`ramp-up 1 min → sustained 8 min → ramp-down 1 min`

| # | Test Case | Endpoint | VUs | Duration | เกณฑ์ PASS |
|---|-----------|----------|-----|----------|-----------|
| 1.1.1 | Login latency | `POST /auth/login` | 50 | 10 min | P95 < 300 ms |
| 1.1.2 | Device list latency | `GET /api/v1/devices` | 50 | 10 min | P95 < 200 ms |
| 1.1.3 | Device detail latency | `GET /api/v1/devices/{id}` | 50 | 10 min | P95 < 200 ms |
| 1.1.4 | Telemetry latest latency | `GET /api/v1/telemetry/{id}/latest` | 50 | 10 min | P95 < 300 ms |
| 1.1.5 | Telemetry range latency | `GET /api/v1/telemetry/{id}/range?from=&to=` | 30 | 10 min | P95 < 800 ms |
| 1.1.6 | Alert list latency | `GET /api/v1/alerts` | 50 | 10 min | P95 < 200 ms |

**วิธีวัด:**
- รัน `k6 run scenarios/normal-load.js --out prometheus=http://localhost:9090/api/v1/write`
- วัด `http_req_duration{p(95)}`, `http_req_failed`, `http_reqs` (RPS)
- ดู Grafana dashboard ที่ `http://localhost:3001`

---

## 1.2 Kafka Telemetry Ingestion Throughput

**Scenario:** เพิ่ม concurrency ของ MQTT Simulator — วัด end-to-end latency ตั้งแต่ publish จนถึง persistence ใน PostgreSQL

| # | Test Case | คำอธิบาย | เกณฑ์ PASS |
|---|-----------|----------|-----------|
| 1.2.1 | Sustained ingestion 500 msg/sec | Simulator → MQTT → Kafka → Consumer → PostgreSQL | consumer lag < 5,000 messages |
| 1.2.2 | Sustained ingestion 1,000 msg/sec | เพิ่ม simulator instances | consumer lag < 10,000 messages |
| 1.2.3 | DLQ rate ที่ peak throughput | นับ messages ที่ land ใน `telemetry.dlq` | DLQ rate < 0.5% |
| 1.2.4 | JDBC batch efficiency | ตรวจ PostgreSQL `pg_stat_user_tables` → `n_tup_ins` rate | batch size ≈ 50 rows/statement |

**วิธีวัด:**
- Kafka lag: `kafka-consumer-groups.sh --describe --group sentinel-telemetry-ingest`
- DB insert rate: Prometheus → `spring_datasource_connections_active`
- Circuit breaker: `GET /actuator/health` → `resilience4j.circuitbreaker.telemetryDB`

---

## 1.3 Redis Cache Performance

**Scenario:** Mixed read/write workload — ตรวจ cache hit ratio และ latency ของทั้ง Redis DB-0 (telemetry) และ DB-1 (JWT blocklist)

| # | Test Case | คำอธิบาย | เกณฑ์ PASS |
|---|-----------|----------|-----------|
| 1.3.1 | Telemetry cache hit ratio | `GET /telemetry/{id}/cache` บน device ที่มี recent data | hit ratio ≥ 90% |
| 1.3.2 | Redis auth DB latency under load | 50 VU → `GET /api/v1/devices` — JwtAuthFilter query DB-1 ทุก request | Redis DB-1 latency P99 < 5 ms |
| 1.3.3 | Replay queue drain after backpressure | หยุด PostgreSQL 30 วินาที → restart → วัด drain speed | queue drain ภายใน 60 วินาที |

**วิธีวัด:**
- Redis INFO: `redis-cli info stats` → `keyspace_hits`, `keyspace_misses`
- Prometheus: `redis_commands_duration_seconds_total`, `redis_connected_clients`

---

## 1.4 WebSocket Broadcast Performance

**Scenario:** N clients connect พร้อมกัน — วัด delivery latency ตั้งแต่ Kafka message arrive จนถึง WebSocket client รับ

| # | Test Case | Clients | เกณฑ์ PASS |
|---|-----------|---------|-----------|
| 1.4.1 | Broadcast to 100 concurrent clients | 100 | P95 delivery latency < 150 ms |
| 1.4.2 | Broadcast to 500 concurrent clients | 500 | P95 delivery latency < 300 ms |
| 1.4.3 | Cross-instance broadcast via Redis pub/sub | 2 backend instances | message ถึงทุก client ทั้ง 2 instances |

**วิธีวัด:**
- k6 WebSocket extension (`k6/x/websocket`): วัด `ws_session_duration`, `ws_msgs_received`
- ฝัง `sentAt` timestamp ใน payload → วัด `receivedAt - sentAt` client-side
- ตรวจ Redis pub/sub channel: `redis-cli subscribe ws:telemetry`

---

## Environment

```
Hardware (minimum):
  Backend host : 4 vCPU · 8 GB RAM
  k6 runner    : 2 vCPU · 4 GB RAM (เครื่องแยกจาก backend)

Infrastructure:
  docker compose --profile full up
  (core stack + Prometheus + Grafana + Jaeger ต้องพร้อมทั้งหมด)
  Simulator: OFF ระหว่างรัน test — k6 เป็น traffic source
```

---

## Tools & Setup

| Tool | วัตถุประสงค์ | Version | Setup |
|------|------------|---------|-------|
| **k6** | Performance scenarios | ≥ v0.51 | `brew install k6` |
| **k6/x/websocket** | WebSocket performance | latest | k6 extension bundle |
| **Prometheus** | Metrics collection | via Docker Compose | `--profile observability` |
| **Grafana** | Metrics visualization | via Docker Compose | `http://localhost:3001` |

### แนะนำ Project Structure

```
performance/
├── common/
│   ├── auth.js          # login helper, token management
│   └── thresholds.js    # SLO definitions (shared กับ load-test)
└── scenarios/
    ├── normal-load.js   # 1.1 — baseline 50 VU
    ├── kafka-load.js    # 1.2 — Kafka throughput
    └── websocket.js     # 1.4 — WebSocket broadcast
```

---

## สรุปจำนวน Test Cases

| หัวข้อ | Test Cases |
|--------|-----------|
| 1.1 API Response Time | 6 |
| 1.2 Kafka Ingestion | 4 |
| 1.3 Redis Cache | 3 |
| 1.4 WebSocket Broadcast | 3 |
| **รวม** | **16** |
