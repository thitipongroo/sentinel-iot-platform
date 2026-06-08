# [NotebookLM Context] Sentinel IoT Platform — Investor Pitch

## INSTRUCTIONS FOR NOTEBOOKLM

สร้าง slide presentation สำหรับ **investor pitch 5 นาที** (10 slides, แต่ละ slide ใช้เวลา ~30 วินาที)

โครงสร้าง slides ที่ต้องการ:

1. **Problem** — ปัญหาที่ตลาดเผชิญ
2. **Solution** — Sentinel แก้ปัญหาอย่างไร
3. **Product** — Screenshots + features หลัก
4. **Market & ICP** — กลุ่มลูกค้าและขนาดตลาด
5. **Business Model** — 7 revenue streams
6. **Pricing & Unit Economics** — ราคาและ COGS
7. **Competitive Advantage** — เปรียบเทียบคู่แข่ง
8. **Technical Credibility** — ผลการทดสอบและ performance
9. **Gross Margin & Financials** — ตัวเลขการเงิน
10. **Vision & The Ask** — เป้าหมายและสิ่งที่ต้องการจากนักลงทุน

แต่ละ slide ให้มี: **Headline** (1 ประโยค), **3 bullet points**, **1 supporting data point**

---

## SECTION 1: PROBLEM

อุตสาหกรรมอาหาร, เภสัชกรรม, อาคาร, และโรงงานมีปัญหาร่วมกัน:

- **ไม่มี real-time visibility** — อุณหภูมิเกิน threshold รู้ทีหลังเสมอ
- **Manual monitoring** — คนเดินตรวจสอบ ไม่มีระบบ alert อัตโนมัติ
- **ไม่มี audit trail** — FDA FSMA, EU Food Safety Law บังคับเก็บ log แต่ไม่มีระบบ
- **ค่าเสียหาย:** สินค้า cold chain เสียหาย 1 ครั้ง = $100,000–$1,000,000+
- **คู่แข่งทางเลือก (AWS IoT Core, Azure IoT Hub)** ต้องมีทีม engineer สร้าง dashboard เอง ไม่มี ready-to-use solution

---

## SECTION 2: SOLUTION — Sentinel IoT Platform

Sentinel IoT Platform คือ **production-grade industrial IoT monitoring system** ที่พร้อมใช้งานทันที

**Core Value Proposition:**

- Monitor sensors แบบ real-time ทุก 5 วินาที
- Alert อัตโนมัติผ่าน Slack, Webhook, LINE Notify เมื่อเกิน threshold
- Multi-tenant: แต่ละองค์กร isolated ระดับ database (PostgreSQL Row Level Security)
- รองรับตั้งแต่ 50 ถึง 100,000+ devices บน architecture เดียวกัน

**Sensors ที่รองรับ:**

- Temperature: -40 ถึง 200°C (alert > 80°C)
- Humidity: 0–100% (alert > 90%)
- Smoke PPM (alert > 200 ppm)
- CO2 PPM
- Motion
- Battery %, RSSI, uptime (v2 edge metadata)

---

## SECTION 3: PRODUCT

**Screenshots ที่มี:**

- `docs/screenshots/dashboard.png` — Real-time dashboard พร้อม TelemetryChart (Live/1h/6h/24h/7d)
- `docs/screenshots/alerts.png` — Alert management panel (All / Unacknowledged tabs)
- `docs/screenshots/grafana.png` — Observability dashboard (Prometheus + Grafana)

**Features หลักที่มีในระบบจริง:**

- Real-time WebSocket dashboard (Live / 1h / 6h / 24h / 7d)
- Multi-tenant architecture ระดับ PostgreSQL Row Level Security (org isolation)
- Alert system: Slack, Webhook (HMAC-SHA256), LINE Notify
- Device lifecycle management (PROVISIONED → ACTIVE → INACTIVE → DECOMMISSIONED)
- Firmware version tracking และ device enrollment tokens (single-use, expiring)
- Hourly aggregates เก็บถาวร, raw data retention 30 วัน default
- Observability: Prometheus + Grafana + Jaeger (OpenTelemetry)
- DeviceTable รองรับ 10,000+ devices ด้วย virtual row rendering

---

