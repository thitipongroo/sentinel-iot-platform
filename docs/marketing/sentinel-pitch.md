# [NotebookLM Context] Sentinel IoT Platform — Investor Pitch

## Pre-Seed Round | Solo Founder | Pure Pre-Revenue

---

## SECTION 1 : PROBLEM

อุตสาหกรรมอาหาร, เภสัชกรรม, อาคาร, และโรงงานที่มีปัญหาร่วมกัน :

> "โรงพยาบาลในสิงคโปร์เสียวัคซีน COVID-19 มูลค่า $2.3 ล้าน ในปี 2021 เพราะตู้เย็นเสียตอนตี 3 ไม่มีระบบ alert อัตโนมัติ — จนถึงตอนเช้าก็สายเกินแก้ไข"

**ปัญหาหลัก :**

- **ไม่มี real-time visibility** — อุณหภูมิเกิน threshold รู้ทีหลังเสมอ
- **Manual monitoring** — คนเดินตรวจสอบ ไม่มีระบบ alert อัตโนมัติ
- **ไม่มี audit trail** — FDA FSMA, EU Food Safety Law บังคับเก็บ log แต่ไม่มีระบบ
- **ค่าเสียหาย:** สินค้า cold chain เสียหาย 1 ครั้ง = $100,000 – $1,000,000+
- **ทางเลือกที่มี (AWS IoT Core, Azure IoT Hub)** ต้องมีทีม engineer สร้าง dashboard เอง ไม่มี ready-to-use solution

---

## SECTION 2 : SOLUTION — Sentinel IoT Platform

Sentinel IoT Platform คือ **production-grade industrial IoT monitoring system** ที่พร้อมใช้งานทันที

**Core Value Proposition :**

- Monitor sensors แบบ real-time ทุก 5 วินาที
- Alert อัตโนมัติผ่าน Slack, Webhook, LINE Notify เมื่อเกิน threshold
- Multi-tenant: แต่ละองค์กร isolated ระดับ database (PostgreSQL Row Level Security)
- รองรับตั้งแต่ 50 ถึง 100,000+ devices บน architecture เดียวกัน

**Sensors ที่รองรับ :**

- Temperature: -40 ถึง 200°C (alert > 80°C)
- Humidity: 0–100% (alert > 90%)
- Smoke PPM (alert > 200 ppm)
- CO2 PPM, Motion, Battery %, RSSI, uptime

---

## SECTION 3 : PRODUCT

**Screenshots :**

- `dashboard.png` — Real-time dashboard พร้อม TelemetryChart (Live / 1h / 6h / 24h / 7d)
- `alerts.png` — Alert management panel (All / Unacknowledged tabs)
- `grafana.png` — Observability dashboard (Prometheus + Grafana)

**Features หลักที่มีในระบบ :**

- Real-time WebSocket dashboard (Live / 1h / 6h / 24h / 7d)
- Multi-tenant architecture ระดับ PostgreSQL Row Level Security (org isolation)
- Alert system: Slack, Webhook (HMAC-SHA256), LINE Notify
- Device lifecycle management (PROVISIONED → ACTIVE → INACTIVE → DECOMMISSIONED)
- Firmware version tracking และ device enrollment tokens (single-use, expiring)
- Hourly aggregates เก็บถาวร, raw data retention 30 วัน default
- Observability: Prometheus + Grafana + Jaeger (OpenTelemetry)
- DeviceTable รองรับ 10,000+ devices ด้วย virtual row rendering

---

## SECTION 4 : MARKET & ICP

### ✅ Market Sizing

| Level | ขนาด | นิยาม | Source |
| --- | --- | --- | --- |
| **TAM** | **$53.8B** (2026) | Global IoT Monitoring & Management Market | MarketsandMarkets 2026 |
| **SAM** | **$8.2B** | Industrial IoT Monitoring: Food, Pharma, Building, Manufacturing ใน Asia-Pacific + SEA | IDC IoT Spending Guide 2026 |
| **SOM** | **$41M** | Thailand + SEA : ลูกค้า Food/Pharma/Building ที่พร้อม adopt SaaS IoT (1,000+ potential accounts) | Bottom-up estimate |

**SOM คำนวณจาก :** 500 SME accounts × $522/month × 12 = ~$3.1M Year 3 target (6% of SOM)

### ICP (Ideal Customer Profile)

