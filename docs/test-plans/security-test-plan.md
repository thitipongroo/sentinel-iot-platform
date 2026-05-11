# Security Test Plan — Sentinel IoT Platform

**ขอบเขต:** Backend API · JWT/Session · RBAC · Multi-Tenant · Rate Limiting · MQTT · WebSocket · Input Validation  
**วิธีทดสอบ:** Manual · Automated (JUnit/Testcontainers) · Tool-based (OWASP ZAP, npm audit, mvn dependency-check)  
**สถานะ:** ❌ ยังไม่ได้ดำเนินการ

---

## สรุป Attack Surface ของโปรเจกต์

| พื้นที่ | กลไกป้องกันที่มีอยู่ |
|--------|-------------------|
| Authentication | JWT HS256 · 15 min expiry · JTI blocklist ใน Redis |
| Session | Refresh token (SHA-256 hash ใน DB) · HttpOnly cookie · Token family revocation |
| Authorization | Spring Security RBAC · `@PreAuthorize` · Filter chain |
| Multi-Tenant | TenantContext (ThreadLocal) · PostgreSQL RLS (V7) · TenantRlsAspect |
| Rate Limiting | Bucket4j per-IP · auth: 10 req/min · API: 100 req/min |
| Input Validation | Bean Validation `@Valid` · semver pattern · `@NotBlank` |
| MQTT | Username/password (optional) · DLQ routing · default plaintext TCP |
| WebSocket | JWT ผ่าน query param · orgId session filtering |
| Secrets | ทุก secret ผ่าน env var · ไม่มี hardcode |

---

## หมวดที่ 1 — Authentication & JWT (9 tests)

### Attack vectors: token forgery, algorithm confusion, expiry bypass, revocation bypass

| # | Test Case | วิธีทดสอบ | ผลที่คาดหวัง | ความรุนแรงถ้าล้มเหลว |
|---|-----------|----------|-------------|-------------------|
| 1.1 | Token ที่ algorithm เปลี่ยนเป็น `none` ถูกปฏิเสธ | ส่ง JWT ที่มี `alg: none` ใน header | 403 Forbidden | Critical |
| 1.2 | Token ที่ signature ถูกแก้ถูกปฏิเสธ | แก้ payload แล้วส่งทับ (signature ไม่ตรง) | 403 Forbidden | Critical |
| 1.3 | Token หมดอายุ (>15 นาที) ถูกปฏิเสธ | ใช้ token ที่ expire แล้ว หรือ mock `exp` claim อดีต | 403 Forbidden | High |
| 1.4 | Token ที่ถูก revoke ผ่าน JTI blocklist ถูกปฏิเสธ | logout แล้วใช้ access token เดิม | 403 Forbidden | High |
| 1.5 | Token ของ org A ใช้เรียก resource ของ org B ไม่ได้ | สร้าง token 2 org ต่างกัน แล้วข้าม orgId | 403 / 404 | Critical |
| 1.6 | Token ที่ไม่มี `role` claim ถูกปฏิเสธ | สร้าง JWT ที่ขาด claim `role` | 403 Forbidden | High |
| 1.7 | Token ที่ `role` ถูกปลอมเป็น ADMIN ถูกปฏิเสธ | แก้ `role: OPERATOR` → `role: ADMIN` แต่ signature เดิม | 403 Forbidden | Critical |
| 1.8 | Key rotation — token เก่า (signed ด้วย `JWT_PREVIOUS_SECRET`) ยังใช้ได้ | set `JWT_PREVIOUS_SECRET` = key เก่า → ส่ง token เก่า | 200 OK | Medium |
| 1.9 | Token ที่ไม่ได้ส่ง Bearer scheme ถูกปฏิเสธ | ส่ง `Authorization: Basic abc` แทน `Bearer` | 403 Forbidden | Medium |

---

## หมวดที่ 2 — Refresh Token & Session Management (7 tests)

### Attack vectors: token theft replay, session fixation, reuse detection

| # | Test Case | วิธีทดสอบ | ผลที่คาดหวัง | ความรุนแรงถ้าล้มเหลว |
|---|-----------|----------|-------------|-------------------|
| 2.1 | Refresh token ปลอม (random string) ถูกปฏิเสธ | ส่ง cookie `sentinel_refresh_token=random-string` | 400 Bad Request | High |
| 2.2 | Refresh token ที่ใช้ไปแล้ว (revoked) ถูกปฏิเสธ | refresh 1 ครั้ง แล้วส่ง token เดิมซ้ำ | 400 Bad Request | High |
| 2.3 | Reuse detection — ส่ง refresh token เดิมซ้ำ → revoke ทุก session | นำ token เก่ากลับมาใช้หลังจาก rotate แล้ว | 400 + ทุก session ถูก revoke | Critical |
| 2.4 | Refresh token ไม่รั่วใน response body | เรียก `/auth/login` → response body ต้องไม่มี `refreshToken` field | ไม่มี field ใน body | High |
| 2.5 | Refresh token cookie มี HttpOnly flag | ตรวจ `Set-Cookie` header หลัง login | `HttpOnly; Secure; SameSite=Strict` | High |
| 2.6 | Logout revoke refresh token ทุก session ของ user | login 2 device → logout จาก device 1 → device 2 refresh → 400 | 400 Bad Request | High |
| 2.7 | Refresh token หมดอายุ (>7 วัน) ถูกปฏิเสธ | ใช้ token ที่ `expiresAt` ผ่านไปแล้ว | 400 Bad Request | Medium |

