# ช่วยตรวจสอบสิ่งที่ project นี้ควรมี และห้ามมี ดังต่อไปนี้

ระบบควรมี:

### 1. Device Simulator

จำลอง sensor devices

เช่น:

* temperature
* humidity
* motion
* smoke

---

### 2. MQTT Communication

ใช้ protocol จริง

เช่น:

* Mosquitto MQTT broker

---

### 3. Backend API

ทำด้วย:

* Spring Boot

มี:

* authentication
* device management
* telemetry ingestion
* alert APIs

---

### 4. Realtime Dashboard

Frontend แสดง:

* realtime sensor data
* online/offline status
* charts
* alerts

---

### 5. Alert Engine

เช่น:

* temperature > 80°C
* แจ้งเตือน LINE/Telegram/email

---

### 6. Database Design

ใช้:

* PostgreSQL

---

### 7. Cache Layer

ใช้:

* Redis

---

### 8. Containerization

ใช้:

* Docker Compose

---

### 9. CI/CD

ใช้:

* GitHub Actions

---

### 10. Monitoring

ใช้:

* Prometheus
* Grafana

---

# Architecture

```text
[ IoT Devices ]
       |
    MQTT
       |
[ MQTT Broker ]
       |
[ Ingestion Service ]
       |
 ---------------------
 |        |          |
Redis   PostgreSQL  Alert Engine
 |
Realtime Gateway
 |
Frontend Dashboard
```

---

## Backend

* Java 21
* Spring Boot 3
* Spring Security
* Spring Data JPA

---

## Messaging

* MQTT (Mosquitto)

---

## Database

* PostgreSQL

---

## Cache

* Redis

---

## Frontend

* Next.js

---

## Infra

* Docker
* Docker Compose

---

## Monitoring

* Prometheus
* Grafana

---

## Testing

* JUnit
* Testcontainers
* Cypress

---

ระบบห้ามมี:

* CRUD dashboard
* monolith มั่วๆ

หลังจากตวจสอบเสร็จแล้วช่วยสรุปข้อมูลมาให้ด้วย

---
