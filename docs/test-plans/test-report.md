# Test Report — Sentinel IoT Platform

**วันที่รัน:** 2026-05-12  
**สถานะ:** ✅ ผ่านทั้งหมด  
**ผลรวม:** 137 tests | 0 failures | 0 errors | 0 skipped

---

## สรุปผลการทดสอบ (ภาพรวมทั้งระบบ)

| แพลตฟอร์ม | ประเภท | Test Files | Test Cases | ผล |
|-----------|--------|-----------|------------|-----|
| Backend | Unit Tests | 5 | 24 | ✅ ผ่าน |
| Backend | Integration Tests | 6 | 34 | ✅ ผ่าน |
| Backend | Concurrency Tests | 1 | 3 | ✅ ผ่าน |
| Frontend | Unit Tests | 11 | 76 | ✅ ผ่าน |
| **รวม** | | **23** | **137** | **✅ ผ่านทั้งหมด** |

---

## Backend Tests (61 tests)

### สรุปผล Backend

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
| 5 | `broadcastLocal` ไม่ส่งข้อความ | `WebSocketConcurrencyTest.java` | test ส่ง plain JSON แต่ handler ต้องการ format `orgId\|payload` |
| 6 | refresh token อยู่ใน body ที่เป็น null | `AuthControllerIntegrationTest.java` | controller ส่ง token เป็น HttpOnly cookie ไม่ใช่ response body |
| 7 | Redis queue มีข้อมูลค้างระหว่าง test | `ReplayConsistencyTest.java` | ไม่มีการ cleanup `sentinel:replay:queue` ระหว่าง test |
| 8 | `/actuator/prometheus` → 404 | `application.properties` (test) | ขาด `management.prometheus.metrics.export.enabled=true` |
| 9 | User `admin`/`operator` ไม่มีใน test DB | `application.properties` (test) | `DataInitializer` ต้องการ `init.admin.password` / `init.operator.password` |
| 10 | Flyway V3 — UNIQUE INDEX บน partitioned table | `V3__telemetry_partitioning.sql` | PostgreSQL 16 บังคับ partition key ต้องอยู่ใน UNIQUE index |

---

## Frontend Unit Tests (76 tests)

**วันที่รัน:** 2026-05-12  
**Stack:** Next.js 14 · Jest 30 · React Testing Library 16 · jsdom 26  
**เวลาที่รัน:** 1.3 วินาที

### สรุปผล Frontend Unit Tests

| Test File | Test Cases | ผล |
|-----------|------------|-----|
| `tokenStore.test.js` | 4 | ✅ ผ่าน |
| `store.test.js` | 7 | ✅ ผ่าน |
| `Badge.test.jsx` | 6 | ✅ ผ่าน |
| `Select.test.jsx` | 5 | ✅ ผ่าน |
| `ErrorBoundary.test.jsx` | 6 | ✅ ผ่าน |
| `StatsBar.test.jsx` | 6 | ✅ ผ่าน |
| `AlertList.test.jsx` | 9 | ✅ ผ่าน |
| `client.test.js` | 6 | ✅ ผ่าน |
| `useAuth.test.js` | 8 | ✅ ผ่าน |
| `useWebSocket.test.js` | 7 | ✅ ผ่าน |
| `DeviceTable.test.jsx` | 12 | ✅ ผ่าน |
| **รวม** | **76** | **✅ ผ่านทั้งหมด** |

---

### `tokenStore.test.js` — 4 tests ✅

ทดสอบ in-memory token store ว่าเก็บ access token ออกจาก localStorage (XSS safety)

| Test | สิ่งที่ตรวจสอบ |
|------|--------------|
| `getAccessToken returns null initially` | ก่อน set → คืน null |
| `setAccessToken stores token in memory` | set token → getAccessToken คืนค่าถูกต้อง |
| `clearAccessToken removes token` | set แล้ว clear → คืน null |
| `token is not stored in localStorage` | หลัง setAccessToken → localStorage ไม่มี token |

---

### `store.test.js` — 7 tests ✅

ทดสอบ Zustand store state transitions ทั้งหมด

| Test | สิ่งที่ตรวจสอบ |
|------|--------------|
| `initial state is correct` | ค่าเริ่มต้นทุก field ถูกต้อง |
| `setSelectedDeviceId updates selected` | เรียก setSelectedDeviceId → selectedDeviceId อัปเดต |
| `setFilter updates specific filter key` | setFilter('status', 'ONLINE') → filters.status เปลี่ยน |
| `setFilter does not affect other keys` | เปลี่ยน status → search/lifecycle คงเดิม |
| `resetFilters restores defaults` | เปลี่ยนหลาย filter → resetFilters → กลับเป็น default |
| `setOffline sets isOffline to true` | setOffline(true) → isOffline === true |
| `setOffline(false) clears offline state` | setOffline(false) → isOffline === false |