| ลำดับ | กลุ่มลูกค้า | Sensor หลัก | เหตุผลที่ยอมจ่ายแพง |
| --- | --- | --- | --- |
| **#1 Priority** | Food & Pharma Cold Chain | Temperature, Humidity | ของเสียหาย 1 ครั้ง = $100K – $1M+, FDA FSMA compliance |
| #2 | Food Manufacturing | Temperature, Humidity, Smoke | กฎหมายบังคับเก็บ log |
| #3 | Building Management | CO2, Smoke, Temperature | ประหยัดพลังงาน $10K – $50K/ปี/อาคาร |
| #4 | Industrial Manufacturing | Temperature, Motion, Smoke | Predictive maintenance |

### ✅ Traction & Proof of Demand

**Current Status — Honest Pre-Seed Positioning:**

แม้ยังไม่มีรายได้ แต่มีหลักฐาน demand ดังนี้ :

- **Platform ทำงานจริง 100%** — 107 automated tests, 0 failures (tested 2026-05-12)
- **Technical validation** — load test ผ่าน 1,003 req/s บน local environment
- **ICP research** — สัมภาษณ์ potential customers ในกลุ่ม Food & Pharma : Ongoing customer discovery
- **Next 90 days** — หา 3 Design Partners ที่ยอม pilot โดยไม่คิดค่าใช้จ่าย เพื่อแลกกับ testimonial + case study

> **Investor Framing :** "เราไม่ได้ขาย vision — เราขาย working product ที่พร้อม pilot วันนี้"

---

## SECTION 5 : GO-TO-MARKET STRATEGY

### GTM Motion : Founder-Led Direct Sales

**เหตุผลที่เลือก Direct Sales ก่อน :**

- Cold chain / food safety เป็น relationship-driven industry — ต้องการ trust ก่อนซื้อ
- Deal size $522 – $5,217/month คุ้มค่ากับ founder ลงทุนเวลาเอง
- Feedback loop เร็วที่สุด: founder คุยกับลูกค้าโดยตรง → iterate product ทันที

### Funnel (18 เดือนแรก)

```text
Phase 1 (เดือน 1–6) : DESIGN PARTNERS
├── เป้าหมาย : 3 unpaid pilots ใน Food Cold Chain
├── Channel : Personal network + LinkedIn outreach
├── Output : 3 case studies + product feedback
└── Budget : $0 (founder time only)

Phase 2 (เดือน 7–12) : FIRST REVENUE
├── เป้าหมาย : Convert 2/3 pilots → paid ($522/month each)
├── + 5 new customers จาก referral + case study
├── MRR target : ~$3,600/month
└── CAC target : < $500 (2x payback in 6 months)

Phase 3 (เดือน 13–18) : REPEATABLE SALES
├── เป้าหมาย : 20 customers, $12,000 MRR
├── Channel : Referral + 1 System Integrator partnership
├── Hire : 1 Sales/BD person (funded by Series Seed)
└── LTV : CAC target : > 5x
```

### Unfair Distribution Advantages

- **Regulatory urgency** — FDA FSMA 2026 enforcement กดดัน Food Manufacturers ให้ต้องมีระบบ
- **LINE Notify integration** — ตอบโจทย์ Southeast Asia market โดยเฉพาะ (Thai/Vietnamese users)
- **ราคาต่ำกว่า manual alternative** — $522/month vs. เงินเดือนพนักงาน 1 คน ($800 – $1,500/month)

---

## SECTION 6: BUSINESS MODEL — Revenue Progression

>**FOCUS : 1 stream ก่อน — ขยายเมื่อลูกค้าจ่ายแล้วเท่านั้น**

### ปีที่ 1 — FOCUS NOW : Base SaaS Subscription เท่านั้น

เป้าหมาย : หา 10 paying customers พิสูจน์ว่ามีคนยอมจ่าย

| Tier | Devices | Price/month | COGS | Gross Margin |
| --- | --- | --- | --- | --- |
| Small | 200 | $522 | $120 | 77% |
| Medium | 2,000 | $1,522 | $350 | 77% |
| Large | 10,000 | $5,217 | $1,200 | 77% |

### ปีที่ 2 — ROADMAP : Upsell ลูกค้าที่พิสูจน์แล้ว

เปิดใช้งานได้หลังจากมี base customers แล้วเท่านั้น

| Add-on | Price/month | Gross Margin | Trigger |
| --- | --- | --- | --- |
| Data Retention 90 วัน | $99–$599 | 99.8% | Compliance audit |
| Alert Pro | $79–$199 | ~100% | Alert fatigue |
| Pro API Access | $199–$599 | ~100% | SCADA / ERP integration |

### ปีที่ 3 — ROADMAP : Enterprise & OEM

เปิดใช้งานหลัง product-market fit ชัดเจนแล้วเท่านั้น

| สินค้า | ราคา | รูปแบบ | Gross Margin |
| --- | --- | --- | --- |
| Enterprise Pack | $499/month | Add-on รายเดือน | ~100% |
| White-label License | $60K–$120K/year | สัญญารายปี | ~85% |

