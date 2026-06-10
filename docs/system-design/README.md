# system-design/ — Sentinel IoT Platform

เอกสารออกแบบระบบ ครอบคลุมตั้งแต่ API contract จนถึงการตัดสินใจด้าน architecture

---

| ไฟล์ | คำอธิบาย |
|------|----------|
| [api.md](api.md) | REST API reference — endpoints, request/response schema, authentication headers, error codes |
| [architecture.md](architecture.md) | System architecture overview — component diagram, data flow, technology stack, deployment topology |
| [security.md](security.md) | Security features — JWT, RBAC, multi-tenant isolation, rate limiting, audit logging, known limitations |
| [telemetry-retention.md](telemetry-retention.md) | Telemetry retention policy — 30-day raw retention, hourly aggregates, partition lifecycle |
| [cicd.md](cicd.md) | CI/CD pipeline — GitHub Actions stages, security scan, Testcontainers, contract testing |
| [mqtt-tls.md](mqtt-tls.md) | MQTT TLS / mTLS setup — certificate generation, env vars, connection testing |
| [notification.md](notification.md) | Notification providers — LINE Messaging API, Telegram, Apprise, Slack, generic webhook + deduplication |
| [sequence-diagrams.md](sequence-diagrams.md) | Sequence diagrams — flows หลักของระบบ เช่น device enrollment, telemetry ingestion, alert flow |
| [tradeoffs.md](tradeoffs.md) | Architecture trade-offs — เหตุผลเบื้องหลังการเลือก technology และ design decisions |
| [scaling.md](scaling.md) | Scaling strategy — horizontal/vertical scaling approach สำหรับแต่ละ component |
| [capacity-planning.md](capacity-planning.md) | Capacity planning — การประมาณ resource ที่ต้องการ, scaling thresholds, hardware sizing |
| [device-catalog.md](device-catalog.md) | Industry device catalog — 8 industries, 47 devices พร้อม per-sensor capability thresholds |
