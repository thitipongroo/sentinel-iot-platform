# Backend Unit Test Report

**วันที่รัน:** 2026-05-12  
**สถานะ:** ✅ ผ่านทั้งหมด  
**ผลรวม:** 28 tests | 6 files | 0 failures  
**Framework:** JUnit 5 + Mockito (ไม่ใช้ Spring Context / Docker)

---

## สรุปผล

| Test Class | Tests | ผล |
|-----------|-------|-----|
| AlertServiceTest | 5 | ✅ |
| DeviceServiceTest | 2 | ✅ |
| MqttConsumerServiceTest | 6 | ✅ |
| TelemetryWebSocketHandlerTest | 5 | ✅ |
| RateLimitFilterTest | 6 | ✅ |
| **รวม** | **28** | **✅** |

---

## AlertServiceTest — 5 tests ✅

| Test | คำอธิบาย |
|------|----------|
| `alertWithTemperatureAboveThreshold_shouldTriggerAlert` | ค่า temperature เกิน threshold → สร้าง alert |
| `alertWithTemperatureBelowThreshold_shouldNotTrigger` | ค่า temperature ปกติ → ไม่สร้าง alert |
| `alertWithSmokeAboveThreshold_shouldTriggerAlert` | ค่า smoke ppm เกิน threshold → สร้าง alert |
| `alertWithMotionDetected_shouldTriggerAlert` | ตรวจพบ motion → สร้าง alert |
| `alertWithAllSensorsNormal_shouldNotTriggerAnyAlert` | ค่าทุกตัวปกติ → ไม่สร้าง alert ใด |

---

## DeviceServiceTest — 2 tests ✅

| Test | คำอธิบาย |
|------|----------|
| `createDevice_withValidRequest_shouldSaveAndReturn` | สร้าง device ด้วยข้อมูลถูกต้อง → save และคืน entity |
| `createDevice_withDuplicateName_shouldThrowException` | ชื่อ device ซ้ำ → throw `IllegalArgumentException` |

---

## MqttConsumerServiceTest — 6 tests ✅

| Test | คำอธิบาย |
|------|----------|
| `validPayload_isProcessedAndSaved` | MQTT payload ถูกต้อง → parse และ persist ลง DB |
| `invalidJson_isRoutedToDlq` | JSON ผิด format → route ไป Dead Letter Queue |
| `payloadMissingRequiredFields_isRoutedToDlq` | ขาด required field → route ไป DLQ |
| `payloadWithEdgeMetadata_isPersistedCorrectly` | payload มี edge metadata → บันทึกครบถ้วน |
| `payloadWithV2ReadingsMap_isPersistedCorrectly` | payload v2 ที่มี readings map → บันทึกถูกต้อง |
| `dlqMessage_isRequeued` | DLQ message → requeue ลง replay queue |

---

## TelemetryWebSocketHandlerTest — 5 tests ✅

| Test | คำอธิบาย |
|------|----------|
| `afterConnectionEstablished_sessionIsRegistered` | เปิด connection → session ถูก register |
| `afterConnectionClosed_sessionIsRemoved` | ปิด connection → session ถูกลบออก |
| `broadcastLocal_sendsToOpenSessions` | broadcast → ส่งเฉพาะ session ที่ยัง open |
| `broadcastLocal_skipsClosedSessions` | session ปิดอยู่ → ข้ามไม่ส่ง |
| `broadcastLocal_filtersMessageByOrgId` | ส่งเฉพาะ session ที่ orgId ตรงกับ message prefix |

---

## RateLimitFilterTest — 6 tests ✅

| Test | คำอธิบาย |
|------|----------|
| `requestUnderLimit_shouldBeAllowed` | request ยังไม่เกิน limit → ผ่าน |
| `requestOverLimit_shouldBeRejected` | request เกิน rate limit → 429 Too Many Requests |
| `differentIps_haveIndependentBuckets` | IP ต่างกัน → นับ bucket แยกกัน |
| `xForwardedFor_usedAsClientIp` | มี `X-Forwarded-For` header → ใช้ IP นั้นเป็น client IP |
| `actuatorEndpoints_areExemptFromRateLimit` | `/actuator/**` → ยกเว้นจาก rate limit |
| `requestsAfterWindowReset_shouldBeAllowed` | หลัง time window reset → อนุญาตใหม่ |