## SECTION 4: MARKET & ICP

**ICP (Ideal Customer Profile) — วิเคราะห์จาก sensor types + market research 2026:**

| ลำดับ | กลุ่มลูกค้า | Sensor หลัก | เหตุผลที่ยอมจ่ายแพง |
|---|---|---|---|
| #1 | Food & Pharma Cold Chain | Temperature, Humidity | ของเสียหาย 1 ครั้ง = $100K–$1M+, FDA FSMA compliance |
| #2 | Food Manufacturing | Temperature, Humidity, Smoke | กฎหมายบังคับเก็บ log |
| #3 | Building Management | CO2, Smoke, Temperature | ประหยัดพลังงาน $10K–$50K/ปี/อาคาร |
| #4 | Industrial Manufacturing | Temperature, Motion, Smoke | Predictive maintenance |

**Scalability ของ Platform:**

- Starter: 1–50 devices
- Small: 51–200 devices
- Medium: 201–2,000 devices
- Large: 2,001–10,000 devices
- XLarge: 10,001–100,000 devices
- Web-scale: 100,000+ devices

---

## SECTION 5: BUSINESS MODEL — 7 Revenue Streams

| # | ช่องทาง | รูปแบบ |
|---|---|---|
| 1 | SaaS Subscription | Recurring monthly, tiered by device count |
| 2 | Data Retention Upsell | Add-on monthly (compliance-driven) |
| 3 | Notification Channel Add-ons | Add-on monthly |
| 4 | API Access | Add-on monthly (SCADA/ERP integrators) |
| 5 | Enterprise Multi-Tenancy | Add-on monthly |
| 6 | Device Enrollment as a Service | Per-device + monthly management |
| 7 | OEM / White-label License | Annual contract |

**Revenue Architecture:**

```text
Base Plan ($522–$5,217/month)
    ├── + Data Retention      (+$99–$599/month)
    ├── + Alert Pro/Enterprise (+$79–$199/month)
    ├── + Pro/Enterprise API   (+$199–$599/month)
    ├── + Enterprise Pack      (+$499/month)
    └── + Enrollment Service   ($1/device + $149/month)

OEM License ($60K–$120K/year)
```

**ตัวอย่าง Revenue ต่อลูกค้า 1 ราย (Medium + all add-ons):**

```text
Base Plan Medium:   $1,522/month
+ Data Retention:   +$299/month
+ Alert Pro:        +$79/month
+ Pro API:          +$199/month
+ Enterprise Pack:  +$499/month
+ Enrollment:       +$149/month
─────────────────────────────
รวม:                $2,747/month (~$32,964/year)
```

---

## SECTION 6: PRICING & UNIT ECONOMICS

**COGS คำนวณจาก AWS cost estimates (capacity-planning.md):**

| Tier | Devices | AWS COGS/month | COGS/device/month |
|---|---|---|---|
| Small | 200 | $120 | $0.60 |
| Medium | 2,000 | $350 | $0.175 |
| Large | 10,000 | $1,200 | $0.12 |

**Selling Price (Target Gross Margin 77%):**

สูตร: `ราคาขาย = COGS ÷ (1 - 0.77)`

| Tier | COGS/month | ราคาขาย/month | Gross Margin |
|---|---|---|---|
| Small (200 devices) | $120 | **$522** | 77% |
| Medium (2,000 devices) | $350 | **$1,522** | 77% |
| Large (10,000 devices) | $1,200 | **$5,217** | 77% |

**Add-on Gross Margin:**

- Data Retention 90 วัน (+$99/month): **99.8%** (COGS เพิ่มแค่ $0.19 S3 storage)
- Notification Pro (+$79/month): **~100%** (Slack API ฟรี)
- Device Enrollment ($1/device): **~100%** (1 DB write)

---

## SECTION 7: COMPETITIVE ADVANTAGE

| คู่แข่ง | ราคาจริง (2026) | จุดอ่อน | Sentinel เหนือกว่า |
|---|---|---|---|
| AWS IoT Core | $0.08/1M connection-min + $1/1M messages | ไม่มี dashboard ต้องสร้างเอง | Ready-to-use |
| Azure IoT Hub | $10–$2,500/unit/month | Infrastructure-only ไม่มี UI | Full-stack platform |
| Losant | $250/month (100K payload) | Payload billing คาดเดายาก | Device-based billing ชัดเจน |
| Particle | $2.99/device/month | ผูกกับ hardware ของ Particle | Hardware-agnostic |
| Manual/SCADA | $0 แต่ต้องคนดูแล | ไม่มี cloud analytics | Real-time + automated alerts |

