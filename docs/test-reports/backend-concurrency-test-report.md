# Backend Concurrency Test Report

**วันที่รัน:** 2026-05-12  
**สถานะ:** ✅ ผ่านทั้งหมด  
**ผลรวม:** 3 tests | 1 file | 0 failures  
**Framework:** JUnit 5 + Spring Boot Test + Testcontainers

---

## สรุปผล

| Test Class | Tests | ผล |
|-----------|-------|-----|
| WebSocketConcurrencyTest | 3 | ✅ |
| **รวม** | **3** | **✅** |

---

## WebSocketConcurrencyTest — 3 tests ✅

ทดสอบ **thread safety** ของ WebSocket handler ที่ต้องรองรับ concurrent connections และ broadcast

| Test | คำอธิบาย | สิ่งที่ตรวจสอบ |
|------|----------|---------------|
| `concurrentSessionsAndBroadcast_noExceptions` | Register 50 sessions + broadcast 20 ข้อความพร้อมกันใน 10 threads | ต้องไม่มี exception หรือ race condition |
| `closedSessions_removedDuringBroadcast` | มี session ที่ปิดแล้วอยู่ใน pool | broadcast ส่งเฉพาะ open session, ข้าม closed โดยอัตโนมัติ |
| `disconnectedSession_notBroadcastedAfterClose` | Session disconnect ระหว่าง broadcast | หลัง close → ไม่รับ broadcast อีก |
