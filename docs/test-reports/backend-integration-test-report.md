# Backend Integration Test Report

**วันที่รัน:** 2026-05-12  
**สถานะ:** ✅ ผ่านทั้งหมด  
**ผลรวม:** 34 tests | 6 files | 0 failures  
**Framework:** JUnit 5 + Spring Boot Test + Testcontainers

---

## Infrastructure

| Container | Image | วัตถุประสงค์ |
|-----------|-------|-------------|
| PostgreSQL | `postgres:16-alpine` | Persistent storage, Flyway migrations (V1–V10) |
| Redis | `redis:7-alpine` | Device status cache, telemetry cache, replay queue |
| Mosquitto | `eclipse-mosquitto:2` | MQTT broker สำหรับ integration |

> Container ทั้งหมด start ครั้งเดียวใน static initializer ของ `BaseIntegrationTest` และรันตลอดอายุ JVM เพื่อหลีกเลี่ยงปัญหา Spring context cache กับ port ที่เปลี่ยนไป

---

## สรุปผล

| Test Class | Tests | ผล |
|-----------|-------|-----|
| SecurityFilterTest | 5 | ✅ |
| SecurityIntegrationTest | 11 | ✅ |
| AuthControllerIntegrationTest | 4 | ✅ |
| DeviceControllerIntegrationTest | 2 | ✅ |
| RedisServiceIntegrationTest | 7 | ✅ |
| ReplayConsistencyTest | 5 | ✅ |
| **รวม** | **34** | **✅** |

---

## SecurityFilterTest — 5 tests ✅

ทดสอบ JWT security filter ว่าป้องกัน/อนุญาต request ได้ถูกต้อง

| Test | คำอธิบาย | ผลที่คาดหวัง |
|------|----------|-------------|
| `requestWithoutToken_shouldReturn403` | ไม่มี token → ปฏิเสธ | 403 |
| `requestWithMalformedToken_shouldReturn403` | JWT รูปแบบผิด → ปฏิเสธ | 403 |
| `requestWithWrongScheme_shouldReturn403` | ใช้ Basic auth แทน Bearer → ปฏิเสธ | 403 |
| `healthEndpoint_shouldBePublic` | `/actuator/health` → เปิดสาธารณะ | 200 |
| `loginEndpoint_shouldBePublic` | `GET /api/v1/auth/login` → เปิดสาธารณะ (method ไม่รองรับ GET) | 405 |

---

## SecurityIntegrationTest — 11 tests ✅

ทดสอบ security แบบ end-to-end ครอบคลุม RBAC, token flow และ request tracing

| Test | คำอธิบาย | ผลที่คาดหวัง |
|------|----------|-------------|
| `login_producesJwtWithCorrectRole` | Login สำเร็จ → JWT มี role ถูกต้อง | 200 |
| `protectedEndpoint_withValidToken_isAccessible` | มี token ถูกต้อง → เข้าถึงได้ | 200 |
| `protectedEndpoint_withExpiredOrTamperedToken_returns403` | Token ถูก tamper → ปฏิเสธ | 403 |
| `adminCanCreateDevice` | ADMIN role → สร้าง device ได้ | 201 |
| `operatorCannotCreateDevice` | OPERATOR role → สร้าง device ไม่ได้ | 403 |
| `operatorCanReadDevices` | OPERATOR role → ดูรายการ device ได้ | 200 |
| `operatorCanReadAlerts` | OPERATOR role → ดูรายการ alert ได้ | 200 |
| `swaggerUi_isPubliclyAccessible` | Swagger UI → redirect ไปหน้า index | 3xx |
| `prometheusEndpoint_isPubliclyAccessible` | `/actuator/prometheus` → เปิดสาธารณะ | 200 |
| `requestId_headerIsEchoedInResponse` | ส่ง `X-Request-ID` → ได้กลับมาใน response | 200 + header |
| `requestWithoutRequestId_responseStillContainsGeneratedId` | ไม่ส่ง `X-Request-ID` → server สร้างให้อัตโนมัติ | 200 + header |

---

## AuthControllerIntegrationTest — 4 tests ✅

ทดสอบ authentication flow ครบวงจร รวมถึง refresh token ผ่าน HttpOnly cookie

| Test | คำอธิบาย | ผลที่คาดหวัง |
|------|----------|-------------|
| `login_withValidCredentials_returnsAccessAndRefreshTokens` | Login ถูกต้อง → access token ใน body + refresh token ใน cookie | 200 |
| `login_withInvalidCredentials_returns401` | Password ผิด → ปฏิเสธ | 401 |
| `refreshToken_withValidToken_returnsNewTokens` | Refresh cookie ถูกต้อง → access token ใหม่ | 200 |
| `refreshToken_withInvalidToken_returns400` | Refresh cookie ไม่ถูกต้อง → error | 400 |