---

## หมวดที่ 3 — Authorization / RBAC (8 tests)

### Attack vectors: privilege escalation, IDOR, missing authorization check

| # | Test Case | วิธีทดสอบ | ผลที่คาดหวัง | ความรุนแรงถ้าล้มเหลว |
|---|-----------|----------|-------------|-------------------|
| 3.1 | OPERATOR สร้าง device ไม่ได้ | `POST /api/v1/devices` ด้วย OPERATOR token | 403 Forbidden | High |
| 3.2 | OPERATOR แก้ lifecycle ไม่ได้ | `PATCH /api/v1/devices/{id}/lifecycle` ด้วย OPERATOR token | 403 Forbidden | High |
| 3.3 | OPERATOR แก้ firmware ไม่ได้ | `PATCH /api/v1/devices/{id}/firmware` ด้วย OPERATOR token | 403 Forbidden | High |
| 3.4 | OPERATOR acknowledge alert ไม่ได้ | `PUT /api/v1/alerts/{id}/acknowledge` ด้วย OPERATOR token | 403 Forbidden | High |
| 3.5 | OPERATOR สร้าง enrollment token ไม่ได้ | `POST /api/v1/devices/{id}/enrollment-token` ด้วย OPERATOR token | 403 Forbidden | High |
| 3.6 | OPERATOR อ่าน device list ได้ | `GET /api/v1/devices` ด้วย OPERATOR token | 200 OK | Medium |
| 3.7 | ไม่มี token เรียก protected endpoint ไม่ได้ | `GET /api/v1/devices` โดยไม่ส่ง Authorization header | 403 Forbidden | High |
| 3.8 | IDOR — เรียก device ของ org อื่นด้วย ID ตรงๆ | ADMIN org A เรียก `GET /api/v1/devices/{id ของ org B}` | 404 Not Found | Critical |

---

## หมวดที่ 4 — Multi-Tenant Isolation (6 tests)

### Attack vectors: tenant data leakage, TenantContext pollution, RLS bypass

| # | Test Case | วิธีทดสอบ | ผลที่คาดหวัง | ความรุนแรงถ้าล้มเหลว |
|---|-----------|----------|-------------|-------------------|
| 4.1 | org A ดู device list ของ org B ไม่ได้ | login org A → `GET /api/v1/devices` → ต้องไม่เห็น device ของ org B | รายการว่าง / เฉพาะ org A | Critical |
| 4.2 | org A ดู alert ของ org B ไม่ได้ | login org A → `GET /api/v1/alerts` → ต้องไม่เห็น alert ของ org B | รายการว่าง / เฉพาะ org A | Critical |
| 4.3 | org A ดู telemetry ของ device org B ไม่ได้ | login org A → `GET /api/v1/telemetry/{deviceId ของ org B}/latest` | 404 Not Found | Critical |
| 4.4 | PostgreSQL RLS ป้องกัน cross-tenant query โดยตรง | ใช้ DB connection โดยตรง (ไม่ผ่าน app) query โดยไม่ set `app.org_id` | ไม่มี row returned | Critical |
| 4.5 | TenantContext ถูก clear หลังจบ request | ทำ 2 requests ต่อเนื่อง: req 1 set org A, req 2 ไม่ set → req 2 ต้องไม่ได้ข้อมูล org A | TenantContext ว่าง | High |
| 4.6 | `orgId` ใน JWT ถูกแก้แล้ว signature ไม่ตรง → ปฏิเสธ | แก้ `orgId` claim ใน payload แต่ไม่ re-sign | 403 Forbidden | Critical |

---

## หมวดที่ 5 — Rate Limiting (5 tests)

### Attack vectors: brute force login, API abuse, IP spoofing

