# Backend Concurrency Test Plan — Sentinel IoT Platform

**Stack:** Java 17 · Spring Boot 3 · JUnit 5 · `java.util.concurrent`  
**สถานะ:** ✅ ผ่านทั้งหมด 3/3 tests  
**หลักการ:** ทดสอบ thread safety ของ `TelemetryWebSocketHandler` ที่ต้องรองรับ concurrent connections และ broadcast พร้อมกัน

---

## Component ที่ทดสอบ

### `TelemetryWebSocketHandler`

Handler นี้ทำงานในสภาพแวดล้อม concurrent เพราะ:
- **Session registration/deregistration** — เกิดบน WebSocket worker threads หลาย thread
- **Broadcast** — เกิดบน MQTT consumer thread หรือ Kafka consumer thread
- **Shared state** — `ConcurrentHashMap` ของ sessions ที่ทุก thread แข่งกัน read/write

**ความเสี่ยงถ้า thread safety ล้มเหลว:**
- `ConcurrentModificationException` ขณะ iterate sessions ระหว่าง broadcast
- `NullPointerException` จาก session ที่ถูก remove ขณะกำลัง send
- Race condition ทำให้ broadcast ส่งซ้ำหรือข้าม session

---

## โครงสร้างไฟล์ Test

```
backend/src/test/java/com/sentinel/iot/
└── WebSocketConcurrencyTest.java    (3 tests)
```

---

## Infrastructure

`WebSocketConcurrencyTest extends BaseIntegrationTest` — ใช้ Spring context จริง (`@Autowired TelemetryWebSocketHandler`) เพื่อทดสอบ instance เดียวกับที่ production ใช้ ไม่ใช่ instance ใหม่

---

## รายละเอียด Test แต่ละ Case

---

### 1. `concurrentSessionsAndBroadcast_noExceptions`

**จุดประสงค์:** ยืนยันว่าไม่มี race condition หรือ exception เมื่อ registration และ broadcast เกิดพร้อมกัน

**Setup:**
- สร้าง `ExecutorService` 10 threads
- สร้าง `CountDownLatch` สำหรับ 50 + 20 = 70 operations
- orgId เดียวกันทั้งหมด (เพื่อให้ broadcast ส่งถึงทุก session)

**Concurrent Operations:**
- **50 threads** แต่ละ thread เรียก `handler.afterConnectionEstablished(session)` พร้อมกัน
- **20 threads** แต่ละ thread เรียก `handler.broadcastLocal(orgId + "|{...}")` พร้อมกัน

**Assertion:** `errors.get() == 0` — ไม่มี exception เกิดขึ้นใน thread ใดเลย  
**Timeout:** รอได้ไม่เกิน 10 วินาที

---

### 2. `closedSessions_removedDuringBroadcast`

**จุดประสงค์:** ยืนยันว่า broadcast ข้าม session ที่ปิดแล้วโดยอัตโนมัติ และไม่ส่งข้อความไปที่ session นั้น

**Setup:**
- `open` session: `isOpen()=true`, มี orgId ใน attributes
- `closed` session: `isOpen()=false`, มี orgId ใน attributes

**Flow:**
1. register ทั้ง `open` และ `closed` sessions
2. `broadcastLocal(orgId + "|...")`

**Assertions:**
- `open.sendMessage()` ถูกเรียก 1 ครั้ง
- `closed.sendMessage()` ไม่ถูกเรียกเลย

---

### 3. `disconnectedSession_notBroadcastedAfterClose`

**จุดประสงค์:** ยืนยัน lifecycle ที่ถูกต้อง — session ที่ disconnect แล้วต้องไม่รับ broadcast อีก

**Flow:**
1. register session
2. `broadcastLocal(msg1)` → session รับ (1 ครั้ง)
3. `afterConnectionClosed(session, null)` — deregister session
4. `broadcastLocal(msg2)` → session ต้องไม่รับ

**Assertion:**
- `session.sendMessage()` ถูกเรียก **รวมทั้งหมด 1 ครั้งตลอดการทดสอบ** (จาก msg1 เท่านั้น)

---

## สรุปภาพรวม

| Test | Threads | Operations | สิ่งที่ยืนยัน |
|------|---------|-----------|------------|
| `concurrentSessionsAndBroadcast_noExceptions` | 10 | 50 register + 20 broadcast | ไม่มี exception หรือ race condition |
| `closedSessions_removedDuringBroadcast` | 1 | 1 broadcast | ข้าม closed session อัตโนมัติ |
| `disconnectedSession_notBroadcastedAfterClose` | 1 | 2 broadcasts | ไม่รับ broadcast หลัง disconnect |
| **รวม** | | | **3 tests** |

---

## หลักการออกแบบ

- **AtomicInteger สำหรับนับ error** — thread-safe counter ที่ไม่ต้องการ synchronization เพิ่มเติม
- **CountDownLatch** — รอให้ทุก operation เสร็จก่อน assert เพื่อป้องกัน false positive
- **orgId per test** — แต่ละ test สร้าง UUID ใหม่เพื่อป้องกัน state รั่วระหว่าง tests ใน handler
- **Mock sessions** — ใช้ Mockito mock `WebSocketSession` เพื่อควบคุม `isOpen()` และนับการเรียก `sendMessage()`
- **ใช้ Spring context จริง** (`@Autowired`) — ทดสอบ handler instance เดียวกับ production ไม่ใช่สร้างใหม่
