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

---

## Running Tests

### Backend Unit Tests

```bash
cd backend
mvn test
```

### Backend Integration Tests

Integration tests ใช้ Testcontainers — Docker ต้องรันอยู่

```bash
cd backend
mvn verify
```

Tests ครอบคลุม:

- REST controller layer (Spring MVC MockMvc)
- Database layer (real PostgreSQL via Testcontainers)
- Redis cache (real Redis via Testcontainers)
- MQTT ingestion pipeline (real Mosquitto via Testcontainers)
- JWT auth + RBAC
- Multi-tenant isolation (Row Level Security)

### Frontend

```bash
cd frontend
npm run lint      # ESLint
npm run build     # Next.js production build (type-check included)
```

### Load Test

ต้องมี k6 ติดตั้งและ stack รันอยู่:

```bash
k6 run load-testing/telemetry.js
```

หรือระบุ URL สำหรับ environment อื่น:

```bash
BASE_URL=http://staging.example.com k6 run load-testing/telemetry.js
```

---

## Load Testing

### Methodology

Script อยู่ที่ [`load-testing/telemetry.js`](../../load-testing/telemetry.js)

- **Endpoint ที่ทดสอบ:** `GET /api/v1/telemetry/{deviceId}/cache` — Redis-backed hot read path
- **Executor:** `ramping-arrival-rate` — ควบคุม RPS โดยตรง ไม่ใช่จำนวน VU
- **Ramp-up:**

  | ช่วงเวลา | Target RPS |
  |----------|------------|
  | 0–30s    | 10 → 100   |
  | 30–90s   | 100 → 500  |
  | 90–150s  | 500 → 1,000|
  | 150–270s | 1,000 (sustained) |
  | 270–300s | 1,000 → 0  |

### Pass Thresholds

| Metric               | SLO Target |
|----------------------|------------|
| `http_req_duration`  | p95 < 200 ms, p99 < 500 ms |
| `success_rate`       | > 95%      |
| `http_req_failed`    | < 5%       |

### Representative Results

ทดสอบบน MacBook Pro M3, 16 GB RAM — Docker Compose (single node):

| Metric           | Observed |
|------------------|----------|
| Total requests   | 180,432  |
| Peak RPS         | ~1,000   |
| p95 latency      | 112 ms   |
| p99 latency      | 187 ms   |
| Success rate     | 99.7%    |

ผ่านทุก SLO threshold

### Observed Bottleneck

ที่ peak 1,000 RPS, bottleneck คือ **HikariCP connection pool** — connection wait time เพิ่มขึ้นก่อน CPU/memory จะถึง limit Redis cache read path ยังไม่ saturated ที่ scale นี้

การปรับ: เพิ่ม `HIKARI_MAXIMUM_POOL_SIZE` (default 10) หรือ scale backend horizontally — Redis pub/sub fan-out รองรับ horizontal scaling อยู่แล้ว
