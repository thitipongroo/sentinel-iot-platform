# Demo Guide

> **สำหรับ Development และ Demo**

ไฟล์นี้รวมทุกอย่างที่จำเป็นสำหรับการสาธิตระบบ ได้แก่ Node.js Simulator ที่จำลอง IoT devices และ Demo Data ที่ seed ข้อมูลตัวอย่างลงฐานข้อมูล

---

## Node.js Simulator

Simulator เป็น Node.js process ที่ publish MQTT telemetry ในรูปแบบเดียวกับ firmware ของอุปกรณ์จริง ใช้สำหรับ development และ demo โดยไม่ต้องมีฮาร์ดแวร์จริง

### Sensor Profile

Simulator สร้าง 4-sensor payload ทุก 5 วินาที ต่อ device:

| Sensor        | Normal range | Spike condition        | Spike rate |
|---------------|--------------|------------------------|------------|
| `temperature` | 60–78 °C     | 81–95 °C (CRITICAL)    | 5%         |
| `humidity`    | 35–85 %      | —                      | —          |
| `motion`      | false        | true (detected)        | 20%        |
| `smokePpm`    | 5–50 ppm     | 201–350 ppm (CRITICAL) | 3%         |

### Environment Variables

| Variable      | Default                    | Description                              |
|---------------|----------------------------|------------------------------------------|
| `MQTT_BROKER` | `mqtt://localhost:1883`    | MQTT broker URL                          |
| `MQTT_TOPIC`  | `factory/telemetry`        | Topic to publish to                      |
| `MQTT_USER`   | —                          | Username (ถ้า broker ต้อง auth)          |
| `MQTT_PASS`   | —                          | Password (ถ้า broker ต้อง auth)          |
| `DEVICES`     | `sensor-1,sensor-2,sensor-3` | Comma-separated device IDs             |
| `INTERVAL_MS` | `5000`                     | Publish interval in milliseconds         |

### Run via Docker Compose (แนะนำ)

ตั้งค่า `COMPOSE_PROFILES=dev` ใน `.env` แล้ว simulator จะรันอัตโนมัติเมื่อ start stack:

```bash
# Linux / macOS / Git Bash (กรณีที่ Windows ไม่ได้ติดตั้ง Make)
./run.sh up          # core + simulator
./run.sh up-obs      # core + simulator + Prometheus / Grafana / Jaeger
./run.sh up-full
# หรือ
make up          # core + simulator
make up-obs      # core + simulator + Prometheus / Grafana / Jaeger
make up-full

# Windows PowerShell
docker compose up -d
```

### Run Standalone (manual)

```bash
cd simulator
npm install
MQTT_BROKER=mqtt://localhost:1883 DEVICES=sensor-1,sensor-2 node index.js
```

### Source Code

ไฟล์ทั้งหมดอยู่ใน [`simulator/`](../../simulator/):

| File           | Purpose                                        |
|----------------|------------------------------------------------|
| `index.js`     | Main process — MQTT connect, publish loop      |
| `package.json` | Dependencies (`mqtt` 5.x)                      |
| `Dockerfile`   | Container image (node:20-alpine)               |

---

## Demo Data

Seed ข้อมูลตัวอย่างลงฐานข้อมูล เพื่อให้ Dashboard, Prometheus, Grafana และ Jaeger มีข้อมูลที่สมจริงตั้งแต่เริ่มต้น โดยไม่ต้องรอ live traffic

### What Gets Seeded

500 devices กระจายใน 100 buildings (5 sensor types ต่อ building):

| Profile        | Count | Location                       | Temperature range            |
|----------------|-------|--------------------------------|------------------------------|
| Assembly Line  | 100   | Building N — Assembly Line     | 60–82 °C                     |
| Cold Storage   | 100   | Building N — Cold Storage      | 15–25 °C, high humidity      |
| Engine Room    | 100   | Building N — Engine Room       | 70–92 °C (most alerts)       |
| Server Room    | 100   | Building N — Server Room       | 18–28 °C, stable             |
| Packaging Area | 100   | Building N — Packaging Area    | 22–35 °C, high motion        |

- **~1,000,000 telemetry rows** — 5-minute intervals × 7 days × 490 active devices
- **Hourly aggregates** pre-computed สำหรับ historical charts
- **~120 sample alerts** — engine-room critical temp, smoke spikes, humidity, 10 offline-device alerts
- **sensor-1 / sensor-2 / sensor-3** ตรงกับ MQTT simulator device IDs — live readings รวมเข้า historical timeline อัตโนมัติ
- Devices 491–500 เป็น `OFFLINE` (demo สถานะ device-down)

### Run Seed Data Demo

**Git Bash / Linux / macOS:**

```bash
./scripts/seed-demo.sh
```

**Windows PowerShell:**

```powershell
docker exec -i sentinel-postgres psql -U sentinel -d sentinel < scripts/seed-demo.sql
```

> Safe to re-run — ลบ seed devices เก่าออกก่อนแล้วค่อย insert ใหม่

### Remove Demo Data

**Git Bash / Linux / macOS:**

```bash
./scripts/unseed-demo.sh
```

**Windows PowerShell:**

```powershell
docker exec -i sentinel-postgres psql -U sentinel -d sentinel < scripts/unseed-demo.sql
```

### What to Check After Seeding

| Service     | URL                                                     | What you see                                            |
|-------------|---------------------------------------------------------|---------------------------------------------------------|
| Dashboard   | [localhost:3000](http://localhost:3000)                 | Live + 30-day charts per device, active alerts          |
| Swagger UI  | [localhost:8080/swagger](http://localhost:8080/swagger) | Test API endpoints interactively                        |
| Prometheus  | [localhost:9090](http://localhost:9090)                 | `sentinel_*` custom metrics (scrapes every 15 s)        |
| Grafana     | [localhost:3001](http://localhost:3001)                 | Pre-built dashboards populated with real data           |
| Jaeger      | [localhost:16686](http://localhost:16686)               | Traces generated by every API call                      |

> **Prometheus / Grafana:** historical telemetry data อยู่ใน PostgreSQL — Prometheus เก็บเฉพาะ real-time scrape samples. Grafana dashboards ที่ใช้ Postgres data source จะแสดง 30-day history ทันที ส่วน dashboards ที่ใช้ Prometheus metrics จะ populate ภายในไม่กี่นาทีหลัง live traffic เริ่ม
>
> **Jaeger:** traces บันทึกทุก HTTP request ขณะที่ observability profile รันอยู่ ให้เปิด dashboard หลัง seeding เพื่อสร้าง traces (`./run.sh up-obs` หรือ `make up-obs`)
