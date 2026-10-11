# Request for Comments (RFCs) & Engineering Roadmap

สารบัญเอกสารข้อเสนอการพัฒนา (Request for Comments - RFC) และแผนงานสถาปัตยกรรม (Architecture Roadmap) สำหรับ Sentinel IoT Platform  
เอกสารในไดเรกทอรีนี้ใช้สำหรับกำหนดทิศทาง วางแผน และบันทึกข้อเสนอการพัฒนาระบบที่มีผลกระทบในวงกว้าง

---

## สารบัญ RFC ทั้งหมด

| เลขที่ | ชื่อเอกสาร RFC | สถานะ | เป้าหมาย Release | รายละเอียดโดยย่อ |
|---|---|---|---|---|
| [RFC-001](RFC-001-production-readiness-and-scaling-roadmap.md) | Production Readiness, Database Lifespan Extension & Scaling Roadmap | **Accepted / In Implementation** | v1.2.0 - v2.0.0 (Q4 2026 - Q3 2027) | แผนสถาปัตยกรรมรองรับ Production, Database Lifespan, EKS 3+ Replicas Stateless Scaling, และ Roadmap 4 เฟส |

---

## สรุปภาพรวมแผนพัฒนา (Phased Roadmap Summary)

- **Phase 1: Production Hardening & EKS Blue/Green Deployments (Q4 2026)**
  - Stateless Backend 3+ Replicas บน EKS (เสร็จสมบูรณ์)
  - Database Range Partitioning 2027-2028 & Auto-Provisioning Service (เสร็จสมบูรณ์)
  - Argo Rollouts Blue/Green พร้อม Automated Prometheus Analysis
  - KEDA Autoscaler อ้างอิง Kafka Consumer Lag และ Replay Queue Depth
  - CloudNativePG (CNPG) Multi-AZ Automated Failover Drill
- **Phase 2: Edge Ingestion & Industrial Protocols (Q1 2027)**
  - Edge Mosquitto Broker Gateway Clustering พร้อม Store-and-Forward
  - mTLS X.509 Device Certificate Authentication & Automatic Rotation
  - Modbus TCP และ OPC-UA Protocol Translation Adapters
  - Device Shadow / Digital Twin ใน Redis
- **Phase 3: Cold Storage & Lakehouse Historical Analytics (Q2 2027)**
  - Automated Parquet Archival บน S3 / GCS สำหรับข้อมูลพ้นระยะ 30 วัน
  - DuckDB / Apache Iceberg Query Engine สำหรับวิเคราะห์ข้อมูลย้อนหลังหลายปี
  - Tiered Rollup Pipeline (Daily, Weekly, Monthly Buckets)
- **Phase 4: Realtime AI & Anomaly Detection (Q3 2027)**
  - Streaming Inference ผ่าน ONNX Runtime / Python Worker บน Kafka
  - Multivariate Sensor Anomaly Detection (Isolation Forest & Autoencoder)
  - Adaptive Dynamic Alarm Threshold Engine
