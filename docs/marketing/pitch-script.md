# Sentinel IoT Platform — Investor Pitch Script (5 นาที)

---

## Slide 1: Problem (30 วินาที)

> "ทุกวันนี้ โรงงานอาหาร คลังยา และอาคารพาณิชย์หลายแสนแห่งทั่วโลก ยังใช้คนเดินตรวจสอบอุณหภูมิด้วยมือ
>
> ผลลัพธ์คือ — รู้ทีหลังเสมอ
>
> สินค้า cold chain เสียหาย 1 ครั้ง ค่าเสียหายเริ่มต้นที่ $100,000 ถึง $1,000,000
> และ FDA FSMA บังคับให้เก็บ audit trail หลายปี แต่ไม่มีระบบรองรับ"

---

## Slide 2: Solution (30 วินาที)

> "เราสร้าง Sentinel IoT Platform — ระบบ monitoring industrial IoT แบบ real-time ที่พร้อมใช้งานทันที
>
> อุปกรณ์ส่งข้อมูลทุก 5 วินาที ระบบประมวลผลและแจ้งเตือนอัตโนมัติผ่าน Slack และ Webhook
> ถ้าอุณหภูมิเกิน threshold — ทีมได้รับแจ้งภายใน 1 วินาที ไม่ใช่ 1 ชั่วโมง
>
> รองรับตั้งแต่ 50 ถึง 100,000 devices บน architecture เดียวกัน"

---

## Slide 3: Product (30 วินาที)

> "นี่คือ dashboard จริงของเรา
>
> ทางซ้าย — real-time telemetry chart ทุก sensor อัปเดตสดทุกครั้งที่มีข้อมูลใหม่
> ตรงกลาง — alert panel แยก acknowledged และ unacknowledged ชัดเจน
> ทางขวา — Grafana observability dashboard สำหรับทีม ops
>
> ทั้งหมดนี้ multi-tenant — แต่ละองค์กร isolated ระดับ database ไม่มีข้อมูลรั่วไหลข้าม tenant"

---

## Slide 4: Market & ICP (30 วินาที)

> "ลูกค้าเป้าหมายหลักของเราคือ 4 กลุ่ม
>
> หนึ่ง — Food & Pharma Cold Chain: ของเสียหาย 1 ครั้งสูงกว่าค่า platform ทั้งปี
> สอง — โรงงานอาหาร: กฎหมายบังคับเก็บ log
> สาม — Building Management: CO2 sensor ช่วยประหยัดพลังงาน $10,000–$50,000 ต่อปี ต่ออาคาร
> สี่ — โรงงานอุตสาหกรรม: predictive maintenance
>
> ทุกกลุ่มมีปัญหาเดียวกัน และ willingness to pay สูงเพราะค่าเสียหายสูงกว่าค่า subscription มาก"

---

## Slide 5: Business Model (30 วินาที)

> "เรามี 7 revenue streams
>
> รากฐานคือ SaaS subscription รายเดือนตาม device count
> บนนั้นคือ add-ons: data retention สำหรับ compliance, API access สำหรับ SCADA integrators, enterprise pack สำหรับ multi-facility
> และ OEM license สำหรับ hardware manufacturer ที่ต้องการ branded platform
>
> ลูกค้า Medium tier ที่ซื้อ add-ons ทั้งหมด สร้างรายได้ $32,964 ต่อปี ต่อราย"

---

## Slide 6: Pricing & Unit Economics (30 วินาที)

> "COGS ของเราคำนวณจาก AWS infrastructure จริง
>
> ที่ 200 devices — COGS $0.60 ต่อ device ต่อเดือน ขายที่ $522 ต่อเดือน
> ที่ 10,000 devices — COGS ลดเหลือ $0.12 ต่อ device economies of scale ทำงานชัดเจน
>
> Add-ons มี gross margin เกือบ 100% เพราะใช้ infrastructure ที่จ่ายไปแล้ว — Slack API ฟรี, S3 storage เพิ่มแค่ $0.19 ต่อเดือน สำหรับ $99 ที่เก็บ"

---

## Slide 7: Competitive Advantage (30 วินาที)

> "คู่แข่งหลักมี 2 กลุ่ม
>
> Infrastructure providers อย่าง AWS IoT Core และ Azure IoT Hub — ไม่มี dashboard พร้อมใช้ ต้องมีทีม engineer สร้างเอง
> Platform players อย่าง Losant และ Particle — Losant เก็บเงินตาม payload คาดเดายาก Particle ผูกกับ hardware ตัวเอง
>
> Sentinel เป็น full-stack platform พร้อมใช้ทันที hardware-agnostic และราคาชัดเจนตาม device count"

---

## Slide 8: Technical Credibility (30 วินาที)

> "ระบบผ่านการทดสอบ 107 tests — unit, integration, และ security — 100% pass rate
>
> Security tests ครอบคลุม JWT forgery, SQL injection, cross-tenant data isolation และ brute-force protection
>
> Load test บน single node: API throughput 1,003 requests ต่อวินาที, p95 latency 112ms ต่ำกว่า SLO 200ms
>
> Architecture มี circuit breaker และ replay queue — ถ้า database down ข้อมูลไม่หาย ระบบ recover อัตโนมัติ"

---

## Slide 9: Gross Margin & Financials (30 วินาที)

> "Target gross margin ของเราคือ 77% — เทียบเท่า Samsara ซึ่งเป็น IoT SaaS leader รายงาน 77% GAAP ใน FY2026
>
> นี่คือตัวเลขที่นักลงทุนต้องการ — 75% ขึ้นไปคือ clean SaaS story 80% ขึ้นไปคือ premium multiple ที่ 7x revenue
>
> และยิ่ง scale มาก COGS ต่อ device ยิ่งลดลง — จาก $0.60 เหลือ $0.12 เมื่อ scale ถึง 10,000 devices"

---

## Slide 10: Vision & The Ask (30 วินาที)

> "วิสัยทัศน์ของเรา — เป็น IoT monitoring platform มาตรฐานสำหรับทุกอุตสาหกรรมที่ต้องการ compliance และ safety
>
> Platform พร้อมแล้ว ทีมพร้อมแล้ว ตลาดพร้อมแล้ว
>
> เราต้องการระดมทุน **$500,000 Pre-Seed** เพื่อ 3 เป้าหมาย:
> หนึ่ง — Go-to-Market สำหรับ Food & Pharma Cold Chain ซึ่งเป็นกลุ่มลูกค้าที่มี willingness to pay สูงที่สุด
> สอง — Production deployment บน Kubernetes และพัฒนา Enterprise features: SSO และ Alert escalation rules
> สาม — 18-month runway เพื่อปิด 10 ลูกค้าแรก ซึ่งสร้างรายได้ $27,470 ต่อเดือน
>
> ขอบคุณครับ มีคำถามอะไรไหมครับ?"
