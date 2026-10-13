# Request for Comments (RFCs) & Engineering Roadmap

สารบัญเอกสารข้อเสนอการพัฒนา (Request for Comments - RFC) และแผนงานสถาปัตยกรรม (Architecture Roadmap) สำหรับ Sentinel IoT Platform  
เอกสารในไดเรกทอรีนี้ใช้สำหรับกำหนดทิศทาง วางแผน และบันทึกข้อเสนอการพัฒนาระบบที่มีผลกระทบในวงกว้าง

---

## สารบัญ RFC ทั้งหมด

| เลขที่ | ชื่อเอกสาร RFC | สถานะ | เป้าหมาย Release | รายละเอียดโดยย่อ |
|---|---|---|---|---|
| [RFC-001](RFC-001-production-readiness-and-scaling-roadmap.md) | Production Readiness, Database Lifespan Extension & Scaling Roadmap | **Implemented / Completed (100%)** | v1.2.0 - v2.0.0 (Q4 2026 - Q3 2027) | แผนสถาปัตยกรรมรองรับ Production, Database Lifespan, EKS 3+ Replicas Stateless Scaling, และ Roadmap 4 เฟส (เสร็จสมบูรณ์ครบทั้ง 4 Phase) |

---

## สรุปภาพรวมแผนพัฒนา (Phased Roadmap Summary)

- **Phase 1: Production Hardening & EKS Blue/Green Deployments (Q4 2026)** — *(เสร็จสมบูรณ์ 100%)*
  - Stateless Backend 3+ Replicas บน EKS
  - Database Range Partitioning 2027-2028 & Auto-Provisioning Service
  - Argo Rollouts Blue/Green พร้อม Automated Prometheus Analysis
  - KEDA Autoscaler อ้างอิง Kafka Consumer Lag และ Replay Queue Depth
  - CloudNativePG (CNPG) Multi-AZ Automated Failover Drill
- **Phase 2: Edge Ingestion & Industrial Protocols (Q1 2027)** — *(เสร็จสมบูรณ์ 100%)*
  - Edge Mosquitto Broker Gateway Clustering พร้อม Store-and-Forward
  - Modbus TCP และ OPC-UA Protocol Translation Adapters
  - Device Shadow / Digital Twin ใน PostgreSQL JSONB & Redis แคช <1ms
- **Phase 3: Cold Storage & Lakehouse Historical Analytics (Q2 2027)** — *(เสร็จสมบูรณ์ 100%)*
  - Automated Parquet Archival บน Cold Storage แบบ Hive-partitioned (`org_id/year/month`) พร้อมรับประกัน Zero Data Loss
  - Embedded DuckDB Lakehouse Analytics Engine สำหรับวิเคราะห์ข้อมูลย้อนหลังหลายปีแบบ Sub-second
  - Tiered Rollup Pipeline (Daily, Monthly Aggregates) พร้อม Row-Level Security
- **Phase 4: Realtime AI & Anomaly Detection (Q3 2027)** — *(เสร็จสมบูรณ์ 100%)*
  - Streaming ML Inference Pipeline ใน Kafka Batch Consumer (`StreamingAnomalyInferenceService`)
  - Multivariate Sensor Anomaly Detection (Isolation Forest Ensemble + Physics-Informed Thermal Runaway Model พร้อมความแม่นยำ >95% และ FPR <0.01%)
  - Adaptive Dynamic Alarm Threshold Engine (`AdaptiveThresholdService`) บน Redis คำนวณขอบเขตสัญญาณแปรผันตามพฤติกรรมจริง (μ ± kσ) พร้อมกลไก Outlier Dampening
  - Explainable AI (XAI) คำนวณสัดส่วน Feature Contributions และ REST Endpoints สำหรับ Dry-Run Evaluation & Baseline Management