**ตัวอย่าง Full Stack Revenue (Medium + ปีที่ 2 add-ons ครบ) :**

```text
Base Plan Medium:   $1,522/month
+ Data Retention:   +$299/month
+ Alert Pro:        +$79/month
+ Pro API Access:   +$199/month
─────────────────────────────
Total:              $2,099/month (~$25,188/year)
```

---

## SECTION 7 : COMPETITIVE ADVANTAGE

| คู่แข่ง | ราคาจริง (2026) | จุดอ่อน | Sentinel เหนือกว่า |
| --- | --- | --- | --- |
| AWS IoT Core | $0.08/1M connection-min + $1/1M messages | ไม่มี dashboard ต้องสร้างเอง | Ready-to-use, no engineering |
| Azure IoT Hub | $10–$2,500/unit/month | Infrastructure-only ไม่มี UI | Full-stack platform |
| Losant | $250/month (100K payload) | Payload billing คาดเดายาก | Device-based billing ชัดเจน |
| Particle | $2.99/device/month | ผูกกับ hardware ของ Particle | Hardware-agnostic |
| Manual/SCADA | $0 แต่ต้องคนดูแล | ไม่มี cloud analytics, ไม่มี audit trail | Real-time + automated alerts + compliance |

**Positioning :** Premium — multi-tenant + compliance-grade + ready-to-deploy + SEA-native (LINE Notify)

---

## SECTION 8 : TECHNICAL CREDIBILITY

**Test Results (ทดสอบเมื่อ 2026-05-12) :**

| หมวดทดสอบ | จำนวน Tests | ผล |
| --- | --- | --- |
| Unit Tests | 28 tests / 6 files | ✅ 0 failures |
| Integration Tests | 34 tests / 6 files | ✅ 0 failures |
| Security Tests | 45 tests / 8 files | ✅ 0 failures |
| **รวม** | **107 tests** | **✅ 100% pass rate** |

**Performance (load test) :**

| Metric | ผลจริง | SLO Target |
| --- | --- | --- |
| API throughput | 1,003 req/s | — |
| p95 API latency | 112 ms | < 200 ms ✅ |
| p99 API latency | 187 ms | < 500 ms ✅ |
| MQTT ingestion | ~400 events/s | — |
| Redis read latency | < 1 ms | — |

**Architecture Highlights :**

- Event-driven: MQTT → Kafka → Spring Boot → PostgreSQL
- Circuit Breaker + Retry + Replay Queue (Zero data loss during DB outage)
- Blue/Green deployment (Argo Rollouts) — zero-downtime
- Kubernetes-native autoscaling (KEDA + HPA)

---

## SECTION 9 : GROSS MARGIN & FINANCIALS

**Gross Margin Benchmark (2025–2026) :**

| บริษัท | ประเภท | Gross Margin | แหล่งข้อมูล |
| --- | --- | --- | --- |
| **Samsara (IOT)** | IoT SaaS | **77% GAAP** | SEC Form 8-K FY2026 |
| Datadog | Infrastructure SaaS | 80% GAAP | SEC FY2025 |
| Snowflake | Infrastructure SaaS | 67% GAAP | SEC FY2026 |
| Median SaaS | — | 74.62% | SaaSDB May 2026 |

**Sentinel Target: 77% Gross Margin** (เทียบเท่า Samsara ผู้นำตลาด IoT SaaS)

**3-Year Revenue Projection (Conservative Case) :**

| # | Year 1 | Year 2 | Year 3 |
| --- | --- | --- | --- |
| Customers | 7 | 25 | 60 |
| Avg MRR/customer | $700 | $900 | $1,100 |
| MRR (end of year) | $4,900 | $22,500 | $66,000 |
| ARR | ~$37,000 | ~$189,000 | ~$594,000 |
| Gross Margin | 77% | 77% | 77% |

**Key Assumptions :**

- Average plan = Small - Medium tier ($522 – $1,522)
- Churn < 5% annually (compliance-driven stickiness)
- No paid marketing Year 1 — founder-led sales only

**Scaling COGS (Infrastructure gets cheaper at scale) :**

- 200 devices: $0.60/device/month
- 2,000 devices: $0.175/device/month (↓71%)
- 10,000 devices: $0.12/device/month (↓80%)

---

## SECTION 10 : VISION & THE ASK

### Vision

> "Sentinel เป็น operating system สำหรับ physical world ของธุรกิจใน Southeast Asia"

**ในระยะ 5 ปี :**

- 10,000+ businesses monitored
- เป็น compliance infrastructure สำหรับ ASEAN Food Safety + Pharma regulation
- Expand: predictive maintenance, energy optimization, ESG reporting

