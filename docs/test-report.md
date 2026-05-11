# Test Report — Sentinel IoT Platform (Backend)

**วันที่รัน:** 2026-05-12  
**สถานะ:** ✅ ผ่านทั้งหมด  
**ผลรวม:** 61 tests | 0 failures | 0 errors | 0 skipped

---

## สรุปผลการทดสอบ

| ประเภท | Test Classes | Test Cases | ผล |
|--------|-------------|------------|-----|
| Unit Tests | 5 | 24 | ✅ ผ่าน |
| Integration Tests | 6 | 34 | ✅ ผ่าน |
| Concurrency Tests | 1 | 3 | ✅ ผ่าน |
| **รวม** | **12** | **61** | **✅ ผ่านทั้งหมด** |

---

## 1. Unit Tests (24 tests)

ทดสอบ business logic ของแต่ละ component แบบ isolation ด้วย Mockito mock
**ไม่ต้องการ** Spring Application Context หรือ Docker container

### AlertServiceTest — 5 tests ✅

| Test | คำอธิบาย |
|------|----------|
| `alertWithTemperatureAboveThreshold_shouldTriggerAlert` | ค่า temperature เกิน threshold → สร้าง alert |
| `alertWithTemperatureBelowThreshold_shouldNotTrigger` | ค่า temperature ปกติ → ไม่สร้าง alert |
| `alertWithSmokeAboveThreshold_shouldTriggerAlert` | ค่า smoke ppm เกิน threshold → สร้าง alert |
| `alertWithMotionDetected_shouldTriggerAlert` | ตรวจพบ motion → สร้าง alert |
| `alertWithAllSensorsNormal_shouldNotTriggerAnyAlert` | ค่าทุกตัวปกติ → ไม่สร้าง alert ใด |

### DeviceServiceTest — 2 tests ✅

| Test | คำอธิบาย |
|------|----------|
| `createDevice_withValidRequest_shouldSaveAndReturn` | สร้าง device ด้วยข้อมูลถูกต้อง → save และคืน entity |
| `createDevice_withDuplicateName_shouldThrowException` | ชื่อ device ซ้ำ → throw `IllegalArgumentException` |

### MqttConsumerServiceTest — 6 tests ✅

| Test | คำอธิบาย |
|------|----------|
| `validPayload_isProcessedAndSaved` | MQTT payload ถูกต้อง → parse และ persist ลง DB |
| `invalidJson_isRoutedToDlq` | JSON ผิด format → route ไป Dead Letter Queue |
| `payloadMissingRequiredFields_isRoutedToDlq` | ขาด required field → route ไป DLQ |
| `payloadWithEdgeMetadata_isPersistedCorrectly` | payload มี edge metadata → บันทึกครบถ้วน |
| `payloadWithV2ReadingsMap_isPersistedCorrectly` | payload v2 ที่มี readings map → บันทึกถูกต้อง |
| `dlqMessage_isRequeued` | DLQ message → requeue ลง replay queue |

### TelemetryWebSocketHandlerTest — 5 tests ✅

| Test | คำอธิบาย |
|------|----------|
| `afterConnectionEstablished_sessionIsRegistered` | เปิด connection → session ถูก register |
| `afterConnectionClosed_sessionIsRemoved` | ปิด connection → session ถูกลบออก |
| `broadcastLocal_sendsToOpenSessions` | broadcast → ส่งเฉพาะ session ที่ยัง open |
| `broadcastLocal_skipsClosedSessions` | session ปิดอยู่ → ข้ามไม่ส่ง |
| `broadcastLocal_filtersMessageByOrgId` | ส่งเฉพาะ session ที่ orgId ตรงกับ message prefix |

### RateLimitFilterTest — 6 tests ✅

| Test | คำอธิบาย |
|------|----------|
| `requestUnderLimit_shouldBeAllowed` | request ยังไม่เกิน limit → ผ่าน |
| `requestOverLimit_shouldBeRejected` | request เกิน rate limit → 429 Too Many Requests |
| `differentIps_haveIndependentBuckets` | IP ต่างกัน → นับ bucket แยกกัน |
| `xForwardedFor_usedAsClientIp` | มี `X-Forwarded-For` header → ใช้ IP นั้นเป็น client IP |
| `actuatorEndpoints_areExemptFromRateLimit` | `/actuator/**` → ยกเว้นจาก rate limit |
| `requestsAfterWindowReset_shouldBeAllowed` | หลัง time window reset → อนุญาตใหม่ |

---

## 2. Integration Tests (34 tests)

ทดสอบหลาย component พร้อมกันผ่าน **Spring Boot Application Context จริง** + **Testcontainers**

### Infrastructure ที่ใช้