**Positioning:** Premium — multi-tenant + compliance-grade + ready-to-deploy

---

## SECTION 8: TECHNICAL CREDIBILITY

**Test Results (ทดสอบเมื่อ 2026-05-12):**

| หมวดทดสอบ | จำนวน Tests | ผล |
|---|---|---|
| Unit Tests | 28 tests / 6 files | ✅ 0 failures |
| Integration Tests | 34 tests / 6 files | ✅ 0 failures |
| Security Tests | 45 tests / 8 files | ✅ 0 failures |
| **รวม** | **107 tests** | **✅ 100% pass rate** |

**Security Tests ครอบคลุม:**

- JWT forgery, algorithm confusion, expiry, revocation
- Multi-tenant data isolation (cross-org IDOR prevention)
- SQL injection, XSS prevention
- Rate limiting, brute-force protection
- WebSocket authentication

**Performance (load test บน MacBook Pro M3, 16GB RAM):**

| Metric | ผลจริง | SLO Target |
|---|---|---|
| API throughput | 1,003 req/s | — |
| p95 API latency | 112 ms | < 200 ms ✅ |
| p99 API latency | 187 ms | < 500 ms ✅ |
| MQTT ingestion | ~400 events/s | — |
| PostgreSQL INSERT | ~1,000 rows/s | — |
| Redis read latency | < 1 ms | — |

**Architecture Highlights:**

- Event-driven: MQTT → Kafka → Spring Boot → PostgreSQL
- Circuit Breaker + Retry + Replay Queue (Zero data loss during DB outage)
- Distributed tracing: OpenTelemetry → Jaeger
- Blue/Green deployment (Argo Rollouts) สำหรับ zero-downtime
- Kubernetes-native autoscaling (KEDA + HPA)

---

## SECTION 9: GROSS MARGIN & FINANCIALS

**Gross Margin Benchmark (2025–2026):**

| บริษัท | ประเภท | Gross Margin | แหล่งข้อมูล |
|---|---|---|---|
| **Samsara (IOT)** | IoT SaaS | **77% GAAP** | SEC Form 8-K FY2026 |
| Datadog | Infrastructure SaaS | 80% GAAP | SEC FY2025 |
| Snowflake | Infrastructure SaaS | 67% GAAP | SEC FY2026 |
| Median SaaS ทั้งตลาด | — | 74.62% | SaaSDB May 2026 |

**Sentinel Target: 77% Gross Margin** (เทียบเท่า Samsara)

**เกณฑ์นักลงทุน:**

- < 70% → นักลงทุนตั้งคำถาม
- 75%+ → "Clean SaaS story"
- 80%+ → Premium multiple (7x+ revenue)

**Scaling Economics (COGS ลดลงเมื่อ scale):**

- 200 devices: COGS = $0.60/device/month
- 2,000 devices: COGS = $0.175/device/month (ลด 71%)
- 10,000 devices: COGS = $0.12/device/month (ลด 80%)

---

## SECTION 10: TECHNOLOGY STACK

**Backend:** Spring Boot 3.2, Java 21, Spring Integration (MQTT), Apache Kafka, Resilience4j

**Frontend:** Next.js 14 (App Router), React Query, Zustand, TanStack Virtual

**Infrastructure:** PostgreSQL 16 (partitioned by month), Redis 7, Eclipse Mosquitto MQTT, Kafka with Avro + Schema Registry

**Deployment:** Docker Compose (dev) → Kubernetes + Helm (prod), Argo Rollouts (Blue/Green), KEDA autoscaling, CloudNativePG

**Observability:** Prometheus + Grafana, Jaeger (OpenTelemetry), Structured logging (Logstash JSON)

**Security:** JWT + Refresh Token rotation, Redis JTI blocklist, PostgreSQL Row Level Security, mTLS (opt-in), HMAC-SHA256 webhook signing
