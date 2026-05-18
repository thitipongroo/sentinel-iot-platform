# test-plans/ — Sentinel IoT Platform

แผนการทดสอบแยกตาม layer และประเภท ผลการรันดูได้ที่ [test-reports/](../test-reports/)

---

## Backend

| ไฟล์ | ขอบเขต | Tests | สถานะ |
|------|--------|-------|-------|
| [backend-unit-test-plan.md](backend-unit-test-plan.md) | Service, Repository, Filter unit tests | 28 | ✅ Implemented |
| [backend-integration-test-plan.md](backend-integration-test-plan.md) | Spring MVC + Testcontainers integration tests | 75 | ✅ Implemented |
| [backend-concurrency-test-plan.md](backend-concurrency-test-plan.md) | Thread safety, TenantContext isolation, rate limiter concurrency | 3 | ✅ Implemented |
| [security-test-plan.md](security-test-plan.md) | JWT auth, RBAC, multi-tenant isolation, rate limit, WebSocket, error handling | 45 | ✅ Implemented |

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
