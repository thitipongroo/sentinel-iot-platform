# Load Test Plan — Sentinel IoT Platform

**ขอบเขต:** Backend REST API · Kafka Telemetry Pipeline · Multi-Tenant Concurrent Load  
**วิธีทดสอบ:** k6 (JavaScript) พร้อม Prometheus remote-write output  
**สถานะ:** 📋 วางแผนแล้ว — ยังไม่ได้ implement  
**Environment:** `docker compose --profile full up` (core + observability)  
**เป้าหมาย:** หา **breaking point**, ทดสอบ system behavior under stress, และตรวจ resource exhaustion

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

เกณฑ์ตัดสิน PASS/FAIL ที่ใช้ร่วมกันกับ Performance Test Plan

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

## 2.1 API Ramp-Up Load Test

**Scenario:** Gradually เพิ่ม VU ตั้งแต่ 0 → 500 เพื่อหาจุดที่ SLO เริ่ม fail

```
Stages:
  0 → 50 VU    over  2 min   (warm-up)
  50 → 200 VU  over  5 min   (normal load)
  200 → 500 VU over  5 min   (stress)
  500 VU       hold   3 min   (peak)
  500 → 0      over  2 min   (cool-down)
```

| # | Test Case | Endpoint | เกณฑ์ PASS | เกณฑ์ FAIL |
|---|-----------|----------|-----------|-----------|
| 2.1.1 | Device list scaling | `GET /api/v1/devices` | Error rate < 1% ที่ 200 VU | Error rate > 5% ที่ 500 VU |
| 2.1.2 | Telemetry read scaling | `GET /api/v1/telemetry/{id}/latest` | P95 < 500 ms ที่ 200 VU | P99 > 2s ที่ 500 VU → note as bottleneck |
| 2.1.3 | Auth endpoint scaling | `POST /auth/login` | Error rate < 0.1% ที่ 200 VU | Rate limit 429s เกิน 20% ที่ 500 VU |
| 2.1.4 | HikariCP pool exhaustion | mixed API calls | ไม่มี `SQLTransientConnectionException` ที่ 200 VU | Pool exhaustion errors ที่ VU เท่าใด |

**สิ่งที่ต้องบันทึก:**
- VU count ที่ error rate เริ่มเกิน 1%
- VU count ที่ P99 เกิน 2 วินาที
- VU count ที่ HikariCP timeout เริ่มเกิด
- Circuit breaker state ตลอด test

**วิธีวัด:**
- รัน `k6 run scenarios/ramp-up.js --out prometheus=http://localhost:9090/api/v1/write`
- ดู `hikaricp_connections_active`, `hikaricp_connections_timeout_total` ใน Grafana
- ตรวจ circuit breaker: `GET /actuator/health` → `resilience4j.circuitbreaker`

---

## 2.2 Spike Load Test

**Scenario:** Traffic กระโดดจาก baseline → spike ทันทีโดยไม่มี ramp-up (เช่น factory shift change)

```
Stages:
  10 VU        hold   2 min   (baseline)
  10 → 300 VU  in    10 sec   (sudden spike)
  300 VU       hold   5 min   (sustained spike)
  300 → 10 VU  in    30 sec   (recovery)
  10 VU        hold   2 min   (post-spike check)
```

| # | Test Case | คำอธิบาย | เกณฑ์ PASS |
|---|-----------|----------|-----------|
| 2.2.1 | System handles sudden spike | `GET /api/v1/devices` + `GET /api/v1/telemetry/{id}/latest` | Error rate < 5% ระหว่าง spike |
| 2.2.2 | Recovery after spike | หลัง spike ลด — error rate กลับ < 0.1% | ภายใน 60 วินาทีหลัง ramp-down |
| 2.2.3 | Rate limiter behavior at spike | ตรวจ 429 responses ระหว่าง spike | 429 ไม่เกิน 30% ของ total requests (expected behavior) |
| 2.2.4 | Circuit breaker under spike | ตรวจ circuit breaker state ที่ `/actuator/health` | ไม่ trip ไปสถานะ OPEN ถ้า DB ยัง healthy |

**วิธีวัด:**
- รัน `k6 run scenarios/spike.js --out prometheus=http://localhost:9090/api/v1/write`
- วัด error rate ระหว่างแต่ละ stage ใน Grafana
- ตรวจ recovery time หลัง ramp-down: `http_req_failed` ลดลงใน 60 วินาที

---

## 2.3 Soak Test (Endurance Test)

**Scenario:** 100 VU รัน **2 ชั่วโมงต่อเนื่อง** — ตรวจ memory leak, connection leak และ long-term degradation

```
Stages:
  0 → 100 VU  over  5 min
  100 VU      hold   120 min
  100 → 0     over  5 min
```

| # | Test Case | คำอธิบาย | เกณฑ์ PASS |
|---|-----------|----------|-----------|
| 2.3.1 | Memory stability | Monitor JVM heap ผ่าน `/actuator/prometheus` → `jvm_memory_used_bytes` | ไม่มี memory trend เพิ่มขึ้นต่อเนื่อง > 20% ตลอด 2 ชั่วโมง |
| 2.3.2 | Connection pool stability | HikariCP active connections ผ่าน `hikaricp_connections_active` | ไม่มี leaked connections — active ลดลงหลัง idle |
| 2.3.3 | Redis connection stability | `redis_connected_clients` ใน Prometheus | ไม่เพิ่มขึ้น unbounded |
| 2.3.4 | Response time degradation | เปรียบ P95 ที่ t=10min กับ t=110min | Degradation < 20% |
| 2.3.5 | TenantContext leak detection | ตรวจ cross-tenant data ใน response ตลอด test | ไม่มี cross-tenant responses เลย |
| 2.3.6 | JWT revocation list growth | Redis DB-1 memory ผ่าน `redis_memory_used_bytes` | Memory ไม่เพิ่มขึ้น unbounded (expired keys ถูก evict) |

