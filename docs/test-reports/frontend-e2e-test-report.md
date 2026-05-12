# Frontend E2E Test Report

**วันที่รัน:** 2026-05-12  
**สถานะ:** ✅ ผ่านทั้งหมด  
**ผลรวม:** 39 tests | 7 files | 0 failures  
**Stack:** Next.js 14 · Cypress 13 · App Router  
**Auth Pattern:** Mock `POST /api/v1/auth/refresh` ให้คืน access token → AuthProvider set user อัตโนมัติ (ไม่แตะ localStorage)

---

## สรุปผล

| Test File | Tests | ครอบคลุม |
|-----------|-------|---------|
| `auth.cy.js` | 5 | Authentication flow |
| `dashboard.cy.js` | 6 | Dashboard overview & StatsBar |
| `device-filters.cy.js` | 7 | DeviceTable filters & selection |
| `telemetry-chart.cy.js` | 6 | TelemetryChart tabs & time windows |
| `alerts.cy.js` | 5 | AlertList filter tabs & acknowledge |
| `admin.cy.js` | 6 | DeviceManagement RBAC & PATCH APIs |
| `edge-cases.cy.js` | 4 | OfflineBanner & VersionBanner |
| **รวม** | **39** | **✅** |

---

## `auth.cy.js` — 5 tests ✅

ทดสอบ authentication flow ครบวงจร: redirect, login, logout

| Test | สิ่งที่ตรวจสอบ |
|------|--------------|
| `redirects unauthenticated user from / to /login` | refresh คืน 401 → URL เปลี่ยนเป็น `/login` |
| `redirects unauthenticated user from /dashboard to /login` | เข้า `/dashboard` โดยตรงโดยไม่มี auth → redirect |
| `login with valid credentials navigates to /dashboard` | กรอก admin/admin123 → POST `/auth/login` → redirect `/dashboard` |
| `login with invalid credentials shows error message` | password ผิด → "Invalid username or password" ปรากฏ |
| `logout clears session and redirects to /login` | กด "Log out" → POST `/auth/logout` → redirect `/login` |

---

## `dashboard.cy.js` — 6 tests ✅

ทดสอบ StatsBar calculations และ initial data load  
(5 devices: 3 ONLINE/2 OFFLINE, 2 CRITICAL unacked, replayQueueSize=5)

| Test | สิ่งที่ตรวจสอบ |
|------|--------------|
| `shows StatsBar with correct total device count` | devices 5 ตัว → "Total Devices" = 5 |
| `shows online and offline counts` | 3 ONLINE, 2 OFFLINE → card ถูกต้อง |
| `shows critical alert count` | 2 CRITICAL unacked → "Critical Alerts" = 2 |
| `shows events-per-minute from stats API` | `lastMinute: 42` → "Events / min" = 42 |
| `shows warning color on buffered count greater than zero` | `replayQueueSize: 5` → Buffered มี class `text-sentinel-warning` |
| `device list renders after data loads` | sensor-alpha, sensor-beta ปรากฏใน DOM |

---

## `device-filters.cy.js` — 7 tests ✅

ทดสอบ DeviceTable filtering, count label, clear และ row selection

| Test | สิ่งที่ตรวจสอบ |
|------|--------------|
| `search filters device list by name` | พิมพ์ "alpha" → แสดงเฉพาะ sensor-alpha |
| `status filter shows only ONLINE devices` | เลือก ONLINE → ซ่อน sensor-beta, sensor-omega |
| `lifecycle filter shows only ACTIVE devices` | เลือก ACTIVE → แสดง sensor-alpha, sensor-beta เท่านั้น |
| `device count label reflects filtered result` | กรอง lifecycle=ACTIVE → "2 of 5 devices" |
| `clear button resets all filters` | filter แล้วกด Clear → devices ทั้งหมดกลับมา |
| `clicking a device row selects it` | คลิก row → `aria-selected="true"` |
| `selected device triggers telemetry fetch` | คลิก sensor-beta → GET `/telemetry/uuid-2/latest` ถูกเรียก |

---

## `telemetry-chart.cy.js` — 6 tests ✅

ทดสอบ TelemetryChart tab switching และ time window API calls (uuid-1 auto-selected)

