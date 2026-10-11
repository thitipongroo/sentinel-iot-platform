# Sentinel IoT Platform — Marketing Plan

## 1. Product Overview

Sentinel IoT Platform คือ production-grade industrial IoT monitoring system รองรับ sensor หลัก: Temperature, Humidity, Motion, Smoke PPM, CO2 PPM และ sensor แบบ dynamic ผ่าน v2 payload มี real-time WebSocket dashboard, multi-tenant architecture ระดับ PostgreSQL Row Level Security, device lifecycle management, firmware tracking, และ alert system (LINE Messaging API, Telegram, Apprise, Slack, generic webhook)

---

## 2. ICP — Ideal Customer Profile

| ลำดับ | กลุ่มลูกค้า | Sensor ที่ใช้หลัก | เหตุผลที่ยอมจ่าย |
|---|---|---|---|
| 1 | Food & Pharma Cold Chain | Temperature, Humidity | Regulatory compliance (FDA FSMA, EU) — ของเสียหาย 1 ครั้ง = 3.5 ล้าน – 35 ล้านบาท+ |
| 2 | Food Manufacturing | Temperature, Humidity, Smoke | กฎหมายบังคับเก็บ log ข้อมูลหลายปี |
| 3 | Building Management | CO2, Smoke, Temperature | HVAC optimization ประหยัดพลังงาน 350,000 – 1,750,000 บาท/ปี/อาคาร |
| 4 | Industrial Manufacturing | Temperature, Motion, Smoke | Predictive maintenance, fire safety compliance |

---

## 3. Competitive Analysis
*(อ้างอิงอัตราแลกเปลี่ยน 1 USD = 35 บาท)*

| คู่แข่ง | ราคาจริง | จุดอ่อน |
|---|---|---|
| AWS IoT Core | 2.80 บาท/1M connection-min + 35 บาท/1M messages | ไม่มี dashboard พร้อมใช้ ต้องมีทีม engineer สร้างเอง |
| Azure IoT Hub | 350 – 87,500 บาท/unit/เดือน | Infrastructure-only ไม่มี UI, ไม่มี alert rules |
| Losant | 8,750 บาท/เดือน (100K payload), 35,000 บาท/เดือน (500K payload) | Payload-based billing คาดเดาค่าใช้จ่ายยาก |
| Particle | 10,465 บาท/block ของ 100 devices (~105 บาท/device/เดือน) | ผูกกับ hardware ecosystem ของ Particle เท่านั้น |
| Manual / SCADA | 0 บาท แต่ต้องคนดูแล 24/7 | ไม่มี real-time alert, ไม่มี cloud analytics |

**Positioning:** Premium — สูงกว่า Losant, ต่ำกว่า Particle เล็กน้อย แต่มี multi-tenant + compliance-grade features ที่คู่แข่งไม่มี

---

## 4. COGS per Device

คำนวณจากต้นทุนประมาณการ AWS (1 USD = 35 บาท):

| Tier | Devices | AWS COGS/เดือน | COGS/device/เดือน |
|---|---|---|---|
| Small | 200 | 4,200 บาท | 21 บาท |
| Medium | 2,000 | 12,250 บาท | 6.13 บาท |
| Large | 10,000 | 42,000 บาท | 4.20 บาท |

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

`ราคาขาย = COGS ÷ (1 - 0.77)`

| Tier | Devices | COGS/เดือน | ราคาขาย/เดือน |
|---|---|---|---|
| Small | 200 | 4,200 บาท | **18,270 บาท** |
| Medium | 2,000 | 12,250 บาท | **53,270 บาท** |
| Large | 10,000 | 42,000 บาท | **182,595 บาท** |

### Add-on Pricing

| Add-on | ราคา/เดือน | Gross Margin | หมายเหตุ |
|---|---|---|---|
| Data Retention 90 วัน | +3,465 บาท | ~99.8% | Extra S3 storage ~6.65 บาท/เดือน (200 devices) |
| Data Retention 1 ปี | +10,465 บาท | ~99% | ตอบโจทย์ FDA/FSMA compliance |
| Data Retention 3 ปี | +20,965 บาท | ~99% | Pharmaceutical GxP validation |
| Alert Pro (unlimited + all channels) | +2,765 บาท | ~100% | Slack API ฟรี, Webhook ใช้ infra เดิม |
| Alert Enterprise (escalation rules) | +6,965 บาท | ~99% | ต้องพัฒนาเพิ่ม |
| Pro API (1,000 req/min + SLA) | +6,965 บาท | ~95% | สำหรับ SCADA/ERP integrators |
| Enterprise API (unlimited + dedicated) | +20,965 บาท | ~95% | Large enterprise |
| Enterprise Pack (RLS + audit + support) | +17,465 บาท | ~95% | Multi-facility enterprise |
| Device Enrollment (one-time) | 35 บาท/device | ~100% | Single-use token, IP tracking |
| Enrollment Management | +5,215 บาท/เดือน | ~100% | Bulk provisioning dashboard |

### OEM / White-label License

| Tier | ราคา/ปี | Devices |
|---|---|---|
| OEM Starter | 2,100,000 บาท | ถึง 2,000 |
| OEM Professional | 4,200,000 บาท | ถึง 10,000 |
| OEM Enterprise | Custom (contact sales) | 10,000+ |

---

## 7. Revenue Architecture

```text
Base Plan (device tier)
    ├── + Data Retention       (3,465 – 20,965 บาท/เดือน)
    ├── + Alert Pro/Enterprise (2,765 – 6,965 บาท/เดือน)
    ├── + Pro/Enterprise API   (6,965 – 20,965 บาท/เดือน)
    ├── + Enterprise Pack      (17,465 บาท/เดือน)
    └── + Enrollment Service   (35 บาท/device + 5,215 บาท/เดือน)

OEM License (แยกสัญญา 2.1 ล้าน – 4.2 ล้านบาท/ปี)
```