| # | Test Case | วิธีทดสอบ | ผลที่คาดหวัง | ความรุนแรงถ้าล้มเหลว |
|---|-----------|----------|-------------|-------------------|
| 5.1 | Auth endpoint เกิน 10 req/min → 429 | ส่ง `POST /api/v1/auth/login` 11 ครั้งในนาทีเดียว | 429 Too Many Requests | High |
| 5.2 | API endpoint เกิน 100 req/min → 429 | ส่ง `GET /api/v1/devices` 101 ครั้งในนาทีเดียว | 429 Too Many Requests | Medium |
| 5.3 | X-Forwarded-For spoofing ไม่ข้าม rate limit | ส่ง `X-Forwarded-For: 1.2.3.4` โดยไม่ผ่าน trusted proxy | นับจาก real IP ไม่ใช่ spoofed IP | High |
| 5.4 | IP ต่างกัน มี bucket แยกกัน | ส่ง 10 req จาก IP A + 10 req จาก IP B → ทั้งคู่ผ่าน | ไม่ถูก block | Medium |
| 5.5 | หลัง window reset (1 นาที) รับ request ใหม่ได้ | รอ window reset แล้วส่งอีกครั้ง | 200 OK | Medium |

---

## หมวดที่ 6 — Input Validation (7 tests)

### Attack vectors: SQL injection, XSS via stored data, schema bypass

| # | Test Case | วิธีทดสอบ | ผลที่คาดหวัง | ความรุนแรงถ้าล้มเหลว |
|---|-----------|----------|-------------|-------------------|
| 6.1 | Firmware version ที่ไม่ใช่ semver ถูกปฏิเสธ | `PATCH /firmware` body `{"firmwareVersion": "not-a-version"}` | 400 Bad Request | Medium |
| 6.2 | Device name ว่างถูกปฏิเสธ | `POST /devices` body `{"name": ""}` | 400 Bad Request | Medium |
| 6.3 | SQL injection ผ่าน device name | `POST /devices` name = `'; DROP TABLE devices; --` | 400 Bad Request / stored as literal string (JPA parameterized query) | High |
| 6.4 | XSS payload ใน device name ถูก sanitize | สร้าง device ชื่อ `<script>alert(1)</script>` → ดึงกลับมา | ได้ string literal ไม่ใช่ executed script | High |
| 6.5 | Enum ผิดค่าถูกปฏิเสธ | `PATCH /lifecycle` body `{"lifecycleStatus": "INVALID"}` | 400 Bad Request | Low |
| 6.6 | TelemetryMessage ที่ขาด deviceId ถูก route ไป DLQ | publish MQTT payload โดยไม่มี field `deviceId` | message ไป DLQ ไม่ crash | Medium |
| 6.7 | Large payload ถูกจำกัด (DoS prevention) | ส่ง request body ขนาด 10 MB | 413 Payload Too Large | Medium |

---

## หมวดที่ 7 — MQTT Security (4 tests)

### Attack vectors: topic spoofing, identity impersonation, plaintext interception

| # | Test Case | วิธีทดสอบ | ผลที่คาดหวัง | ความรุนแรงถ้าล้มเหลว |
|---|-----------|----------|-------------|-------------------|
| 7.1 | Device ปลอม publish payload ในชื่อ device อื่น | publish payload ที่มี `deviceId` ของ device อื่น | บันทึกภายใต้ deviceId นั้น (ยืนยัน gap นี้มีจริง) | High |
| 7.2 | MQTT connection ปัจจุบันเป็น plaintext TCP | ตรวจ broker URL ว่าเป็น `tcp://` ไม่ใช่ `tls://` | พบว่าเป็น plaintext (document gap) | High |
| 7.3 | Malformed JSON ถูก route ไป DLQ ไม่ crash service | publish `{ invalid json }` ไปที่ topic | service ไม่ crash, message อยู่ใน DLQ | Medium |
| 7.4 | Broker credential ว่าง (anonymous) ได้รับการ config | ตรวจ config ว่า `MQTT_USER`/`MQTT_PASS` ถูก set | broker ต้อง require authentication | High |

---

## หมวดที่ 8 — WebSocket Security (4 tests)

### Attack vectors: unauthorized connection, cross-tenant message leakage

| # | Test Case | วิธีทดสอบ | ผลที่คาดหวัง | ความรุนแรงถ้าล้มเหลว |
|---|-----------|----------|-------------|-------------------|
| 8.1 | WebSocket connection โดยไม่มี JWT token ถูกปฏิเสธ | เปิด connection ไปที่ `/ws/telemetry` โดยไม่มี `?token=` | WebSocket handshake ถูกปฏิเสธ | High |
| 8.2 | WebSocket connection ด้วย JWT ผิด/หมดอายุ ถูกปฏิเสธ | ส่ง `?token=invalid` | WebSocket handshake ถูกปฏิเสธ | High |
| 8.3 | org A ไม่รับ message ของ org B ผ่าน WebSocket | connect 2 clients ต่าง orgId → broadcast ของ org B → org A ต้องไม่รับ | org A ไม่เห็น message | Critical |
| 8.4 | JWT ใน query param ไม่ถูก log ใน access log | ตรวจ application log ว่าไม่มี token value ปรากฏ | URL ถูก sanitize ใน log | Medium |

