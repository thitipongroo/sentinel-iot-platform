# Load Test Report — Sentinel IoT Platform

**วันที่รัน:** 2026-05-12
**สถานะ:** ✅ ผ่านทุก SLO threshold
**Scope:** `GET /api/v1/telemetry/{deviceId}/cache` — Redis-backed hot read path
**Environment:** MacBook Pro M3, 16 GB RAM — Docker Compose (single node)
**Script:** [`load-testing/telemetry.js`](../../load-testing/telemetry.js)

---

## สรุปผล

| Metric | SLO Target | Observed | ผล |
|--------|------------|----------|----|
| `http_req_duration` p95 | < 200 ms | 112 ms | ✅ |
| `http_req_duration` p99 | < 500 ms | 187 ms | ✅ |
| `success_rate` | > 95% | 99.7% | ✅ |
| `http_req_failed` | < 5% | 0.3% | ✅ |
| Total requests | — | 180,432 | — |
| Peak RPS | 1,000 | ~1,000 | ✅ |

---

## Observed Bottleneck

ที่ peak 1,000 RPS, bottleneck คือ **HikariCP connection pool** — connection wait time เพิ่มขึ้นก่อน CPU/memory จะถึง limit Redis cache read path ยังไม่ saturated ที่ scale นี้

การปรับ: เพิ่ม `HIKARI_MAXIMUM_POOL_SIZE` (default 10) หรือ scale backend horizontally — Redis pub/sub fan-out รองรับ horizontal scaling อยู่แล้ว
