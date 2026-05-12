# Regression Test Plan — Sentinel IoT Platform

**ขอบเขต:** Backend REST API · Auth & RBAC · Multi-Tenant Isolation · Database Migrations · Rate Limiting · WebSocket  
**วิธีทดสอบ:** REST Assured + JUnit 5 (extends `BaseIntegrationTest` — Testcontainers)  
**สถานะ:** 📋 วางแผนแล้ว — ยังไม่ได้ implement  
**Environment:** Testcontainers (PostgreSQL 16 + Redis 7 + Mosquitto) — ไม่ต้องใช้ Docker Compose เต็ม  
**Trigger:** ทุก PR ที่แตะ backend code, Flyway migrations, หรือ Spring Boot version  
**เป้าหมาย:** ตรวจให้มั่นใจว่า **behavior ที่มีอยู่ไม่เปลี่ยน** หลัง code changes, dependency upgrades หรือ migration ใหม่

---

## 3.1 API Contract Regression

**เป้าหมาย:** ตรวจว่า response schema และ HTTP status codes ไม่เปลี่ยนโดยไม่ตั้งใจ

| # | Test Case | Endpoint | สิ่งที่ตรวจสอบ |
|---|-----------|----------|--------------|
| 3.1.1 | Login response schema | `POST /auth/login` | Body มี `accessToken`, `role`, `username` — ไม่มี `refreshToken` ใน body |
| 3.1.2 | Device list schema | `GET /api/v1/devices` | Array ของ objects ที่มี `id`, `name`, `status`, `lifecycleStatus`, `organizationId` |
| 3.1.3 | Device detail schema | `GET /api/v1/devices/{id}` | Fields ครบ + `firmwareVersion`, `capabilities` ไม่หาย |
| 3.1.4 | Telemetry schema | `GET /api/v1/telemetry/{id}/latest` | `deviceId`, `readings`, `recordedAt` ทุก record |
| 3.1.5 | Alert schema | `GET /api/v1/alerts` | `id`, `deviceId`, `level`, `message`, `acknowledged`, `createdAt`, `organizationId` |
| 3.1.6 | Stats schema | `GET /api/v1/telemetry/stats` | `lastMinute` และ `replayQueueSize` (ไม่มี field อื่น) |
| 3.1.7 | Error response schema | 400/403/404/500 | ProblemDetail format: `type`, `status`, `detail` — ไม่มี `stackTrace` |
| 3.1.8 | Pagination headers ไม่หาย | `GET /api/v1/devices` | ถ้ามี pagination header เดิม ต้องยังมีอยู่ |

**Implementation note:** ใช้ `JsonSchemaValidator` ของ REST Assured หรือ `assertThat(json).satisfies()` กับ schema ที่ define ไว้ใน JSON Schema files

---

## 3.2 HTTP Status Code Regression

**เป้าหมาย:** ตรวจว่า HTTP status mapping ไม่เปลี่ยน (ป้องกัน silent behavior change ใน `GlobalExceptionHandler`)

| # | Test Case | คำอธิบาย | Status ที่คาดหวัง |
|---|-----------|----------|-----------------|
| 3.2.1 | Valid login | Correct credentials | 200 |
| 3.2.2 | Invalid login | Wrong password | 401 |
| 3.2.3 | Create device as ADMIN | Valid body + ADMIN token | 201 |
| 3.2.4 | Create device as OPERATOR | Valid body + OPERATOR token | 403 |
| 3.2.5 | Device not found | `GET /devices/{random-uuid}` | 404 |
| 3.2.6 | Validation failure | Blank device name | 400 |
| 3.2.7 | Decommissioned device lifecycle transition | `PATCH lifecycle` on DECOMMISSIONED | 400 |
| 3.2.8 | Malformed JSON body | Non-JSON content | 400 |
| 3.2.9 | Refresh with invalid cookie | Random string in cookie | 400 |
| 3.2.10 | Unauthenticated request | No Authorization header | 403 |

---

## 3.3 Authentication & Token Regression

**เป้าหมาย:** ตรวจว่า JWT flow, cookie attributes และ token rotation ยังทำงานถูกต้อง

