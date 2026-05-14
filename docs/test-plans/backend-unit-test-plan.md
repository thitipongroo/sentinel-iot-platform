# Backend Unit Test Plan — Sentinel IoT Platform

**Stack:** Java 17 · Spring Boot 3 · JUnit 5 · Mockito  
**สถานะ:** ✅ ผ่านทั้งหมด 24/24 tests  
**หลักการ:** ทดสอบ business logic แบบ isolation — ไม่ต้องการ Spring Context, Database หรือ Docker container

---

## Framework ที่ใช้

| Library | วัตถุประสงค์ |
|---------|------------|
| `JUnit 5` | test runner และ assertions |
| `Mockito` | mock dependencies (`@Mock`, `@InjectMocks`) |
| `AssertJ` | fluent assertions (`assertThat`) |
| `ReflectionTestUtils` | inject `@Value` fields โดยตรงโดยไม่ต้องโหลด Spring context |
| `spring-test` | `MockHttpServletRequest/Response` สำหรับ filter tests |

---

## โครงสร้างไฟล์ Test

```
backend/src/test/java/com/sentinel/iot/
├── AlertServiceTest.java           (5 tests)
├── DeviceServiceTest.java          (2 tests)
├── MqttConsumerServiceTest.java    (6 tests)
├── TelemetryWebSocketHandlerTest.java (5 tests)
└── RateLimitFilterTest.java        (6 tests)
```

---

## รายละเอียด Test แต่ละไฟล์

---

### 1. `AlertServiceTest` — 5 tests

ทดสอบ alert evaluation logic ของ `AlertService` แบบ isolation

**Dependencies ที่ Mock:**
- `AlertRepository` — ตรวจสอบว่า `save()` ถูกเรียกด้วย alert ที่ถูกต้อง
- `NotificationService` — ตรวจสอบว่า `send()` ถูกเรียกเมื่อควร
- `BusinessMetricsService` — mock ไว้ แต่ไม่ได้ assert

**Threshold ที่ set ใน setUp:**
- temperature: 80.0°C
- humidity: 90.0%
- smokePpm: 200.0 ppm

| Test | Input | ผลที่ตรวจสอบ |
|------|-------|------------|
| `evaluate_shouldCreateCriticalAlertWhenTemperatureExceedsThreshold` | TEMPERATURE = 85.0°C | `alertRepository.save()` ถูกเรียก, alert level = CRITICAL, message มีคำว่า "temperature", `notificationService.send()` ถูกเรียก |
| `evaluate_shouldCreateCriticalAlertWhenSmokeExceedsThreshold` | SMOKE_PPM = 250.0 ppm | alert level = CRITICAL, message มีคำว่า "smoke" |
| `evaluate_shouldCreateWarningWhenHumidityExceedsThreshold` | HUMIDITY = 95.0% | alert level = WARNING, message มีคำว่า "humidity" |
| `evaluate_shouldCreateNoAlertWhenBelowAllThresholds` | TEMP=70°C, HUM=60%, SMOKE=10ppm | `alertRepository.save()` ไม่ถูกเรียก, `notificationService.send()` ไม่ถูกเรียก |
| `evaluate_shouldCreateWarningWhenMotionDetectedAtElevatedTemperature` | TEMP=75°C, MOTION=1.0 | alert level = WARNING, message มีคำว่า "motion" |

---

### 2. `DeviceServiceTest` — 2 tests

ทดสอบ device creation logic ของ `DeviceService`

**Dependencies ที่ Mock:**
- `DeviceRepository` — mock `existsByName()` และ `save()`
- `RedisService` — ตรวจสอบว่า `setDeviceStatus()` ถูกเรียกหลัง create

**setUp:** สร้าง `DeviceRequest` ที่มี name="sensor-1", location="Factory A"

| Test | Input | ผลที่ตรวจสอบ |
|------|-------|------------|
| `create_shouldSaveAndReturnDevice` | name ไม่ซ้ำ | device ถูก save, status = "OFFLINE", `redisService.setDeviceStatus()` ถูกเรียกด้วย "OFFLINE" |
| `create_shouldThrowWhenNameExists` | name ซ้ำ (existsByName = true) | throw `IllegalArgumentException` ที่มี message "already exists" |

---

### 3. `MqttConsumerServiceTest` — 6 tests

ทดสอบ MQTT message processing pipeline ของ `MqttConsumerService`

**Dependencies ที่ Mock:**
- `KafkaTelemetryProducer` — ตรวจสอบว่า `publish()` ถูกเรียกด้วย deviceId และ payload ถูกต้อง
- `MessageChannel mqttDlqChannel` — ตรวจสอบว่า DLQ message มี header `dlq-error-code` ถูกต้อง

**setUp:** สร้าง service ด้วย `SimpleMeterRegistry` และ smokeThreshold=200