### ✅ THE ASK

**Raising: $300,000 USD (Pre-Seed) :**

| Use of Funds | % | Amount | เป้าหมาย |
| --- | --- | --- | --- |
| Product & Infrastructure | 35% | $105,000 | Cloud infra + security audit + v2 features |
| Sales & Design Partners | 30% | $90,000 | Founder runway 18 เดือน + pilot program |
| Legal & Compliance | 15% | $45,000 | Company setup, IP, PDPA compliance |
| Marketing & Content | 10% | $30,000 | SEO + content targeting Food/Pharma ICP |
| Buffer | 10% | $30,000 | Contingency |

**Runway :**

- 18 months

**Milestones ที่จะ unlock ณ สิ้น runway :**

1. ✅ 3 Design Partners → paid customers (validation)
2. ✅ $10,000 MRR (product-market fit signal)
3. ✅ 1 System Integrator partnership signed
4. ✅ Ready for Seed Round ($1–2M) with proven unit economics

**Why now? :**

- FDA FSMA enforcement ปี 2026 กดดัน Food Manufacturers ทั่วโลกรวมถึง SEA
- Product พร้อม 100% — ไม่ใช่ prototype แต่เป็น production-grade platform
- ทีมลงทุนเวลา 12+ เดือนสร้าง infrastructure ก่อนขอเงิน

---

## SECTION 11: TEAM

### Solo Founder — Engineering-Led

**THITIPONG ROONGPRASERT** — Founder & CEO

**Background :**

- Software Engineer · 10+ ปี · Ex-Managing Director
- Technical · Full-stack + DevOps + IoT systems design
- สร้าง real-time system ที่รับ 10M+ requests/day ที่ Computerlogy (2020–2023)
- Redis caching → response time < 100 ms
- Ex-Managing Director — เข้าใจทั้ง technical และ business

**Built :**

- ใช้เวลา 12+ เดือนสร้าง Sentinel IoT Platform - production-grade, 107 tests, 1,003 req/s ก่อนขอทุน

**Why this problem :**

- "ผมเริ่มจาก Technical interest — เห็นว่า AWS IoT Core และ Azure IoT Hub เป็น infrastructure ที่ดี แต่ไม่มีใคร สร้าง ready-to-use platform ที่ครบวงจร สำหรับ industrial use case จริงๆ ผมจึงลงมือสร้างมันเอง"

**Why Solo Founder is an Asset (Pre-Seed) :**

- ตัดสินใจเร็ว, iterate เร็ว
- Technical credibility พิสูจน์ได้จาก codebase จริง
- Capital efficient — ไม่มี Co-Founder Equity Dilution ก่อน Product-market fit

**Planned First Hires (Post-Seed) :**

1. Sales / BD (Month 13–18) — ขยาย customer base
2. Senior Backend Engineer (Month 18+) — Scale Infrastructure

**Advisors Sought :**

- IoT Industry Domain Expert (Food/Pharma)
- Enterprise SaaS GTM Advisor

---

## APPENDIX A : TECHNOLOGY STACK

**Backend:** Spring Boot 3.2, Java 21, Spring Integration (MQTT), Apache Kafka, Resilience4j

**Frontend:** Next.js 14 (App Router), React Query, Zustand, TanStack Virtual

**Infrastructure:** PostgreSQL 16 (partitioned by month), Redis 7, Eclipse Mosquitto MQTT, Kafka with Avro + Schema Registry

**Deployment:** Docker Compose (dev) → Kubernetes + Helm (prod), Argo Rollouts (Blue/Green), KEDA autoscaling, CloudNativePG

**Observability:** Prometheus + Grafana, Jaeger (OpenTelemetry), Structured logging (Logstash JSON)

**Security:** JWT + Refresh Token rotation, Redis JTI blocklist, PostgreSQL Row Level Security, mTLS (opt-in), HMAC-SHA256 webhook signing

---

## APPENDIX B : SECURITY TEST COVERAGE

- JWT forgery, algorithm confusion, expiry, revocation
- Multi-tenant data isolation (cross-org IDOR prevention)
- SQL injection, XSS prevention
- Rate limiting, brute-force protection
- WebSocket authentication

---

## APPENDIX C : PLATFORM SCALABILITY TIERS

| Tier | Devices | AWS COGS/month | COGS/device/month |
| --- | --- | --- | --- |
| Starter | 1–50 | ~$40 | $0.80 |
| Small | 51–200 | $120 | $0.60 |
| Medium | 201–2,000 | $350 | $0.175 |
| Large | 2,001–10,000 | $1,200 | $0.12 |
| XLarge | 10,001–100,000 | ~$8,000 | $0.08 |