---

### `Badge.test.jsx` — 6 tests ✅

ทดสอบ visual variant และ className mapping ของ Badge component

| Test | สิ่งที่ตรวจสอบ |
|------|--------------|
| `renders children correctly` | children แสดงใน DOM |
| `applies default variant when no variant given` | ไม่ส่ง variant → class `bg-sentinel-700` |
| `applies success variant` | variant="success" → class `text-sentinel-success` |
| `applies critical variant` | variant="critical" → class `bg-sentinel-danger text-white` |
| `falls back to default for unknown variant` | variant="invalid" → fallback เป็น default class |
| `merges custom className` | className="mt-2" → class ถูก merge |

---

### `Select.test.jsx` — 5 tests ✅

ทดสอบ accessibility และ callback ของ Select component

| Test | สิ่งที่ตรวจสอบ |
|------|--------------|
| `renders label and all options` | label + options ทุกตัวแสดงใน DOM |
| `renders without label when label prop omitted` | ไม่มี label element ใน DOM |
| `calls onChange with selected value` | เลือก option → onChange ถูกเรียกด้วย value ถูกต้อง |
| `label is associated with select via htmlFor/id` | label[for] ตรงกับ select[id] |
| `shows currently selected value` | value="ONLINE" → select แสดง option ที่ถูกเลือก |

---

### `ErrorBoundary.test.jsx` — 6 tests ✅

ทดสอบ error catching, fallback UI และ reset ของ ErrorBoundary class component

| Test | สิ่งที่ตรวจสอบ |
|------|--------------|
| `renders children when no error` | children แสดงปกติ |
| `shows fallback UI when child throws` | component throw → แสดง fallback พร้อม error message |
| `shows label in fallback title when label prop given` | label="Device list" → fallback title เป็น "Device list failed to render" |
| `shows generic message when no label` | ไม่มี label → "Something went wrong" |
| `reset button clears error state and re-renders children` | กด "Try again" → children render ใหม่ |
| `fallback has role="alert" for accessibility` | fallback element มี role="alert" |

---

### `StatsBar.test.jsx` — 6 tests ✅

ทดสอบ calculation logic จาก props ของ StatsBar

| Test | สิ่งที่ตรวจสอบ |
|------|--------------|
| `shows correct total device count` | devices 5 ตัว → "Total Devices" = 5 |
| `calculates online and offline counts correctly` | 3 ONLINE, 2 OFFLINE → card ถูกต้อง |
| `shows critical unacknowledged alert count` | 2 CRITICAL unacked → "Critical Alerts" = 2 |
| `shows 0 for buffered when replayQueueSize is 0` | replayQueueSize=0 → "Buffered" = 0, color = gray |
| `shows warning color for buffered when replayQueueSize > 0` | replayQueueSize=5 → "Buffered" = 5, color = warning |
| `shows events per minute from stats.lastMinute` | lastMinute=42 → "Events / min" = 42 |

---

### `AlertList.test.jsx` — 9 tests ✅

ทดสอบ filter tabs, RBAC และ acknowledge flow ของ AlertList

| Test | สิ่งที่ตรวจสอบ |
|------|--------------|
| `renders all alerts by default` | render → แสดง alert ทั้งหมด |
| `shows empty state when no alerts` | alerts=[] → "No alerts" |
| `shows unacknowledged count badge in header` | 2 unacked → badge แสดง "2" |
| `filters to unacknowledged when tab clicked` | คลิก Unacknowledged tab → แสดงเฉพาะ unacked |
| `shows empty state message in unacked tab when all acked` | ทุก alert acked → "No active alerts" |
| `ADMIN sees Ack button on unacknowledged alert` | role=ADMIN + unacked → ปุ่ม "Ack" ปรากฏ |
| `OPERATOR does not see Ack button` | role=OPERATOR → ไม่มีปุ่ม "Ack" |
| `clicking Ack calls alertsApi.acknowledge and onAcknowledge callback` | กด Ack → alertsApi.acknowledge(id) ถูกเรียก |
| `CRITICAL alert has danger border styling` | level=CRITICAL → border class มี sentinel-danger |

---

### `client.test.js` — 6 tests ✅

ทดสอบ axios interceptors โดยเรียก handler functions โดยตรง

| Test | สิ่งที่ตรวจสอบ |
|------|--------------|
| `adds Authorization header when token exists` | setAccessToken → request มี `Authorization: Bearer` |
| `does not add Authorization header when no token` | ไม่มี token → ไม่มี Authorization header |
| `dispatches api-version-mismatch event when version differs` | response header api-version=2 → custom event ถูก dispatch |
| `does not dispatch event when version matches` | api-version=1 → ไม่มี event |
| `clears access token on 401 response` | API คืน 401 → token ถูก clear |
| `dispatches api-version-rejected event on 406 response` | API คืน 406 → sentinel:api-version-rejected event |

