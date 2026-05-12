# docs/ — Sentinel IoT Platform Documentation

คู่มือและเอกสารทั้งหมดของโปรเจกต์ แบ่งตาม directory ตามประเภทและวัตถุประสงค์

---

## โครงสร้าง Directory

```text
docs/
├── system-design/   # System design & architecture documents
├── test-reports/    # Test execution reports
├── runbooks/        # Operational runbooks & incident response
├── screenshots/     # Architecture diagrams & visual assets
└── test-plans/      # Test plans (unit, integration, e2e, security, performance)
```

---

## system-design/

เอกสารออกแบบระบบ ครอบคลุมตั้งแต่ API contract จนถึงการตัดสินใจด้าน architecture

| ไฟล์ | คำอธิบาย |
|------|----------|
| [api.md](system-design/api.md) | REST API reference — endpoints, request/response schema, authentication headers, error codes |
| [architecture.md](system-design/architecture.md) | System architecture overview — component diagram, data flow, technology stack, deployment topology |
| [capacity-planning.md](system-design/capacity-planning.md) | Capacity planning — การประมาณ resource ที่ต้องการ, scaling thresholds, hardware sizing |
| [scaling.md](system-design/scaling.md) | Scaling strategy — horizontal/vertical scaling approach สำหรับแต่ละ component |
| [sequence-diagrams.md](system-design/sequence-diagrams.md) | Sequence diagrams — flows หลักของระบบ เช่น device enrollment, telemetry ingestion, alert flow |
| [tradeoffs.md](system-design/tradeoffs.md) | Architecture trade-offs — เหตุผลเบื้องหลังการเลือก technology และ design decisions |

---

## test-reports/

ผลการรัน test suite — รวม test counts, pass/fail status และ notes ต่อแต่ละ component

| ไฟล์ | คำอธิบาย |
|------|----------|
| [test-report.md](test-reports/test-report.md) | รายงานสรุป test ทั้งหมด — backend unit/integration/security/E2E, frontend unit, จำนวน test cases รวม 221 tests |

---

## runbooks/

Runbook ปฏิบัติการ ใช้ระหว่าง incident หรือ on-call เพื่อ diagnose และ remediate ปัญหา

| ไฟล์ | คำอธิบาย |
|------|----------|
| [incident-flow.md](runbooks/incident-flow.md) | กระบวนการ incident response ตั้งแต่ detect จนถึง post-mortem |
| [kafka-lag.md](runbooks/kafka-lag.md) | Runbook สำหรับ Kafka consumer lag — วิธี diagnose และ recover |
| [kafka-lag-critical.md](runbooks/kafka-lag-critical.md) | Runbook สำหรับ Kafka lag ระดับ critical — escalation path |
| [latency-p95.md](runbooks/latency-p95.md) | Runbook เมื่อ P95 latency เกิน SLO — ขั้นตอน investigation |
| [latency-p99.md](runbooks/latency-p99.md) | Runbook เมื่อ P99 latency เกิน SLO |
| [jvm-heap.md](runbooks/jvm-heap.md) | Runbook สำหรับ JVM heap pressure — heap dump, GC tuning |
| [slo-fast-burn.md](runbooks/slo-fast-burn.md) | SLO error budget fast burn alert — immediate response |
| [slo-medium-burn.md](runbooks/slo-medium-burn.md) | SLO error budget medium burn alert |
| [slo-slow-burn.md](runbooks/slo-slow-burn.md) | SLO error budget slow burn — trend monitoring |
| [slo-budget-low.md](runbooks/slo-budget-low.md) | Error budget ใกล้หมด — freeze non-critical changes |
| [chaos-testing.md](runbooks/chaos-testing.md) | Chaos engineering runbook — failure injection scenarios |
| [failure-testing.md](runbooks/failure-testing.md) | Failure mode testing — dependency failure simulation |

---

## screenshots/

Diagram และ visual asset ที่อ้างอิงจากเอกสารอื่น ๆ ใน docs/

| ไฟล์ | คำอธิบาย |
|------|----------|
| [sentinel-high-level-diagram.png](screenshots/sentinel-high-level-diagram.png) | High-level system diagram — ภาพรวม component ทั้งหมด |
| [sentinel-architecture-diagram.png](screenshots/sentinel-architecture-diagram.png) | Detailed architecture diagram พร้อม internal data flows |
| [sentinel-tech-stack.png](screenshots/sentinel-tech-stack.png) | Technology stack — layers และ frameworks ที่ใช้ |
| [sentinel-deployment-topology.png](screenshots/sentinel-deployment-topology.png) | Deployment topology — Docker Compose services และ network |
| [sentinel-data-flow-normal.png](screenshots/sentinel-data-flow-normal.png) | Data flow — happy path (telemetry ingestion → storage → broadcast) |
| [sentinel-data-flow-failure.png](screenshots/sentinel-data-flow-failure.png) | Data flow — failure path (DB down → replay queue → recovery) |
| [sentinel-dataflow-normal-path.png](screenshots/sentinel-dataflow-normal-path.png) | Detailed normal path flow diagram |
| [sentinel-dataflow-failure-path.png](screenshots/sentinel-dataflow-failure-path.png) | Detailed failure path flow diagram |

---

## test-plans/

แผนการทดสอบแยกตาม layer และประเภท ยังไม่ได้ implement เว้นแต่ระบุไว้

| ไฟล์ | ขอบเขต | Test Cases | สถานะ |
|------|--------|-----------|-------|
| [backend-unit-test-plan.md](test-plans/backend-unit-test-plan.md) | Service, Repository, Filter unit tests | 28 | ✅ Implemented |
| [backend-integration-test-plan.md](test-plans/backend-integration-test-plan.md) | Spring MVC + Testcontainers integration tests | 75 | ✅ Implemented |
| [backend-concurrency-test-plan.md](test-plans/backend-concurrency-test-plan.md) | Thread safety, TenantContext isolation, rate limiter concurrency | 3 | ✅ Implemented |
| [security-test-plan.md](test-plans/security-test-plan.md) | JWT auth, RBAC, multi-tenant isolation, rate limit, WebSocket, error handling | 45 | ✅ Implemented |
| [e2e-test-plan.md](test-plans/e2e-test-plan.md) | Full user journeys (Cypress) — device lifecycle, alert, WebSocket | 39 | ✅ Implemented |
| [frontend-unit-test-plan.md](test-plans/frontend-unit-test-plan.md) | React component unit tests (Jest + React Testing Library) | 76 | ✅ Implemented |
| [performance-test-plan.md](test-plans/performance-test-plan.md) | Normal load (50 VU), Kafka throughput, Redis cache, WebSocket broadcast | 16 | 📋 วางแผน |
| [load-test-plan.md](test-plans/load-test-plan.md) | Ramp-up (0→500 VU), spike, soak (2 hr), Kafka consumer, multi-tenant | 22 | 📋 วางแผน |
| [regression-test-plan.md](test-plans/regression-test-plan.md) | API contract, HTTP status, auth, RBAC, multi-tenant, migration, rate limit, WebSocket | 55 | 📋 วางแผน |
