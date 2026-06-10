# Test Report — Sentinel IoT Platform

**วันที่รัน:** 2026-05-12  
**สถานะ:** ✅ ผ่านทั้งหมด  
**ผลรวม:** 335 tests | 0 failures | 0 errors | 0 skipped

---

## ภาพรวมทั้งระบบ

| แพลตฟอร์ม | ประเภท | Test Files | Tests | ผล | รายละเอียด |
|-----------|--------|-----------|-------|-----|-----------|
| Backend | Unit Tests | 11 | 53 | ✅ | [backend-unit-test-report.md](backend-unit-test-report.md) |
| Backend | Integration Tests | 10 | 51 | ✅ | [backend-integration-test-report.md](backend-integration-test-report.md) |
| Backend | Concurrency Tests | 1 | 3 | ✅ | [backend-concurrency-test-report.md](backend-concurrency-test-report.md) |
| Backend | Security Tests | 8 | 45 | ✅ | [backend-security-test-report.md](backend-security-test-report.md) |
| Backend | Regression Tests | 7 | 55 | ✅ | [backend-regression-test-report.md](backend-regression-test-report.md) |
| Backend | Contract Tests (Pact) | 1 | 5 | ✅ | — |
| Backend | Benchmark Gate (JMH) | 1 | 1 | ✅ | — |
| Backend | Concurrent Load Tests | 1 | 3 | ✅ | — |
| Backend | Chaos Engineering Tests | 1 | 4 | ✅ | — |
| Frontend | Unit Tests | 11 | 76 | ✅ | [frontend-unit-test-report.md](frontend-unit-test-report.md) |
| Frontend | E2E Tests (Cypress) | 7 | 39 | ✅ | [frontend-e2e-test-report.md](frontend-e2e-test-report.md) |
| Load | Cache Read Path (k6) | — | — | ✅ | [load-test-report.md](load-test-report.md) |
| **รวม** | | **59** | **335** | **✅** | |

---

## Backend (220 tests)

| ประเภท | Files | Tests | ผล |
|--------|-------|-------|-----|
| Unit Tests | 11 | 53 | ✅ |
| Integration Tests | 10 | 51 | ✅ |
| Concurrency Tests | 1 | 3 | ✅ |
| Security Tests | 8 | 45 | ✅ |
| Regression Tests | 7 | 55 | ✅ |
| Contract Tests (Pact) | 1 | 5 | ✅ |
| Benchmark Gate (JMH) | 1 | 1 | ✅ |
| Concurrent Load Tests | 1 | 3 | ✅ |
| Chaos Engineering Tests | 1 | 4 | ✅ |
| **รวม** | **41** | **220** | **✅** |

**Framework:** JUnit 5 + Mockito + Spring Boot Test + Testcontainers  
**Infrastructure (integration/security):** PostgreSQL 16 · Redis 7 · Mosquitto 2

---

## Frontend (115 tests)

| ประเภท | Files | Tests | ผล |
|--------|-------|-------|-----|
| Unit Tests | 11 | 76 | ✅ |
| E2E Tests (Cypress) | 7 | 39 | ✅ |
| **รวม** | **18** | **115** | **✅** |

**Framework (Unit):** Jest 30 · React Testing Library 16 · jsdom 26  
**Framework (E2E):** Cypress 13 · Next.js 14 App Router
