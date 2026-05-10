## ช่วยสร้าง flagship project โดยมีขั้นตอนดังนี้

# STEP 1 — Design Architecture Diagram

ต้องมี:

* devices
* MQTT
* backend
* database
* cache
* frontend

---

# STEP 2 — Setup Spring Boot

สร้าง modules:

```text
backend/
```

dependencies:

* Spring Web
* Spring Security
* Spring Data JPA
* PostgreSQL Driver
* Validation
* Actuator

---

# STEP 3 — Database Design

Tables:

## devices

| field      | type      |
| ---------- | --------- |
| id         | UUID      |
| name       | VARCHAR   |
| status     | VARCHAR   |
| created_at | TIMESTAMP |

---

## telemetry

| field       | type      |
| ----------- | --------- |
| id          | UUID      |
| device_id   | UUID      |
| temperature | DOUBLE    |
| humidity    | DOUBLE    |
| timestamp   | TIMESTAMP |

---

## alerts

| field     | type    |
| --------- | ------- |
| id        | UUID    |
| device_id | UUID    |
| level     | VARCHAR |
| message   | TEXT    |

---

# STEP 4 — Build Device APIs

Endpoints:

```text
POST /devices
GET /devices
GET /devices/{id}
```

---

# STEP 5 — Authentication

ใช้:

* JWT

roles:

* ADMIN
* OPERATOR

---

# STEP 6 — Setup MQTT Broker

ใช้ Docker:

```yaml
mosquitto:
  image: eclipse-mosquitto
```

---

# STEP 7 — Create Device Simulator

ใช้:

* Node.js

simulate data:

```json
{
  "deviceId": "sensor-1",
  "temperature": 71.2,
  "humidity": 40
}
```

publish ทุก 5 วินาที

---

# STEP 8 — Build MQTT Consumer

Spring Boot subscribe:

```text
factory/telemetry
```

แล้ว save ลง PostgreSQL

---

# STEP 9 — Add Redis

ใช้ cache:

* device online status
* latest telemetry

---

# STEP 10 — WebSocket Gateway

Frontend realtime updates

---

# STEP 11 — Threshold Rules

เช่น:

```text
temperature > 80
```

create alert

---

# STEP 12 — Notification

Integrate:

* LINE Notify

---

# STEP 13 — Dashboard

ต้องมี:

* device list
* charts
* realtime metrics
* alerts

---

# STEP 14 — Charts

ใช้:

* Recharts

---

# STEP 15 — Docker Compose

ต้องรันทั้งระบบได้:

```bash
docker compose up
```

---

# STEP 16 — GitHub Actions

CI Pipeline:

* build
* test
* lint

---

# STEP 17 — Monitoring

Prometheus metrics:

* request latency
* MQTT throughput
* memory

Grafana dashboard

---

# STEP 18 — Add Tests

ขั้นต่ำ:

| Type        | Tool           |
| ----------- | -------------- |
| Unit        | JUnit          |
| Integration | Testcontainers |
| E2E         | Cypress        |

---

# STEP 19 — Load Testing

ใช้:

* k6

test:

* 1,000 telemetry/sec

---

# STEP 20 — Add Documentation

สิ่งที่ต้องมี:

* architecture
* API docs
* sequence diagrams
* scaling discussion
* tradeoffs

---

# STEP 21 — Deploy

แนะนำ:

| Service    | Platform       |
| ---------- | -------------- |
| frontend   | Vercel         |
| backend    | Render/Railway |
| monitoring | Docker VM      |

---

## สิ่งต้องมีใน README

### 1. Architecture Diagram

สำคัญมาก

---

### 2. Load Testing Result

เช่น:

```text
Handled 10,000 telemetry events/minute
```

---

### 3. CI/CD Badge

---

### 4. Screenshots

---

### 5. Tradeoffs

เช่น:

> Why Redis instead of Memcached

---
