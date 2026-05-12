# system-design/ — Sentinel IoT Platform

เอกสารออกแบบระบบ ครอบคลุมตั้งแต่ API contract จนถึงการตัดสินใจด้าน architecture

---

| ไฟล์ | คำอธิบาย |
|------|----------|
| [api.md](api.md) | REST API reference — endpoints, request/response schema, authentication headers, error codes |
| [architecture.md](architecture.md) | System architecture overview — component diagram, data flow, technology stack, deployment topology |
| [sequence-diagrams.md](sequence-diagrams.md) | Sequence diagrams — flows หลักของระบบ เช่น device enrollment, telemetry ingestion, alert flow |
| [tradeoffs.md](tradeoffs.md) | Architecture trade-offs — เหตุผลเบื้องหลังการเลือก technology และ design decisions |
| [scaling.md](scaling.md) | Scaling strategy — horizontal/vertical scaling approach สำหรับแต่ละ component |
| [capacity-planning.md](capacity-planning.md) | Capacity planning — การประมาณ resource ที่ต้องการ, scaling thresholds, hardware sizing |