| Container | Image | วัตถุประสงค์ |
|-----------|-------|-------------|
| PostgreSQL | `postgres:16-alpine` | persistent storage, Flyway migrations (V1–V10) |
| Redis | `redis:7-alpine` | device status cache, telemetry cache, replay queue |
| Mosquitto | `eclipse-mosquitto:2` | MQTT broker สำหรับ integration |

> **หมายเหตุ:** Container ทั้งหมด start ครั้งเดียวใน static initializer ของ `BaseIntegrationTest` และรันตลอดอายุ JVM เพื่อหลีกเลี่ยงปัญหา Spring context cache กับ port ที่เปลี่ยนไป

### SecurityFilterTest — 5 tests ✅

ทดสอบ JWT security filter ว่าป้องกัน/อนุญาต request ได้ถูกต้อง

| Test | คำอธิบาย | ผลที่คาดหวัง |
|------|----------|-------------|
| `requestWithoutToken_shouldReturn403` | ไม่มี token → ปฏิเสธ | 403 |
| `requestWithMalformedToken_shouldReturn403` | JWT รูปแบบผิด → ปฏิเสธ | 403 |
| `requestWithWrongScheme_shouldReturn403` | ใช้ Basic auth แทน Bearer → ปฏิเสธ | 403 |
| `healthEndpoint_shouldBePublic` | `/actuator/health` → เปิดสาธารณะ | 200 |
| `loginEndpoint_shouldBePublic` | `GET /api/v1/auth/login` → เปิดสาธารณะ (แต่ method ไม่รองรับ GET) | 405 |

### SecurityIntegrationTest — 11 tests ✅

ทดสอบ security แบบ end-to-end ครอบคลุม RBAC, token flow และ request tracing

| Test | คำอธิบาย | ผลที่คาดหวัง |
|------|----------|-------------|
| `login_producesJwtWithCorrectRole` | login สำเร็จ → JWT มี role ถูกต้อง | 200 |
| `protectedEndpoint_withValidToken_isAccessible` | มี token ถูกต้อง → เข้าถึงได้ | 200 |
| `protectedEndpoint_withExpiredOrTamperedToken_returns403` | token ถูก tamper → ปฏิเสธ | 403 |
| `adminCanCreateDevice` | ADMIN role → สร้าง device ได้ | 201 |
| `operatorCannotCreateDevice` | OPERATOR role → สร้าง device ไม่ได้ | 403 |
| `operatorCanReadDevices` | OPERATOR role → ดูรายการ device ได้ | 200 |
| `operatorCanReadAlerts` | OPERATOR role → ดูรายการ alert ได้ | 200 |
| `swaggerUi_isPubliclyAccessible` | Swagger UI → redirect ไปหน้า index | 3xx |
| `prometheusEndpoint_isPubliclyAccessible` | `/actuator/prometheus` → เปิดสาธารณะ | 200 |
| `requestId_headerIsEchoedInResponse` | ส่ง `X-Request-ID` → ได้กลับมาใน response | 200 + header |
| `requestWithoutRequestId_responseStillContainsGeneratedId` | ไม่ส่ง `X-Request-ID` → server สร้างให้อัตโนมัติ | 200 + header |

### AuthControllerIntegrationTest — 4 tests ✅

ทดสอบ authentication flow ครบวงจร รวมถึง refresh token ผ่าน HttpOnly cookie

| Test | คำอธิบาย | ผลที่คาดหวัง |
|------|----------|-------------|
| `login_withValidCredentials_returnsAccessAndRefreshTokens` | login ถูกต้อง → ได้ access token ใน body และ refresh token ใน cookie | 200 |
| `login_withInvalidCredentials_returns401` | password ผิด → ปฏิเสธ | 401 |
| `refreshToken_withValidToken_returnsNewTokens` | ส่ง refresh cookie ถูกต้อง → ได้ access token ใหม่ | 200 |
| `refreshToken_withInvalidToken_returns400` | refresh cookie ไม่ถูกต้อง → error | 400 |

### DeviceControllerIntegrationTest — 2 tests ✅

ทดสอบ Device REST API end-to-end กับ database จริง

| Test | คำอธิบาย | ผลที่คาดหวัง |
|------|----------|-------------|
| `shouldCreateAndFetchDevice` | ADMIN สร้าง device → บันทึกสำเร็จ ดึงกลับมาได้ | 201 → 200 |
| `operatorCannotCreateDevice` | OPERATOR พยายามสร้าง device → ถูกปฏิเสธ | 403 |

### RedisServiceIntegrationTest — 7 tests ✅

ทดสอบ Redis operations กับ Redis container จริง ผ่าน Lettuce client