---

### `useAuth.test.js` — 8 tests ✅

ทดสอบ AuthProvider + useAuth hook lifecycle ด้วย mock authApi

| Test | สิ่งที่ตรวจสอบ |
|------|--------------|
| `starts with loading=true then resolves to loading=false` | mount → loading=true → refresh เสร็จ → loading=false |
| `sets user on successful silent refresh` | refresh สำเร็จ → user ถูก set |
| `stays logged out if refresh fails` | refresh ล้มเหลว → user=null, loading=false |
| `login() sets user and stores access token` | login สำเร็จ → user set + accessToken ใน memory |
| `login() throws on invalid credentials` | API คืน 401 → login() throw |
| `logout() clears user and access token` | logout() → user=null + token cleared |
| `logout() clears state even if API call fails` | API logout throw → state ยังถูก clear |
| `useAuth returns null outside AuthProvider` | เรียก useAuth นอก AuthProvider → คืน null |

---

### `useWebSocket.test.js` — 7 tests ✅

ทดสอบ WebSocket hook ด้วย MockWebSocket class

| Test | สิ่งที่ตรวจสอบ |
|------|--------------|
| `connects on mount with provided URL` | mount → new WebSocket(url) ถูกเรียก |
| `status is CONNECTED after onopen fires` | ws.onopen() → status === 'CONNECTED' |
| `parses JSON message from onmessage` | onmessage({data: '{"t":1}'}) → lastMessage = {t: 1} |
| `stores raw string if JSON parse fails` | onmessage({data: 'not-json'}) → lastMessage = 'not-json' |
| `status is RECONNECTING after onclose fires` | ws.onclose() → status === 'RECONNECTING' |
| `schedules reconnect setTimeout after onclose` | ws.onclose() → setTimeout ถูกเรียก |
| `cleans up WebSocket and timer on unmount` | unmount → ws.close() + clearTimeout ถูกเรียก |

---

### `DeviceTable.test.jsx` — 12 tests ✅

ทดสอบ virtualised device table: filter, sort, selection, keyboard nav และ WebSocket override  
**Mock:** `@tanstack/react-virtual` เพื่อให้ render ทุก row โดยไม่ขึ้นกับ container height

| Test | สิ่งที่ตรวจสอบ |
|------|--------------|
| `renders empty state when no devices` | devices=[] → "No devices registered" |
| `renders visible device names` | devices 3 ตัว → ชื่อแสดงใน DOM |
| `search filter narrows results` | พิมพ์ "alpha" → แสดงเฉพาะ device ที่ชื่อตรง |
| `status filter shows only ONLINE devices` | เลือก Online → ซ่อน OFFLINE devices |
| `lifecycle filter shows only ACTIVE devices` | เลือก ACTIVE → ซ่อน lifecycle อื่น |
| `clear button resets all filters` | filter แล้วกด Clear → แสดง devices ครบ |
| `device count label updates with filter` | filter เหลือ 3 จาก 5 → "3 of 5 devices" |
| `clicking device row calls onSelect with device` | คลิก row → onSelect(device) ถูกเรียก |
| `Enter key on row calls onSelect` | กด Enter บน row → onSelect(device) ถูกเรียก |
| `selected row has aria-selected=true` | selected.id ตรงกับ row → aria-selected="true" |
| `WebSocket message overrides device status to ONLINE` | lastMessage.deviceId ตรงกับ device → แสดง ONLINE แม้ DB บอก OFFLINE |
| `shows no-match state when filters produce no results` | filter ที่ไม่มีผล → "No devices match the filters" |

---

## ปัญหาที่พบและแก้ไข (Frontend Unit Tests)

| # | ปัญหา | ไฟล์ที่แก้ | สาเหตุ |
|---|-------|-----------|--------|
| 1 | MSW ESM ไม่ถูก transform โดย Jest | `jest.config.js` | MSW v2 ใช้ ESM modules, next/jest override transformIgnorePatterns — แก้ด้วย async config export |
| 2 | `{ name: /ack/i }` match "Unacknowledged" tab ด้วย | `AlertList.test.jsx` | regex ครอบคลุมเกินไป — เปลี่ยนเป็น exact string `'Ack'` |
| 3 | Cannot redefine property: location (jsdom 26) | `client.test.js` | jsdom 26 ทำให้ `window.location` เป็น non-configurable — ทดสอบ `getAccessToken() === null` แทน |