**วิธีวัด:**
- รัน `k6 run scenarios/soak.js --out prometheus=http://localhost:9090/api/v1/write`
- บันทึก Grafana snapshot ทุก 30 นาที
- เปรียบ `jvm_memory_used_bytes` ที่ t=0, t=60min, t=120min

---

## 2.4 Kafka Consumer Load Test

**Scenario:** ทดสอบ Kafka consumer pipeline ภายใต้ sustained high throughput

| # | Test Case | คำอธิบาย | เกณฑ์ PASS |
|---|-----------|----------|-----------|
| 2.4.1 | Consumer lag under 2,000 msg/sec | 2x simulator instances → 2,000 msg/sec | Lag < 20,000 messages ตลอด test |
| 2.4.2 | Partition imbalance | ตรวจ per-partition lag ว่า balanced | Lag variance ระหว่าง partitions < 2x |
| 2.4.3 | Consumer recovery after lag spike | หยุด consumer 60 วินาที → restart → วัด catch-up time | Catch-up ภายใน 5 นาที |
| 2.4.4 | DLQ under load | ส่ง 5% malformed messages ระหว่าง load test | Malformed messages ทั้งหมด land ใน DLQ ไม่ crash consumer |
| 2.4.5 | JDBC batch insert rate | วัด `telemetry` rows/sec ใน PostgreSQL | ≥ 800 rows/sec sustained (batch_size=50) |

**วิธีวัด:**
- Kafka lag: `kafka-consumer-groups.sh --describe --group sentinel-telemetry-ingest`
- Per-partition lag: ตรวจแต่ละ partition ใน Kafka UI หรือ `--verbose` flag
- DB insert rate: ตรวจ `pg_stat_user_tables` → `n_tup_ins` ก่อนและหลัง test

---

## 2.5 Multi-Tenant Load Test

**Scenario:** ทดสอบ per-org isolation ภายใต้ concurrent requests จาก **หลาย tenants พร้อมกัน**

```
Setup:
  - 5 organizations (org-A ถึง org-E) แต่ละ org มี admin + 10 devices
  - 50 VU แต่ละ org (รวม 250 VU)
  - แต่ละ VU ใช้ JWT ของ org ตัวเอง
```

| # | Test Case | คำอธิบาย | เกณฑ์ PASS |
|---|-----------|----------|-----------|
| 2.5.1 | Cross-tenant data isolation under load | Response ของ org A ต้องไม่มีข้อมูลของ org B | 0 cross-tenant leaks ตลอด test |
| 2.5.2 | RLS performance overhead | เปรียบ latency ระหว่าง single-tenant vs multi-tenant | Overhead < 15% |
| 2.5.3 | TenantContext ThreadLocal under high concurrency | ทดสอบกับ thread pool reuse | 0 cross-tenant data leaks |

**วิธีวัด:**
- ตรวจ `organizationId` ใน response body ทุก request ว่าตรงกับ JWT ของ VU นั้น
- เปรียบ P95 latency ระหว่าง single-org scenario (50 VU) กับ multi-org scenario (250 VU รวม)

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
| **k6** | Load scenarios | ≥ v0.51 | `brew install k6` |
| **k6/x/websocket** | WebSocket load testing | latest | k6 extension bundle |
| **Prometheus** | Metrics collection | via Docker Compose | `--profile observability` |
| **Grafana** | Metrics visualization | via Docker Compose | `http://localhost:3001` |

### แนะนำ Project Structure

```
performance/
├── common/
│   ├── auth.js          # login helper, token management
│   └── thresholds.js    # SLO definitions (shared กับ performance-test)
└── scenarios/
    ├── ramp-up.js       # 2.1 — ramp to 500 VU
    ├── spike.js         # 2.2 — sudden spike test
    ├── soak.js          # 2.3 — 2-hour endurance
    ├── kafka-load.js    # 2.4 — Kafka consumer throughput
    └── multi-tenant.js  # 2.5 — 5 org concurrent load
```

### ลำดับการรัน (แนะนำ)

```
1. Ramp-Up Test (2.1)     — หา breaking point ก่อน
2. Spike Test (2.2)       — ตรวจ recovery behavior
3. Kafka Load (2.4)       — ทดสอบ pipeline แยกต่างหาก
4. Multi-Tenant (2.5)     — ตรวจ isolation under load
5. Soak Test (2.3)        — เฉพาะก่อน major release (2 ชั่วโมง)
```

---

## สรุปจำนวน Test Cases

| หัวข้อ | Test Cases |
|--------|-----------|
| 2.1 API Ramp-Up | 4 |
| 2.2 Spike Test | 4 |
| 2.3 Soak Test | 6 |
| 2.4 Kafka Consumer Load | 5 |
| 2.5 Multi-Tenant Load | 3 |
| **รวม** | **22** |
