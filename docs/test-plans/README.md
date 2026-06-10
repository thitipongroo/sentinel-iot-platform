# test-plans/ — Sentinel IoT Platform

แผนการทดสอบแยกตาม layer และประเภท ผลการรันดูได้ที่ [test-reports/](../test-reports/)

---

## Backend

| ไฟล์ | ขอบเขต | Tests | สถานะ |
|------|--------|-------|-------|
| [backend-unit-test-plan.md](backend-unit-test-plan.md) | Service, Repository, Filter, Converter unit tests | 53 | ✅ Implemented |
| [backend-integration-test-plan.md](backend-integration-test-plan.md) | Spring MVC + Testcontainers integration tests | 92 | ✅ Implemented |
| [backend-concurrency-test-plan.md](backend-concurrency-test-plan.md) | Thread safety, TenantContext isolation, rate limiter concurrency | 3 | ✅ Implemented |
| [security-test-plan.md](security-test-plan.md) | JWT auth, RBAC, multi-tenant isolation, rate limit, WebSocket, error handling | 45 | ✅ Implemented |
| contract/ — SentinelApiConsumerContractTest | Consumer-driven contract tests (Pact) — auth login (200/401), device list (200), device not found (404), unauthenticated (403) | 5 | ✅ Implemented |
| benchmark/ — PerformanceGateTest + JwtPerformanceBenchmark | JMH microbenchmark gate — JWT generateAccessToken, extractUsername, extractOrgId ต้อง < 1 ms | 1 | ✅ Implemented |
| concurrent/ — ConcurrentLoadTest | In-process concurrent request safety — 50 concurrent GET, 20 concurrent login, 30 reads + 10 writes | 3 | ✅ Implemented |
| chaos/ — ResilienceUnderChaosTest | Fault injection via Toxiproxy — baseline, 500 ms latency, 6 s latency (→ 5xx), recovery | 4 | ✅ Implemented |

---

## Frontend

| ไฟล์ | ขอบเขต | Tests | สถานะ |
|------|--------|-------|-------|
| [frontend-unit-test-plan.md](frontend-unit-test-plan.md) | React component unit tests (Jest + React Testing Library) | 76 | ✅ Implemented |
| [e2e-test-plan.md](e2e-test-plan.md) | Full user journeys (Cypress) — device lifecycle, alert, WebSocket | 39 | ✅ Implemented |

---

## Performance & Load

| ไฟล์ | ขอบเขต | Tests | สถานะ |
|------|--------|-------|-------|
| [performance-test-plan.md](performance-test-plan.md) | Normal load (50 VU), Kafka throughput, Redis cache, WebSocket broadcast | 16 | ✅ Implemented |
| [load-test-plan.md](load-test-plan.md) | Ramp-up (0→500 VU), spike, soak (2 hr), Kafka consumer, multi-tenant | 22 | ✅ Implemented |
| [regression-test-plan.md](regression-test-plan.md) | API contract, HTTP status, auth, RBAC, multi-tenant, migration, rate limit, WebSocket | 55 | ✅ Implemented |