| # | Test Case | คำอธิบาย | เกณฑ์ PASS |
|---|-----------|----------|-----------|
| 3.3.1 | Access token lifetime | Token ยังใช้งานได้ก่อน expiry (15 min) | 200 ก่อนหมดอายุ |
| 3.3.2 | Refresh token rotation | ทุก `/auth/refresh` call → token ใหม่ + old token revoked | Old token → 400 |
| 3.3.3 | Cookie flags unchanged | `Set-Cookie` header หลัง login | `HttpOnly; Secure; SameSite=Strict` ยังอยู่ครบ |
| 3.3.4 | Logout revokes JTI | Logout → ใช้ access token เดิม | 403 |
| 3.3.5 | Refresh token expiry field | Body ไม่มี `refreshToken` field | `json.has("refreshToken") == false` |
| 3.3.6 | Role ใน response ถูกต้อง | Login as admin vs operator | `role` field ตรงกับ DB role |

---

## 3.4 RBAC Rules Regression

**เป้าหมาย:** ตรวจว่า `SecurityConfig` matchers และ `@PreAuthorize` ไม่ถูก relax โดยไม่ตั้งใจ

| # | Test Case | Action | Role | ผลที่คาดหวัง |
|---|-----------|--------|------|-------------|
| 3.4.1 | Create device | `POST /devices` | OPERATOR | 403 |
| 3.4.2 | Create device | `POST /devices` | ADMIN | 201 |
| 3.4.3 | Patch lifecycle | `PATCH /devices/{id}/lifecycle` | OPERATOR | 403 |
| 3.4.4 | Patch firmware | `PATCH /devices/{id}/firmware` | OPERATOR | 403 |
| 3.4.5 | Read devices | `GET /devices` | OPERATOR | 200 |
| 3.4.6 | Read alerts | `GET /alerts` | OPERATOR | 200 |
| 3.4.7 | Acknowledge alert | `PUT /alerts/{id}/acknowledge` | OPERATOR | 403 |
| 3.4.8 | Acknowledge alert | `PUT /alerts/{id}/acknowledge` | ADMIN | 204 |
| 3.4.9 | Generate enrollment token | `POST /devices/{id}/enrollment-token` | OPERATOR | 403 |
| 3.4.10 | Enrollment (device-side) | `POST /devices/enroll` | No auth | 400 (valid token required) |

---

## 3.5 Multi-Tenant Isolation Regression

**เป้าหมาย:** ตรวจว่า org isolation ไม่ถูก break หลัง refactor หรือ migration ใหม่

| # | Test Case | คำอธิบาย | เกณฑ์ PASS |
|---|-----------|----------|-----------|
| 3.5.1 | Device list isolation | Org B JWT → `GET /devices` ของ org A | Array ว่าง |
| 3.5.2 | Device detail isolation | Org B JWT → `GET /devices/{id ของ org A}` | 404 |
| 3.5.3 | Telemetry isolation | Org B JWT → `GET /telemetry/{device ของ org A}/latest` | 404 |
| 3.5.4 | Alert isolation | Org B JWT → `GET /alerts` (ถ้า org A มี alerts) | Array ว่าง |
| 3.5.5 | RLS policies still exist | ตรวจ `pg_policies` ทุก deployment | 3 policies present: `devices_tenant_isolation`, `alerts_tenant_isolation`, `audit_logs_tenant_isolation` |
| 3.5.6 | Created device → correct org | ADMIN org A สร้าง device → ตรวจ `organizationId` ใน response | = org A's UUID |

---

## 3.6 Database Migration Regression

**เป้าหมาย:** ตรวจว่า Flyway migration ใหม่ไม่ break existing data หรือ schema

| # | Test Case | คำอธิบาย | เกณฑ์ PASS |
|---|-----------|----------|-----------|
| 3.6.1 | Migration idempotency | Run migration 2 ครั้ง (baseline-on-migrate) | ไม่ error |
| 3.6.2 | Seed data survives migration | Admin + operator user ยังอยู่หลัง migration ใหม่ | Login ได้ทั้งคู่ |
| 3.6.3 | RLS intact after migration | ตรวจ `ENABLE ROW LEVEL SECURITY` บน tables | `relrowsecurity = true` ใน `pg_class` |
| 3.6.4 | Index coverage | Key indexes ยังอยู่ครบ | ตรวจ `pg_indexes` สำหรับ `idx_alerts_org_id`, `idx_audit_logs_org_id` |
| 3.6.5 | No orphan data | FK constraints หลัง migration | `pg_constraint` ยังมี FK ครบ |

