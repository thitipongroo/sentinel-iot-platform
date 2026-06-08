# Sentinel IoT Platform — Marketing Plan

## 1. Product Overview

### Sentinel IoT Platform **คือ** production-grade industrial IoT monitoring system

**รองรับ sensor หลัก :**

- Temperature
- Humidity
- Motion
- Smoke PPM
- CO2 PPM
- sensor แบบ dynamic ผ่าน v2 payload

> 400 Devices Across 20 Industries

**Main Features :**

- Real-time WebSocket dashboard (Live / 1h / 6h / 24h / 7d)
- Multi-tenant architecture ระดับ PostgreSQL Row Level Security (org isolation)
- Alert system: Slack, Webhook (HMAC-SHA256), LINE Notify
- Device lifecycle management (PROVISIONED → ACTIVE → INACTIVE → DECOMMISSIONED)
- Firmware version tracking และ device enrollment tokens (single-use, expiring)
- Hourly aggregates เก็บถาวร, raw data retention 30 วัน default
- Observability: Prometheus + Grafana + Jaeger (OpenTelemetry)
- Capacity รองรับตั้งแต่ 50 ถึง 100,000+ devices

![Dashboard](../screenshots/dashboard.png)

![Alerts](../screenshots/alerts.png)

![Grafana](../screenshots/grafana.png)

---

## 2. ICP — Ideal Customer Profile

| ลำดับ | กลุ่มลูกค้า | Sensor ที่ใช้หลัก | เหตุผลที่ยอมจ่าย |
|---|---|---|---|
| #1 | Food & Pharma Cold Chain | Temperature, Humidity | Regulatory compliance (FDA FSMA, EU) — ของเสียหาย 1 ครั้ง = $100K–$1M+ |
| #2 | Food Manufacturing | Temperature, Humidity, Smoke | กฎหมายบังคับเก็บ log ข้อมูลหลายปี |
| #3 | Building Management | CO2, Smoke, Temperature | HVAC optimization ประหยัดพลังงาน $10K–$50K/ปี/อาคาร |
| #4 | Industrial Manufacturing | Temperature, Motion, Smoke | Predictive maintenance, fire safety compliance |

---

## 3. Competitive Analysis

| คู่แข่ง | ราคาจริง | จุดอ่อน |
|---|---|---|
| AWS IoT Core | $0.08/1M connection-min + $1/1M messages | ไม่มี dashboard พร้อมใช้ ต้องมีทีม engineer สร้างเอง |
| Azure IoT Hub | $10–$2,500/unit/month | Infrastructure-only ไม่มี UI, ไม่มี alert rules |
| Losant | $250/month (100K payload), $1,000/month (500K payload) | Payload-based billing คาดเดาค่าใช้จ่ายยาก |
| Particle | $299/block ของ 100 devices (~$2.99/device/month) | ผูกกับ hardware ecosystem ของ Particle เท่านั้น |
| Manual / SCADA | $0 แต่ต้องคนดูแล 24/7 | ไม่มี real-time alert, ไม่มี cloud analytics |

**Positioning:** Premium — สูงกว่า Losant, ต่ำกว่า Particle เล็กน้อย แต่มี multi-tenant + compliance-grade features ที่คู่แข่งไม่มี

---

## 4. COGS per Device

คำนวณจาก AWS cost estimates:

| Tier | Devices | AWS COGS/month | COGS/device/month |
|---|---|---|---|
| Small | 200 | $120 | $0.60 |
| Medium | 2,000 | $350 | $0.175 |
| Large | 10,000 | $1,200 | $0.12 |

---

## 5. Gross Margin Target

**Target: 77%** — อ้างอิงจาก Samsara (ticker: IOT) IoT SaaS platform ที่ใกล้เคียงที่สุด รายงาน 77% GAAP ใน FY2026 (SEC Form 8-K)

| Benchmark | Gross Margin | แหล่งข้อมูล |
|---|---|---|
| Samsara (IoT SaaS) | 77% GAAP | SEC Form 8-K FY2026 |
| Median SaaS ทั้งตลาด | 74.62% | SaaSDB May 2026 |
| Datadog (Infrastructure SaaS) | 80% GAAP | SEC FY2025 |
| Snowflake (Infrastructure SaaS) | 67% GAAP | SEC FY2026 |

เกณฑ์นักลงทุน: ต่ำกว่า 70% → ตั้งคำถาม / 75%+ → clean SaaS story / 80%+ → premium multiple

---

## 6. Pricing Strategy

### Base Plan (SaaS Subscription)

สูตร: `ราคาขาย = COGS ÷ (1 - 0.77)`

| Tier | Devices | COGS/month | ราคาขาย/month |
|---|---|---|---|
| Small | 200 | $120 | **$522** |
| Medium | 2,000 | $350 | **$1,522** |
| Large | 10,000 | $1,200 | **$5,217** |

### Add-on Pricing

| Add-on | ราคา/month | Gross Margin | หมายเหตุ |
|---|---|---|---|
| Data Retention 90 วัน | +$99 | ~99.8% | Extra S3 storage ~$0.19/month (200 devices) |
| Data Retention 1 ปี | +$299 | ~99% | ตอบโจทย์ FDA/FSMA compliance |
| Data Retention 3 ปี | +$599 | ~99% | Pharmaceutical GxP validation |
| Alert Pro (unlimited + all channels) | +$79 | ~100% | Slack API ฟรี, Webhook ใช้ infra เดิม |
| Alert Enterprise (escalation rules) | +$199 | ~99% | ต้องพัฒนาเพิ่ม |
| Pro API (1,000 req/min + SLA) | +$199 | ~95% | สำหรับ SCADA/ERP integrators |
| Enterprise API (unlimited + dedicated) | +$599 | ~95% | Large enterprise |
| Enterprise Pack (RLS + audit + support) | +$499 | ~95% | Multi-facility enterprise |
| Device Enrollment (one-time) | $1.00/device | ~100% | Single-use token, IP tracking |
| Enrollment Management | +$149/month | ~100% | Bulk provisioning dashboard |

### OEM / White-label License

| Tier | ราคา/ปี | Devices |
|---|---|---|
| OEM Starter | $60,000 | ถึง 2,000 |
| OEM Professional | $120,000 | ถึง 10,000 |
| OEM Enterprise | Custom (contact sales) | 10,000+ |

---

## 7. Revenue Architecture

```text
Base Plan (device tier)
    ├── + Data Retention      ($99–$599/month)
    ├── + Alert Pro/Enterprise ($79–$199/month)
    ├── + Pro/Enterprise API   ($199–$599/month)
    ├── + Enterprise Pack      ($499/month)
    └── + Enrollment Service   ($1/device + $149/month)

OEM License (แยกสัญญา $60K–$120K/year)
```