| Test | Input Payload | ผลที่ตรวจสอบ |
|------|-------------|------------|
| `handleMessage_withValidPayload_forwardsToKafka` | JSON ถูกต้องครบ field | `kafkaProducer.publish("sensor-1", payload)` ถูกเรียก, DLQ ไม่ถูกเรียก |
| `handleMessage_withMalformedJson_routesToDlq` | `"not-valid-json{{{"` | DLQ ถูกเรียกด้วย `dlq-error-code: PARSE_ERROR`, Kafka ไม่ถูกเรียก |
| `handleMessage_withMissingTemperature_routesToDlq` | ขาด field `temperature` | DLQ ถูกเรียกด้วย `dlq-error-code: VALIDATION_ERROR` |
| `handleMessage_withTemperatureOutOfRange_routesToDlq` | temperature = 999.0 | DLQ ถูกเรียกด้วย `dlq-error-code: VALIDATION_ERROR` |
| `handleMessage_withNegativeHumidity_routesToDlq` | humidity = -5.0 | DLQ ถูกเรียกด้วย `dlq-error-code: VALIDATION_ERROR` |
| `handleMessage_withMissingDeviceId_routesToDlq` | ขาด field `deviceId` | DLQ ถูกเรียกด้วย `dlq-error-code: VALIDATION_ERROR` |

---

### 4. `TelemetryWebSocketHandlerTest` — 5 tests

ทดสอบ session management และ broadcast logic ของ `TelemetryWebSocketHandler`

**ไม่มี Mock framework** — ใช้ Mockito สร้าง `WebSocketSession` ที่ mock พฤติกรรม isOpen/getAttributes/sendMessage

**Message format:** `<orgId>|<json-payload>` — handler กรอง session ด้วย orgId

**Helpers:**
- `openSession(id)` — mock session ที่ `isOpen()=true` และ `getAttributes()` return `Map{"orgId": TEST_ORG}`
- `closedSession(id)` — mock session ที่ `isOpen()=false`

| Test | Scenario | ผลที่ตรวจสอบ |
|------|----------|------------|
| `afterConnectionEstablished_tracksSession` | register session แล้ว broadcast | `session.sendMessage()` ถูกเรียก 1 ครั้ง |
| `afterConnectionClosed_removesSession` | register → close → broadcast | `session.sendMessage()` ไม่ถูกเรียกหลัง close |
| `broadcast_sendsToAllOpenSessions` | register 3 sessions → broadcast 1 ครั้ง | ทั้ง 3 sessions รับ message |
| `broadcast_skipsClosedSessions` | 1 open + 1 closed → broadcast | เฉพาะ open session รับ message |
| `broadcast_continuesWhenOneSendFails` | 1 good + 1 bad (throws) → broadcast | ไม่ throw exception, good session ยังรับ message |

---

### 5. `RateLimitFilterTest` — 6 tests

ทดสอบ rate limiting logic ของ `RateLimitFilter` โดยตรงโดยไม่ผ่าน Spring Security

**setUp:** inject `trustedProxiesConfig = "10.0.0.1"` ผ่าน ReflectionTestUtils

**Helpers:**
- `invoke(path, remoteAddr)` — สร้าง MockHttpServletRequest ด้วย remote address โดยตรง
- `invokeForwarded(path, forwardedIp)` — สร้าง request ที่มี `X-Forwarded-For` จาก trusted proxy IP 10.0.0.1

| Test | Scenario | ผลที่ตรวจสอบ |
|------|----------|------------|
| `first100ApiRequests_areAllowed` | ส่ง 100 request ไปที่ `/api/devices` จาก IP เดียวกัน | ทุก request ไม่ได้ status 429 |
| `request101_isRateLimited` | ส่ง 101 request จาก IP เดียวกัน | request ที่ 101 ได้ status 429 |
| `rateLimitedResponse_containsErrorJson` | เกิน limit → ดู response body | status 429, body มีข้อความ "Rate limit exceeded" |
| `nonApiPath_isNeverRateLimited` | ส่ง 200 request ไปที่ `/actuator/health` | ไม่มี request ได้ status 429 |
| `differentClientIPs_haveIndependentBuckets` | exhaust bucket ของ IP A → ส่งจาก IP B | IP B ยังผ่านได้ (ไม่ได้ status 429) |
| `xForwardedForHeader_isUsedAsClientIp` | ส่งผ่าน trusted proxy พร้อม `X-Forwarded-For` | rate limit นับจาก forwarded IP, IP ต่างกันมี bucket แยก |

---

## สรุปภาพรวม

| Test Class | Tests | Component ที่ทดสอบ | Mock Dependencies |
|-----------|-------|-------------------|-----------------|
| `AlertServiceTest` | 5 | `AlertService.evaluate()` | AlertRepository, NotificationService, BusinessMetricsService |
| `DeviceServiceTest` | 2 | `DeviceService.create()` | DeviceRepository, RedisService |
| `MqttConsumerServiceTest` | 6 | `MqttConsumerService.handleMessage()` | KafkaTelemetryProducer, MessageChannel |
| `TelemetryWebSocketHandlerTest` | 5 | `TelemetryWebSocketHandler` | WebSocketSession (Mockito mock) |
| `RateLimitFilterTest` | 6 | `RateLimitFilter.doFilter()` | MockHttpServletRequest/Response |
| **รวม** | **24** | | |

---

## หลักการออกแบบ

- **Isolation สมบูรณ์** — ไม่มี test ใดที่ต้องการ database, Redis หรือ network จริง
- **Fast feedback** — รันใน milliseconds ไม่ต้องรอ container start
- **Single responsibility** — แต่ละ test ตรวจสอบ behavior เดียว ไม่ผสมกัน
- **ReflectionTestUtils** — ใช้ inject `@Value` fields เพื่อหลีกเลี่ยงการโหลด Spring context

---

## วิธีรัน

```bash
cd backend
mvn test
```
