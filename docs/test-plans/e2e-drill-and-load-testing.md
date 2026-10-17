# E2E Drill & High-Throughput Load Testing Plan

เอกสารแผนการทดสอบระบบเสมือนจริงแบบครบวงจร (End-to-End Integration & Load Testing) ครอบคลุมการทดสอบโหลด 10,000 req/sec, การตรวจจับสภาวะวิกฤต Thermal Runaway, และการขยายตัวอัตโนมัติของ Kubernetes Pod ผ่าน KEDA

---

## 1. ขอบเขตการทดสอบ (Test Scope)

| รายการทดสอบ | เครื่องมือ / สคริปต์ | วัตถุประสงค์ | เกณฑ์การผ่าน (Acceptance Criteria) |
|---|---|---|---|
| **High-Throughput Ingestion & Cache (10k RPS)** | [`tests/load/k6-high-throughput.js`](file:///Users/thitipongroo/workspace/github/sentinel-iot-platform/tests/load/k6-high-throughput.js) | วัดความหน่วงและเสถียรภาพของ Hot Cache Read, ML Inference Dry-Run, และ Shadow Update | • Peak $\ge 10,000\text{ req/sec}$<br/>• Telemetry Cache $p95 < 10\text{ ms}$<br/>• ML Anomaly Inference $p95 < 20\text{ ms}$<br/>• Error Rate $< 0.1\%$ |
| **DuckDB Lakehouse OLAP Concurrency** | [`tests/load/k6-lakehouse-olap.js`](file:///Users/thitipongroo/workspace/github/sentinel-iot-platform/tests/load/k6-lakehouse-olap.js) | จำลอง 50 Concurrent Data Analysts รันคำสั่งวิเคราะห์ข้อมูลย้อนหลัง 2 ปี | • DuckDB Query $p95 < 500\text{ ms}$<br/>• Sub-Second Query Rate $> 98\%$ |
| **Thermal Runaway Live Crisis Drill** | [`infra/scripts/thermal-runaway-injection.js`](file:///Users/thitipongroo/workspace/github/sentinel-iot-platform/infra/scripts/thermal-runaway-injection.js)<br/>[`infra/scripts/thermal-runaway-drill.sh`](file:///Users/thitipongroo/workspace/github/sentinel-iot-platform/infra/scripts/thermal-runaway-drill.sh) | ยิงข้อมูลจำลองอุณหภูมิวิกฤต $\Delta T \uparrow$, $\Delta H \downarrow$, $S \uparrow$ เข้าสู่ MQTT Broker | • AI ตรวจจับถูกต้อง (Precision $> 95\%$)<br/>• สร้าง Alert ระดับ `CRITICAL`<br/>• ไม่เกิด Alert Storm (Deduplication ทำงาน)<br/>• Outlier Dampening ป้องกันค่าเฉลี่ยสถิติปนเปื้อน |
| **KEDA Consumer Lag Autoscaling Drill** | [`infra/scripts/keda-autoscaling-drill.sh`](file:///Users/thitipongroo/workspace/github/sentinel-iot-platform/infra/scripts/keda-autoscaling-drill.sh) | จำลอง Ingest Burst สร้าง Kafka Consumer Lag $> 1,000$ | • ScaledObject ตรวจพบ Lag<br/>• Backend Pod Scale Out จาก 3 ไป 10 Replicas<br/>• Scale Down กลับสู่ 3 Replicas อย่างปลอดภัย |

---

## 2. ขั้นตอนการรันการทดสอบ (Execution Procedures)

### 2.1 รันการทดสอบโหลดความเร็วสูง (High-Throughput 10k RPS Load Test)
```bash
# รัน k6 High-Throughput Load Test
k6 run tests/load/k6-high-throughput.js -e BASE_URL=http://localhost:8080
```

### 2.2 รันการทดสอบ DuckDB Lakehouse OLAP Query
```bash
# รัน k6 Lakehouse Concurrency Test
k6 run tests/load/k6-lakehouse-olap.js -e BASE_URL=http://localhost:8080
```

### 2.3 รัน Thermal Runaway Crisis Live Injection Drill
```bash
# เรียกใช้งาน Shell Script ทดสอบกระบวนการทั้งหมดแบบอัตโนมัติ
./infra/scripts/thermal-runaway-drill.sh

# หรือสั่งจำลองข้อมูลเฉพาะจุดผ่าน Node Script
TARGET_DEVICE="sensor-1" MQTT_BROKER="mqtt://localhost:1883" node infra/scripts/thermal-runaway-injection.js
```

### 2.4 รัน KEDA Autoscaling Drill
```bash
# ตรวจสอบและทดสอบการขยายขนาดของ Pod ตามระดับ Kafka Lag
./infra/scripts/keda-autoscaling-drill.sh
```

---

## 3. เกณฑ์การประเมินและการตรวจสอบผล (Validation Checkpoints)

1. **Precision & False Positive Rate (RFC-001 Pillar):**
   * อัตราความแม่นยำในการตรวจจับ Thermal Runaway ต้อง $\ge 95\%$
   * อัตราการแจ้งเตือนผิดพลาด (False Positive Rate) บนข้อมูลสภาวะการทำงานปกติของโรงงานต้อง $\le 0.01\%$
2. **Distributed Resilience:**
   * การส่งสัญญาณซ้ำซ้อนอย่างรวดเร็ว (Rapid Repeat Burst) จะต้องถูกระงับด้วย Redis Cooldown Key (`alert:dedup:...`)
   * ปริมาณ Replay Queue Dropped ต้องเท่ากับ $0\text{ msgs/min}$
3. **Storage Cost Efficiency:**
   * สัดส่วนพื้นที่จัดเก็บของ Apache Parquet Cold Storage ต้องประหยัดได้มากกว่า $70\%$ เมื่อเทียบกับ Raw Table Storage
