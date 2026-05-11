# Backend Integration Test Plan — Sentinel IoT Platform

**Stack:** Java 17 · Spring Boot 3 · JUnit 5 · Testcontainers · MockMvc  
**สถานะ:** ✅ ผ่านทั้งหมด 34/34 tests  
**หลักการ:** ทดสอบหลาย component พร้อมกันผ่าน Spring Application Context จริงและ infrastructure containers จริง

---

## Infrastructure (Testcontainers)

| Container | Image | วัตถุประสงค์ |
|-----------|-------|------------|
| PostgreSQL | `postgres:16-alpine` | persistent storage, Flyway migrations V1–V10 |
| Redis | `redis:7-alpine` | device status cache, telemetry cache, replay queue |
| Mosquitto | `eclipse-mosquitto:2` | MQTT broker สำหรับ integration |

**Lifecycle:** Container ทั้งหมด start ครั้งเดียวใน `static {}` initializer ของ `BaseIntegrationTest` และรันตลอดอายุ JVM เพื่อให้ Spring ApplicationContext cache ใช้ port เดิมได้ตลอด

---

## `BaseIntegrationTest` — Base Class

```
@SpringBootTest + @AutoConfigureMockMvc
```

ทุก Integration Test class extends `BaseIntegrationTest` เพื่อ:
- รับ Spring ApplicationContext ที่ configured ด้วย port ของ containers จริง
- ใช้ `@DynamicPropertySource` inject datasource URL, Redis host/port, MQTT broker URL
- ใช้ `MockMvc` สำหรับ HTTP call โดยไม่ต้องเปิด port จริง

**Properties พิเศษ (test/application.properties):**
- `spring.kafka.listener.auto-startup=false` — ปิด Kafka consumer (ไม่มี Kafka container)
- `management.prometheus.metrics.export.enabled=true` — เปิด Prometheus endpoint
- `init.admin.password=admin123` / `init.operator.password=op123` — สร้าง seed users

---

## โครงสร้างไฟล์ Test

```
backend/src/test/java/com/sentinel/iot/
├── BaseIntegrationTest.java                  (base class — infrastructure setup)
├── SecurityFilterTest.java                   (5 tests)
├── SecurityIntegrationTest.java              (11 tests)
├── AuthControllerIntegrationTest.java        (4 tests)
├── DeviceControllerIntegrationTest.java      (2 tests)
├── RedisServiceIntegrationTest.java          (7 tests)
└── ReplayConsistencyTest.java                (5 tests)
```

---

## รายละเอียด Test แต่ละไฟล์

---

### 1. `SecurityFilterTest` — 5 tests

ทดสอบ JWT security filter chain ว่าป้องกัน/อนุญาต request ได้ถูกต้อง  
**ไม่ต้องการ auth token จริง** — ทดสอบเฉพาะพฤติกรรมของ filter

| Test | Request | ผลที่คาดหวัง |
|------|---------|------------|
| `requestWithoutToken_shouldReturn403` | `GET /api/devices` ไม่มี header | 403 Forbidden |
| `requestWithMalformedToken_shouldReturn403` | `Authorization: Bearer not.a.valid.jwt` | 403 Forbidden |
| `requestWithWrongScheme_shouldReturn403` | `Authorization: Basic dXNlcjpwYXNz` | 403 Forbidden |
| `healthEndpoint_shouldBePublic` | `GET /actuator/health` ไม่มี token | 200 OK |
| `loginEndpoint_shouldBePublic` | `GET /api/v1/auth/login` (GET method) | 405 Method Not Allowed (endpoint มีอยู่ แต่รับเฉพาะ POST) |

---

### 2. `SecurityIntegrationTest` — 11 tests

ทดสอบ security ครบวงจร ครอบคลุม JWT flow, RBAC, และ request tracing  
**มี TenantContext setup:** `@BeforeEach TenantContext.set(a0000000-...)` + `@AfterEach TenantContext.clear()`

**หมวด Authentication (3 tests):**