---

## หมวดที่ 9 — Error Handling & Information Disclosure (4 tests)

### Attack vectors: stack trace leakage, user enumeration, path disclosure

| # | Test Case | วิธีทดสอบ | ผลที่คาดหวัง | ความรุนแรงถ้าล้มเหลว |
|---|-----------|----------|-------------|-------------------|
| 9.1 | Login ด้วย username ที่ไม่มีอยู่ vs password ผิด → response เหมือนกัน | ทดสอบทั้งสองกรณี เทียบ response body และ timing | response เหมือนกัน (ป้องกัน user enumeration) | Medium |
| 9.2 | 500 error ไม่แสดง stack trace ใน response | ทำให้เกิด server error จงใจ → ดู response body | ได้รับ generic error message ไม่มี stack trace | High |
| 9.3 | เรียก endpoint ที่ไม่มีอยู่ → ไม่เปิดเผย internal path | `GET /api/v1/nonexistent` | 404 with generic message | Low |
| 9.4 | Swagger UI ไม่เปิดเผย sensitive endpoint detail ใน production | ตรวจว่า swagger ถูก disable ใน production profile | Swagger disable เมื่อ `spring.profiles.active=prod` | Medium |

---

## หมวดที่ 10 — Dependency Vulnerabilities (2 tests)

### Attack vectors: known CVEs ใน dependencies

| # | Test Case | วิธีทดสอบ | เครื่องมือ |
|---|-----------|----------|-----------|
| 10.1 | Backend dependencies ไม่มี known CVEs | `mvn dependency-check:check` | OWASP Dependency-Check |
| 10.2 | Frontend dependencies ไม่มี known CVEs | `npm audit --audit-level=high` | npm audit |

---

## สรุปภาพรวม

| หมวด | Tests | เครื่องมือ |
|------|-------|-----------|
| Authentication & JWT | 9 | Manual + JUnit |
| Refresh Token & Session | 7 | Manual + JUnit |
| Authorization / RBAC | 8 | Manual + JUnit (มีบางส่วนใน SecurityIntegrationTest แล้ว) |
| Multi-Tenant Isolation | 6 | JUnit + DB query |
| Rate Limiting | 5 | Manual + JUnit (มีบางส่วนใน RateLimitFilterTest แล้ว) |
| Input Validation | 7 | Manual + OWASP ZAP |
| MQTT Security | 4 | Manual |
| WebSocket Security | 4 | Manual + JUnit |
| Error Handling | 4 | Manual |
| Dependency Vulnerabilities | 2 | npm audit / mvn dependency-check |
| **รวม** | **56 tests** | |

---

## ช่องโหว่ที่พบจากการวิเคราะห์โค้ด (ก่อน implement tests)

| # | ช่องโหว่ | ระดับ | ไฟล์ | หมายเหตุ |
|---|---------|-------|------|---------|
| V1 | MQTT broker ใช้ `tcp://` (plaintext) เป็น default | 🔴 High | `MqttConfig.java` | ต้องใช้ `tls://` ใน production |
| V2 | Device สามารถ publish ในชื่อ device อื่นได้ (no per-device topic ACL) | 🔴 High | `MqttConfig.java` | ไม่มี per-device authentication ใน broker |
| V3 | `DB_PASSWORD` มี default value `sentinel` | 🟡 Medium | `application.yml` | อันตรายถ้า dev config รั่วไป production |
| V4 | `TenantRlsAspect` ใช้ string concatenation set `app.org_id` | 🟢 Low | `TenantRlsAspect.java` | UUID ไม่มีอักขระ SQL อันตราย แต่ best practice ควรใช้ parameterized |
| V5 | `TelemetryMessage` ไม่มี Bean Validation | 🟡 Medium | `TelemetryMessage.java` | พึ่ง Jackson type coercion เท่านั้น |
| V6 | JWT ส่งผ่าน WebSocket query param | 🟡 Medium | `JwtWebSocketHandshakeInterceptor.java` | browser limitation — อาจถูก log ใน access log |

---

## ลำดับการ Implement (แนะนำ)

```
Priority 1 — ช่องโหว่ที่พบแล้ว (V1–V6)
  แก้ไขก่อน implement tests เพื่อไม่ให้ tests fail โดยไม่จำเป็น

Priority 2 — Critical tests
  หมวด 1 (JWT) → หมวด 4 (Multi-Tenant) → หมวด 3 (RBAC)

Priority 3 — High severity tests
  หมวด 2 (Session) → หมวด 5 (Rate Limit) → หมวด 8 (WebSocket)

Priority 4 — Medium/Low severity
  หมวด 6 (Input) → หมวด 7 (MQTT) → หมวด 9 (Error) → หมวด 10 (Dependencies)
```
