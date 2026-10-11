# Architecture Decision Records (ADRs)

สารบัญบันทึกการตัดสินใจเชิงสถาปัตยกรรม (Architecture Decision Records) สำหรับ Sentinel IoT Platform  
เอกสารในไดเรกทอรีนี้บันทึกการตัดสินใจเชิงเทคนิค เหตุผล เบื้องหลัง ผลลัพธ์ และข้อแลกเปลี่ยน (Trade-offs) ที่เกิดขึ้นตลอดวงจรการพัฒนา

---

## สารบัญ ADR ทั้งหมด

| เลขที่ | ชื่อบันทึกการตัดสินใจ | สถานะ | วันที่ | หัวข้อหลัก |
|---|---|---|---|---|
| [ADR-0001](0001-database-lifespan-native-partitioning-vs-timescaledb.md) | Telemetry Database Lifespan: PostgreSQL Native Range Partitioning vs. TimescaleDB | **Accepted & Implemented** | 2026-10-11 | Database, Partitioning, RDS/Aurora, CNPG |
| [ADR-0002](0002-stateless-backend-redis-pubsub-and-mqtt-shared-subscriptions.md) | Stateless Multi-Replica Scaling on EKS via Redis Pub/Sub, Distributed Rate Limiting & MQTT Shared Subscriptions | **Accepted & Implemented** | 2026-10-11 | EKS, Multi-Replica, MQTT 5, Redis Pub/Sub, Token Bucket |
| [ADR-0003](0003-distributed-resilience-replay-queue-and-alert-deduplication.md) | Distributed Resilience: Replay Queue Drop Observability and Cache-Fallbacked Alert Deduplication | **Accepted & Implemented** | 2026-10-11 | SRE, Observability, Alert Storm, Fallback Cache |

---

## โครงสร้างและมาตรฐานของ ADR

แต่ละ ADR ยึดตามรูปแบบมาตรฐานสากล (Michael Nygard format) ประกอบด้วย:
1. **Title & Status:** ชื่อเรื่อง สถานะ (Proposed, Accepted, Implemented, Superseded) และวันที่
2. **Context & Problem Statement:** ปัญหาและสถานการณ์ที่นำไปสู่การตัดสินใจ
3. **Decision Drivers:** ปัจจัยชี้ขาดและเป้าหมายเชิงธุรกิจ/เทคนิค
4. **Considered Options & Tradeoff Matrix:** ทางเลือกทั้งหมดพร้อมตารางเปรียบเทียบข้อดี-ข้อเสีย
5. **Decision Outcome:** ทางเลือกที่ได้รับอนุมัติ และรายละเอียดการ Implement
6. **Consequences & Verification:** ผลกระทบเชิงบวก/ลบ การบรรเทาความเสี่ยง และผลการทดสอบ