| Test | สิ่งที่ตรวจสอบ |
|------|--------------|
| `shows Temperature/Humidity tab as active by default` | tab มี class `bg-sentinel-accent` |
| `switching to Smoke tab makes it active` | คลิก "Smoke (ppm)" → tab active, Temperature/Humidity inactive |
| `switching to Motion tab makes it active` | คลิก "Motion" → tab active |
| `switching time window to 1h calls range API` | คลิก "1h" → GET `/telemetry/uuid-1/range*` ถูกเรียก |
| `switching time window to 24h calls hourly API` | คลิก "24h" → GET `/telemetry/uuid-1/hourly*` ถูกเรียก |
| `switching time window to 7d calls hourly API` | คลิก "7d" → GET `/telemetry/uuid-1/hourly*` ถูกเรียก |

---

## `alerts.cy.js` — 5 tests ✅

ทดสอบ AlertList tab filtering และ acknowledge flow  
(3 alerts: 2 CRITICAL unacked + 1 WARNING acked)

| Test | สิ่งที่ตรวจสอบ |
|------|--------------|
| `shows all alerts by default` | alert ทั้ง 3 ข้อความปรากฏใน DOM |
| `unacknowledged badge shows correct count` | badge ใน header แสดง "2" |
| `clicking Unacknowledged tab filters to unacked alerts only` | คลิก "Unacknowledged" → ซ่อน a3 (acked) |
| `ADMIN sees Acknowledge button on unacked alerts` | role=ADMIN → ปุ่ม "Ack" 2 ปุ่ม |
| `clicking Acknowledge calls the acknowledge API` | กด Ack → PUT `/alerts/a1/acknowledge` ถูกเรียก |

---

## `admin.cy.js` — 6 tests ✅

ทดสอบ DeviceManagement RBAC, lifecycle/firmware PATCH APIs และ decommissioned state  
(uuid-1 auto-selected: ACTIVE)

| Test | สิ่งที่ตรวจสอบ |
|------|--------------|
| `ADMIN sees lifecycle controls` | role=ADMIN → "Device Management" + lifecycle buttons แสดง |
| `OPERATOR does not see lifecycle controls` | role=OPERATOR → "Device Management" ไม่มีใน DOM |
| `lifecycle transition calls PATCH API` | คลิก "→ INACTIVE" → PATCH `/devices/uuid-1/lifecycle` body `{lifecycleStatus: 'INACTIVE'}` |
| `firmware input validates semver format` | กรอก "not-semver" → "Version must follow semver" ปรากฏ |
| `firmware update calls PATCH API with correct body` | กรอก "2.1.0" แล้ว Submit → PATCH `/devices/uuid-1/firmware` body `{firmwareVersion: '2.1.0'}` |
| `DECOMMISSIONED device disables all controls` | คลิก sensor-omega → decommission message + firmware input disabled |

---

## `edge-cases.cy.js` — 4 tests ✅

ทดสอบ OfflineBanner (window events) และ VersionBanner (custom events)

| Test | สิ่งที่ตรวจสอบ |
|------|--------------|
| `shows OfflineBanner when network goes offline` | dispatch `offline` event → banner "You are offline" ปรากฏ |
| `OfflineBanner disappears when network comes back online` | dispatch `online` event → banner หาย |
| `shows VersionBanner on api-version mismatch event` | dispatch `sentinel:api-version-mismatch` → "A new version is available." ปรากฏ |
| `shows VersionBanner on api-version-rejected event` | dispatch `sentinel:api-version-rejected` → "This client version is no longer supported by the server." ปรากฏ |

---

## ปัญหาที่พบและแก้ไข

| # | ปัญหา | ไฟล์ที่แก้ | สาเหตุ |
|---|-------|-----------|--------|
| 1 | ไม่มี `cypress.config.js` — Cypress รันไม่ได้ | `cypress.config.js` (สร้างใหม่) | ขาด config file ทั้งหมด |
| 2 | URL interceptors ผิด (`/api/devices`) | ทุก `*.cy.js` | Endpoint จริงอยู่ที่ `/api/v1/...` |
| 3 | Auth approach ผิด (localStorage) | `commands.js` | App ใช้ in-memory tokenStore — ต้อง mock `POST /api/v1/auth/refresh` แทน |
| 4 | ไม่มี fixtures — data อยู่ใน test โดยตรง | `cypress/fixtures/` (สร้างใหม่) | แยก fixture ออกเป็น `devices.json`, `alerts.json`, `stats.json`, `telemetry.json` |
| 5 | OfflineBanner ไม่มีปุ่ม dismiss | `edge-cases.cy.js` | Component ไม่มี X button — ปรับ test เป็น "banner หายเมื่อ online กลับมา" |