---

## DeviceControllerIntegrationTest — 2 tests ✅

ทดสอบ Device REST API end-to-end กับ database จริง

| Test | คำอธิบาย | ผลที่คาดหวัง |
|------|----------|-------------|
| `shouldCreateAndFetchDevice` | ADMIN สร้าง device → บันทึกสำเร็จ ดึงกลับมาได้ | 201 → 200 |
| `operatorCannotCreateDevice` | OPERATOR พยายามสร้าง device → ถูกปฏิเสธ | 403 |

---

## RedisServiceIntegrationTest — 7 tests ✅

ทดสอบ Redis operations กับ Redis container จริง ผ่าน Lettuce client

| Test | คำอธิบาย |
|------|----------|
| `setAndGetLatestTelemetry_roundtrips` | บันทึก/อ่าน telemetry ล่าสุดได้ครบถ้วน |
| `setLatestTelemetry_withNullOptionalFields_storesDefaults` | Optional field เป็น null → บันทึก default value |
| `setLatestTelemetry_overwritesPreviousReading` | บันทึกซ้ำ → ค่าใหม่ทับค่าเก่า |
| `setAndGetDeviceStatus_roundtrips` | บันทึก/อ่าน device status ได้ถูกต้อง |
| `setDeviceStatus_canBeOverwritten` | อัปเดต status ซ้ำ → ได้ค่าล่าสุด |
| `getDeviceStatus_returnsNullForUnknownDevice` | Device ที่ไม่มีข้อมูล → คืน null |
| `getLatestTelemetry_returnsNullForUnknownDevice` | Telemetry ที่ไม่มีข้อมูล → คืน null |

---

## ReplayConsistencyTest — 5 tests ✅

ทดสอบ Redis → PostgreSQL replay queue ว่าดึงข้อมูลไปบันทึกครบถ้วนและจัดการ partial failure ได้

| Test | คำอธิบาย |
|------|----------|
| `drain_emptyQueue_doesNothing` | Queue ว่าง → drain ไม่กระทบ DB |
| `drain_singleMessage_persistsToDb` | Push 1 message → drain แล้ว row ปรากฏใน DB |
| `drain_multipleBatches_allMessagesEventuallyPersisted` | Push 15 messages → drain ครั้งเดียวล้างหมด |
| `drain_malformedMessage_requeuesAndContinues` | Message เสีย 1 ใน 3 → requeue ไว้, 2 ที่ดีถูก persist |
| `pushToReplayQueue_respectsMaxQueueSize` | Push เกิน max size → queue ไม่เกิน limit |

---

## ปัญหาที่พบและแก้ไข

| # | ปัญหา | ไฟล์ที่แก้ | สาเหตุ |
|---|-------|-----------|--------|
| 1 | Container หยุดทำงานระหว่าง test classes → HikariCP timeout | `BaseIntegrationTest.java` | `@Testcontainers` + `@Container` ใน abstract class stop container หลังแต่ละ class |
| 2 | `ERROR: column "capabilities" is of type jsonb` | `Device.java`, `Telemetry.java` | `@Convert` คืน `String` (VARCHAR) ไม่ตรงกับ `jsonb` ใน PostgreSQL 16 |
| 3 | URL path ผิด → 404 | 4 test files | Endpoint ย้ายไปที่ `/api/v1/...` แต่ test ยังใช้ `/api/...` |
| 4 | `organization_id` NOT NULL violation | `DeviceControllerIntegrationTest`, `SecurityIntegrationTest` | `@WithMockUser` bypass `JwtAuthFilter` ทำให้ `TenantContext` ว่าง |
| 5 | Redis queue มีข้อมูลค้างระหว่าง test | `ReplayConsistencyTest.java` | ไม่มีการ cleanup `sentinel:replay:queue` ระหว่าง test |
| 6 | `/actuator/prometheus` → 404 | `application.properties` (test) | ขาด `management.prometheus.metrics.export.enabled=true` |
| 7 | User `admin`/`operator` ไม่มีใน test DB | `application.properties` (test) | `DataInitializer` ต้องการ `init.admin.password` / `init.operator.password` |
| 8 | Flyway V3 — UNIQUE INDEX บน partitioned table | `V3__telemetry_partitioning.sql` | PostgreSQL 16 บังคับ partition key ต้องอยู่ใน UNIQUE index |