| Test | คำอธิบาย |
|------|----------|
| `setAndGetLatestTelemetry_roundtrips` | บันทึก/อ่าน telemetry ล่าสุดได้ครบถ้วน |
| `setLatestTelemetry_withNullOptionalFields_storesDefaults` | optional field เป็น null → บันทึก default value |
| `setLatestTelemetry_overwritesPreviousReading` | บันทึกซ้ำ → ค่าใหม่ทับค่าเก่า |
| `setAndGetDeviceStatus_roundtrips` | บันทึก/อ่าน device status ได้ถูกต้อง |
| `setDeviceStatus_canBeOverwritten` | อัปเดต status ซ้ำ → ได้ค่าล่าสุด |
| `getDeviceStatus_returnsNullForUnknownDevice` | device ที่ไม่มีข้อมูล → คืน null |
| `getLatestTelemetry_returnsNullForUnknownDevice` | telemetry ที่ไม่มีข้อมูล → คืน null |

### ReplayConsistencyTest — 5 tests ✅

ทดสอบ Redis → PostgreSQL replay queue ว่าดึงข้อมูลไปบันทึกครบถ้วนและจัดการ partial failure ได้

| Test | คำอธิบาย |
|------|----------|
| `drain_emptyQueue_doesNothing` | queue ว่าง → drain ไม่กระทบ DB |
| `drain_singleMessage_persistsToDb` | push 1 message → drain แล้ว row ปรากฏใน DB |
| `drain_multipleBatches_allMessagesEventuallyPersisted` | push 15 messages → drain ครั้งเดียวล้างหมด |
| `drain_malformedMessage_requeuesAndContinues` | message เสีย 1 ใน 3 → requeue ไว้, 2 ที่ดีถูก persist |
| `pushToReplayQueue_respectsMaxQueueSize` | push เกิน max size → queue ไม่เกิน limit |

---

## 3. Concurrency Tests (3 tests)

ทดสอบ **thread safety** ของ WebSocket handler ที่ต้องรองรับ concurrent connections และ broadcast

### WebSocketConcurrencyTest — 3 tests ✅

| Test | คำอธิบาย | สิ่งที่ตรวจสอบ |
|------|----------|---------------|
| `concurrentSessionsAndBroadcast_noExceptions` | Register 50 sessions + broadcast 20 ข้อความพร้อมกันใน 10 threads | ต้องไม่มี exception หรือ race condition |
| `closedSessions_removedDuringBroadcast` | มี session ที่ปิดแล้วอยู่ใน pool | broadcast ส่งเฉพาะ open session, ข้าม closed โดยอัตโนมัติ |
| `disconnectedSession_notBroadcastedAfterClose` | session disconnect ระหว่าง broadcast | หลัง close → ไม่รับ broadcast อีก |

---

## ปัญหาที่พบและแก้ไข

| # | ปัญหา | ไฟล์ที่แก้ | สาเหตุ |
|---|-------|-----------|--------|
| 1 | Container หยุดทำงานระหว่าง test classes → HikariCP timeout | `BaseIntegrationTest.java` | `@Testcontainers` + `@Container` ใน abstract class stop container หลังแต่ละ class |
| 2 | `ERROR: column "capabilities" is of type jsonb` | `Device.java`, `Telemetry.java` | `@Convert` คืน `String` (VARCHAR) ไม่ตรงกับ `jsonb` ใน PostgreSQL 16 |
| 3 | URL path ผิด → 404 | 4 test files | endpoint ย้ายไปที่ `/api/v1/...` แต่ test ยังใช้ `/api/...` |
| 4 | `organization_id` NOT NULL violation | `DeviceControllerIntegrationTest`, `SecurityIntegrationTest` | `@WithMockUser` bypass `JwtAuthFilter` ทำให้ `TenantContext` ว่าง |
| 5 | `broadcastLocal` ไม่ส่งข้อความ | `WebSocketConcurrencyTest.java` | test ส่ง plain JSON แต่ handler ต้องการ `<orgId>|<payload>` |
| 6 | refresh token อยู่ใน body ที่เป็น null | `AuthControllerIntegrationTest.java` | controller ส่ง token เป็น HttpOnly cookie ไม่ใช่ response body |
| 7 | Redis queue มีข้อมูลค้างระหว่าง test | `ReplayConsistencyTest.java` | ไม่มีการ cleanup `sentinel:replay:queue` ระหว่าง test |
| 8 | `/actuator/prometheus` → 404 | `application.properties` (test) | ขาด `management.prometheus.metrics.export.enabled=true` |
| 9 | User `admin`/`operator` ไม่มีใน test DB | `application.properties` (test) | `DataInitializer` ต้องการ `init.admin.password` / `init.operator.password` |
| 10 | Flyway V3 — UNIQUE INDEX บน partitioned table | `V3__telemetry_partitioning.sql` | PostgreSQL 16 บังคับ partition key ต้องอยู่ใน UNIQUE index |