| Test | Scenario | ผลที่คาดหวัง |
|------|----------|------------|
| `login_producesJwtWithCorrectRole` | POST login ด้วย admin/admin123 | 200 OK, body มี `accessToken` และ `role: ADMIN` |
| `protectedEndpoint_withValidToken_isAccessible` | login → ใช้ token ที่ได้เรียก `GET /api/v1/devices` | 200 OK |
| `protectedEndpoint_withExpiredOrTamperedToken_returns403` | ต่อ string ด้านหลัง token → signature ไม่ตรง | 403 Forbidden |

**หมวด RBAC (4 tests):**

| Test | Role | Endpoint | ผลที่คาดหวัง |
|------|------|----------|------------|
| `adminCanCreateDevice` | ADMIN | `POST /api/v1/devices` | 201 Created |
| `operatorCannotCreateDevice` | OPERATOR | `POST /api/v1/devices` | 403 Forbidden |
| `operatorCanReadDevices` | OPERATOR | `GET /api/v1/devices` | 200 OK |
| `operatorCanReadAlerts` | OPERATOR | `GET /api/v1/alerts` | 200 OK |

**หมวด Public Endpoints (2 tests):**

| Test | Endpoint | ผลที่คาดหวัง |
|------|----------|------------|
| `swaggerUi_isPubliclyAccessible` | `GET /swagger-ui.html` | 3xx Redirect |
| `prometheusEndpoint_isPubliclyAccessible` | `GET /actuator/prometheus` | 200 OK |

**หมวด Request Tracing (2 tests):**

| Test | Scenario | ผลที่คาดหวัง |
|------|----------|------------|
| `requestId_headerIsEchoedInResponse` | ส่ง `X-Request-ID: test-trace-123` | response header `X-Request-ID: test-trace-123` |
| `requestWithoutRequestId_responseStillContainsGeneratedId` | ไม่ส่ง `X-Request-ID` | response header `X-Request-ID` ยังมี (server สร้างให้) |

---

### 3. `AuthControllerIntegrationTest` — 4 tests

ทดสอบ authentication flow ครบวงจร รวมถึง refresh token ผ่าน HttpOnly cookie

**คำสำคัญ:** Refresh token อยู่ใน HttpOnly cookie ชื่อ `sentinel_refresh_token` ไม่ใช่ใน response body

| Test | Scenario | ผลที่คาดหวัง |
|------|----------|------------|
| `login_withValidCredentials_returnsAccessAndRefreshTokens` | POST login ด้วย admin/admin123 | 200 OK, body มี `accessToken` + `role: ADMIN`, response มี cookie `sentinel_refresh_token` |
| `login_withInvalidCredentials_returns401` | POST login ด้วย password ผิด | 401 Unauthorized |
| `refreshToken_withValidToken_returnsNewTokens` | login ด้วย operator → ส่ง refresh cookie กลับ | 200 OK, body มี `accessToken` ใหม่ |
| `refreshToken_withInvalidToken_returns400` | ส่ง cookie ที่มีค่า `not-a-valid-token` | 400 Bad Request (rotateRefreshToken throw IllegalArgumentException → GlobalExceptionHandler) |

---

### 4. `DeviceControllerIntegrationTest` — 2 tests

ทดสอบ Device REST API end-to-end กับ PostgreSQL จริง  
**มี TenantContext setup:** ใช้ org UUID `a0000000-0000-0000-0000-000000000001` (Flyway-seeded)

| Test | Role | Scenario | ผลที่คาดหวัง |
|------|------|----------|------------|
| `shouldCreateAndFetchDevice` | ADMIN | POST สร้าง device "integration-sensor" → GET ดึงรายการ | 201 Created, GET 200 OK มี device ใหม่ใน JSON array |
| `operatorCannotCreateDevice` | OPERATOR | POST พยายามสร้าง device | 403 Forbidden |

---

### 5. `RedisServiceIntegrationTest` — 7 tests

ทดสอบ `RedisService` operations กับ Redis container จริงผ่าน Lettuce client

**หมวด Device Status (3 tests):**

| Test | Scenario | ผลที่ตรวจสอบ |
|------|----------|------------|
| `setAndGetDeviceStatus_roundtrips` | set "ONLINE" → get | ได้ค่า "ONLINE" |
| `setDeviceStatus_canBeOverwritten` | set "ONLINE" → set "OFFLINE" → get | ได้ค่า "OFFLINE" |
| `getDeviceStatus_returnsNullForUnknownDevice` | get device ที่ไม่มีข้อมูล | คืน null |

