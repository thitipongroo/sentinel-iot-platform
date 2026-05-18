# Demo Guide

> **สำหรับ Development และ Demo**

ข้อมูลที่จำเป็นสำหรับการ Demo ได้แก่ Node.js Simulator ที่จำลอง IoT devices และ Demo Data ที่ seed ข้อมูลตัวอย่างลง Database

---

## Node.js Simulator

Simulator เป็น Node.js process ที่ publish MQTT telemetry ในรูปแบบเดียวกับ firmware ของอุปกรณ์จริง ใช้สำหรับ development และ demo โดยไม่ต้องมีฮาร์ดแวร์จริง

### Sensor Profile

Simulator สร้าง 4-sensor payload ทุก 5 วินาที ต่อ device :

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
./run.sh up         # core + simulator
./run.sh up-obs     # core + simulator + Prometheus / Grafana / Jaeger
./run.sh up-full    # core + simulator + Prometheus / Grafana / Jaeger + rebuild images ทั้งหมด
# หรือ
make up             # core + simulator
make up-obs         # core + simulator + Prometheus / Grafana / Jaeger
make up-full        # core + simulator + Prometheus / Grafana / Jaeger + rebuild images ทั้งหมด

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

---

## Industry Device Catalog Seed

Seed ข้อมูลอุปกรณ์ IoT จากอุตสาหกรรมจริง 8 ประเภท พร้อม per-sensor capability thresholds ใน JSONB column

### What Gets Seeded

| อุตสาหกรรม | Org Slug | Admin Username | จำนวน Device |
|---|---|---|---|
| Manufacturing / Smart Factory | `manufacturing` | `org-manufacturing-admin` | 8 |
| Cold Chain & Food Safety | `cold-chain` | `org-cold-chain-admin` | 6 |
| Data Center & IT Infrastructure | `datacenter` | `org-datacenter-admin` | 6 |
| Agriculture & Greenhouse | `agriculture` | `org-agriculture-admin` | 6 |
| Healthcare & Pharmaceuticals | `healthcare` | `org-healthcare-admin` | 5 |
| Energy & Utilities | `energy` | `org-energy-admin` | 5 |
| Smart Building & Facilities | `smart-building` | `org-smart-building-admin` | 6 |
| Logistics & Warehouse | `logistics` | `org-logistics-admin` | 5 |

- **47 devices** รวม 26 sensor types (TEMPERATURE, VIBRATION_G, CO2_PPM, BATTERY_PCT, PH, FLOW_LPM, TILT_DEG ฯลฯ)
- **~2,256 telemetry rows** — hourly × 48 ชั่วโมง × 47 devices
- **~32 sample alerts** — ตัวอย่าง alert ทุกอุตสาหกรรม พร้อม realistic message
- **8 organisations + 8 admin users** (password: `sentinel123`)
- แต่ละ device มี `capabilities` JSONB ครบ — alert engine ใช้ per-device threshold แทน global env-var

### Sensor Types ที่ครอบคลุม

```text
TEMPERATURE  HUMIDITY     PRESSURE     SMOKE_PPM    CO2_PPM      CO_PPM
VOC_INDEX    PM25         PM10         O3_PPB       MOTION       VIBRATION_G
TILT_DEG     VOLTAGE_V    CURRENT_A    POWER_W      ENERGY_KWH   BATTERY_V
BATTERY_PCT  SIGNAL_RSSI  LIGHT_LUX    UV_INDEX     SOUND_DB     WATER_LEVEL_PCT
FLOW_LPM     PH
```

### Run Industry Seed

**Git Bash / Linux / macOS:**

```bash
./scripts/seed-industry.sh
```

**Windows PowerShell:**

```powershell
docker exec -i sentinel-postgres psql -U sentinel -d sentinel < scripts/seed-industry.sql
```

> Safe to re-run — ลบ industry devices เก่าออกก่อนแล้วค่อย insert ใหม่

### Remove Industry Seed

```powershell
docker exec -i sentinel-postgres psql -U sentinel -d sentinel < scripts/unseed-industry.sql
```

### Login หลัง Seed

```bash
# ตัวอย่าง — เปลี่ยน org name ตามต้องการ
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"org-manufacturing-admin","password":"sentinel123"}'
```

---

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

---

## Local / Demo Deployment

Stack ทั้งหมดรันผ่าน Docker Compose บนเครื่อง local:

| Service    | Platform       | URL / Port                                         |
|------------|----------------|----------------------------------------------------|
| Frontend   | Docker Compose | [localhost:3000](http://localhost:3000)             |
| Backend    | Docker Compose | [localhost:8080](http://localhost:8080)             |
| PostgreSQL | Docker Compose | localhost:5432                                     |
| Redis      | Docker Compose | localhost:6379                                     |
| Kafka      | Docker Compose | localhost:9092                                     |
| MQTT       | Docker Compose | localhost:1883 (Mosquitto)                         |
| Monitoring | Docker Compose | Prometheus :9090 · Grafana :3001 · Jaeger :16686   |

---

## Development Quick Start

สำหรับ local development ที่ต้องการ simulator รันพร้อมกับ stack ให้ตั้งค่า `COMPOSE_PROFILES=dev` ใน `.env`:

```env
INIT_ADMIN_PASSWORD=<your-admin-password>
INIT_OPERATOR_PASSWORD=<your-operator-password>
COMPOSE_PROFILES=dev
```

| `COMPOSE_PROFILES`      | Services ที่รัน                                      |
|-------------------------|------------------------------------------------------|
| `dev`                   | core + Node.js Simulator                             |
| `dev,observability`     | core + Simulator + Prometheus + Grafana + Jaeger     |
| `prod`                  | core เท่านั้น (ไม่มี simulator)                      |
| `prod,observability`    | core + Prometheus + Grafana + Jaeger                 |

> **core** = postgres, redis, mosquitto, kafka, backend, frontend

```bash
# Linux / macOS / Git Bash
make up          # core + simulator (dev profile)
make up-obs      # core + simulator + monitoring

# Windows PowerShell
docker compose up -d
```