**Implementation note:** ใช้ `JdbcTemplate` เพื่อ query `pg_class`, `pg_policies`, `pg_indexes`, `pg_constraint` โดยตรง

---

## 3.7 Rate Limiting Configuration Regression

**เป้าหมาย:** ตรวจว่า rate limit values ไม่ถูกเปลี่ยนโดยไม่ตั้งใจ

| # | Test Case | คำอธิบาย | เกณฑ์ PASS |
|---|-----------|----------|-----------|
| 3.7.1 | Auth limit = 10 req/min | Request ที่ 11 บน `/auth/login` | 429 |
| 3.7.2 | API limit = 100 req/min | Request ที่ 101 บน `/api/v1/devices` | 429 |
| 3.7.3 | Non-API paths exempt | `/actuator/health` ไม่ถูก rate limit | ไม่มี 429 แม้ > 100 req |
| 3.7.4 | 429 response body format | Rate limit response | JSON `{"error": "Rate limit exceeded..."}` |
| 3.7.5 | `/devices/enroll` ใช้ auth bucket | `/api/v1/devices/enroll` → ใช้ auth bucket (10/min) | Request ที่ 11 → 429 |

---

## 3.8 WebSocket Behavior Regression

**เป้าหมาย:** ตรวจว่า WebSocket handshake และ broadcast behavior ไม่เปลี่ยน

| # | Test Case | คำอธิบาย | เกณฑ์ PASS |
|---|-----------|----------|-----------|
| 3.8.1 | Valid token → handshake accepted | `JwtWebSocketHandshakeInterceptor.beforeHandshake()` | คืน `true`, `orgId` ใน attributes |
| 3.8.2 | Missing token → rejected | ไม่มี `?token=` | คืน `false` |
| 3.8.3 | Message format | Broadcast payload format `"orgId|{json}"` | Receiver parse ได้ถูกต้อง |
| 3.8.4 | Cross-org isolation | Session ของ org A ไม่รับ message ของ org B | 0 cross-org messages |
| 3.8.5 | Closed session cleanup | Session ปิด → ลบออกจาก `sessions` map | Memory ไม่ leak |

---

## Environment

```
Infrastructure: BaseIntegrationTest (Testcontainers — PostgreSQL 16 + Redis 7 + Mosquitto)
CI: GitHub Actions ทุก PR ที่แตะ backend/
Timeout: ไม่เกิน 10 นาที ต่อ test class
```

---

## Tools & Setup

| Tool | วัตถุประสงค์ | Version | Setup |
|------|------------|---------|-------|
| **REST Assured** | API assertions | 5.x | `pom.xml` dependency |
| **JUnit 5** | Test runner | via Spring Boot | included |
| **Testcontainers** | Infrastructure | via `BaseIntegrationTest` | auto-start |
| **JdbcTemplate** | DB schema assertions | via Spring | injected |

### แนะนำ Project Structure

```
backend/src/test/java/com/sentinel/iot/regression/
├── ApiContractRegressionTest.java      # 3.1 + 3.2 (18 tests)
├── AuthRegressionTest.java             # 3.3 (6 tests)
├── RbacRegressionTest.java             # 3.4 (10 tests)
├── MultiTenantRegressionTest.java      # 3.5 (6 tests)
├── MigrationRegressionTest.java        # 3.6 (5 tests)
├── RateLimitRegressionTest.java        # 3.7 (5 tests)
└── WebSocketRegressionTest.java        # 3.8 (5 tests)
```

---

## สรุปจำนวน Test Cases

| หัวข้อ | Test Cases |
|--------|-----------|
| 3.1 API Contract | 8 |
| 3.2 HTTP Status Code | 10 |
| 3.3 Auth & Token | 6 |
| 3.4 RBAC Rules | 10 |
| 3.5 Multi-Tenant Isolation | 6 |
| 3.6 Database Migration | 5 |
| 3.7 Rate Limiting | 5 |
| 3.8 WebSocket Behavior | 5 |
| **รวม** | **55** |