**หมวด Latest Telemetry (4 tests):**

| Test | Scenario | ผลที่ตรวจสอบ |
|------|----------|------------|
| `setAndGetLatestTelemetry_roundtrips` | set temperature=72.5, humidity=55.0, motion=true, smokePpm=120.0 | ได้ค่าตรงกันทุก field รวมถึง timestamp `ts` |
| `setLatestTelemetry_withNullOptionalFields_storesDefaults` | set motion=null, smokePpm=null | ได้ motion="false", smokePpm="0.0" |
| `setLatestTelemetry_overwritesPreviousReading` | set ครั้งแรก → set ครั้งที่สอง | ได้ค่าจากการ set ครั้งล่าสุด |
| `getLatestTelemetry_returnsEmptyMapForUnknownDevice` | get device ที่ไม่มีข้อมูล | คืน empty map |

---

### 6. `ReplayConsistencyTest` — 5 tests

ทดสอบ Redis → PostgreSQL replay queue ว่าดึงข้อมูลไปบันทึกครบถ้วนและจัดการ partial failure ได้

**setUp:** `stringRedisTemplate.delete("sentinel:replay:queue")` — clear queue ก่อนทุก test เพื่อป้องกัน state รั่วระหว่าง tests

**หมายเหตุ:** `ReplayQueueService` อยู่ใน `@Deprecated` state — รอ Kafka DLQ path รับงาน 100% ก่อนจะถูกลบ

| Test | Scenario | ผลที่ตรวจสอบ |
|------|----------|------------|
| `drain_emptyQueue_doesNothing` | queue ว่าง → drain | จำนวน rows ใน DB ไม่เปลี่ยน |
| `drain_singleMessage_persistsToDb` | push 1 message → drain | queue size = 0, DB มี row ที่มี deviceId และ temperature ถูกต้อง |
| `drain_multipleBatches_allMessagesEventuallyPersisted` | push 15 messages → drain ครั้งเดียว | queue size = 0 |
| `drain_malformedMessage_requeuesAndContinues` | push good + bad + good → drain | bad message อยู่ใน queue (size=1), 2 good messages persist ใน DB |
| `pushToReplayQueue_respectsMaxQueueSize` | push 7 messages โดย check size ก่อน push (max=5) | queue size ≤ 5 |

---

## สรุปภาพรวม

| Test Class | Tests | Component ที่ทดสอบ | Infrastructure ที่ใช้ |
|-----------|-------|-------------------|--------------------|
| `SecurityFilterTest` | 5 | `JwtAuthFilter` | Spring Context |
| `SecurityIntegrationTest` | 11 | Spring Security, RBAC, RequestIdFilter | Spring Context + PostgreSQL |
| `AuthControllerIntegrationTest` | 4 | `AuthController`, `JwtService`, Refresh Token | Spring Context + PostgreSQL |
| `DeviceControllerIntegrationTest` | 2 | `DeviceController`, `DeviceService`, PostgreSQL | Spring Context + PostgreSQL |
| `RedisServiceIntegrationTest` | 7 | `RedisService` | Redis |
| `ReplayConsistencyTest` | 5 | `ReplayQueueService`, `TelemetryRepository` | Redis + PostgreSQL |
| **รวม** | **34** | | |

---

## หลักการออกแบบ

- **Static container lifecycle** — containers start ครั้งเดียวต่อ JVM run เพื่อให้ Spring context cache ใช้งานได้ ไม่มีปัญหา port เปลี่ยนระหว่าง test classes
- **TenantContext isolation** — tests ที่ใช้ `@WithMockUser` (bypass JwtAuthFilter) ต้อง set TenantContext ด้วยตัวเองใน `@BeforeEach` และ clear ใน `@AfterEach`
- **Flyway migrations** — database schema และ seed data (V1–V10) ถูก apply อัตโนมัติเมื่อ container start ครั้งแรก
- **Redis isolation** — test ที่เกี่ยวกับ queue ต้อง delete key ก่อนรันเพื่อป้องกัน state ค้างจาก test ก่อนหน้า
