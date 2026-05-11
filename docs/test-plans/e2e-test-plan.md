# E2E Test Plan — Sentinel IoT Dashboard (Cypress)

**Stack:** Next.js 14 · Cypress 13 · App Router  
**สถานะปัจจุบัน:** ❌ มี test file 1 ไฟล์ (4 tests) แต่รันไม่ได้ — ไม่มี `cypress.config.js`, URL ผิด, auth approach ผิด  
**เป้าหมาย:** ~39 test cases ครอบคลุม auth, device management, telemetry, alerts, admin features และ edge cases

---

## ปัญหาของ Test ปัจจุบัน

| # | ปัญหา | รายละเอียด |
|---|-------|-----------|
| 1 | ไม่มี `cypress.config.js` | Cypress รันไม่ได้เลย |
| 2 | URL interceptors ผิด | ใช้ `/api/auth/login`, `/api/devices` แต่จริงๆ เป็น `/api/v1/auth/login`, `/api/v1/devices` |
| 3 | Auth approach ผิด | `localStorage.setItem('sentinel_token', ...)` แต่ app ใช้ in-memory tokenStore + HttpOnly cookie |
| 4 | ไม่ mock `/api/v1/auth/refresh` | AuthProvider เรียก refresh ทุก page load → request จริงไปหา backend |
| 5 | ไม่มี support files | ไม่มี `commands.js`, `e2e.js` |
| 6 | ไม่มี fixtures | ข้อมูล mock อยู่ใน test โดยตรง ทำซ้ำในทุกไฟล์ |

---

## วิธีจัดการ Auth ใน Cypress (สำคัญมาก)

App ใช้ in-memory token store (ป้องกัน XSS) — ไม่เก็บ token ใน localStorage  
**วิธีที่ถูกต้อง:** Mock `POST /api/v1/auth/refresh` ให้คืน access token ทันที → AuthProvider จะ set user โดยอัตโนมัติ ไม่ต้องแตะ localStorage เลย

```js
// cypress/support/commands.js
Cypress.Commands.add('mockAuthAsAdmin', () => {
  cy.intercept('POST', '/api/v1/auth/refresh', {
    statusCode: 200,
    body: { accessToken: 'fake-admin-token', username: 'admin', role: 'ADMIN' }
  }).as('refresh')
})

Cypress.Commands.add('mockAuthAsOperator', () => {
  cy.intercept('POST', '/api/v1/auth/refresh', {
    statusCode: 200,
    body: { accessToken: 'fake-op-token', username: 'operator', role: 'OPERATOR' }
  }).as('refresh')
})
```

---

## โครงสร้างไฟล์ที่จะสร้าง

```
frontend/
├── cypress.config.js                    (สร้างใหม่)
├── cypress/
│   ├── support/
│   │   ├── e2e.js                       (สร้างใหม่ — global setup)
│   │   └── commands.js                  (สร้างใหม่ — custom commands)
│   ├── fixtures/
│   │   ├── devices.json                 (สร้างใหม่)
│   │   ├── alerts.json                  (สร้างใหม่)
│   │   ├── telemetry.json               (สร้างใหม่)
│   │   └── stats.json                   (สร้างใหม่)
│   └── e2e/
│       ├── auth.cy.js                   (สร้างใหม่)
│       ├── dashboard.cy.js              (เขียนใหม่ทั้งหมด)
│       ├── device-filters.cy.js         (สร้างใหม่)
│       ├── telemetry-chart.cy.js        (สร้างใหม่)
│       ├── alerts.cy.js                 (สร้างใหม่)
│       ├── admin.cy.js                  (สร้างใหม่)
│       └── edge-cases.cy.js             (สร้างใหม่)
```

---

## รายละเอียด Test แต่ละไฟล์

---

### 1. `cypress.config.js` — Configuration

```js
// ค่าสำคัญ
baseUrl: 'http://localhost:3000'
viewportWidth: 1440
viewportHeight: 900
defaultCommandTimeout: 8000
video: false
```

---

### 2. `auth.cy.js` — 5 tests

ทดสอบ authentication flow ครบวงจร

| Test | สิ่งที่ตรวจสอบ |
|------|--------------|
| redirects unauthenticated user from `/` to `/login` | refresh คืน 401 → URL เป็น `/login` |
| redirects unauthenticated user from `/dashboard` to `/login` | เข้า `/dashboard` โดยไม่มี auth → redirect |
| login with valid credentials navigates to `/dashboard` | กรอก admin/admin123 → POST `/auth/login` → redirect `/dashboard` |
| login with invalid credentials shows error message | กรอก password ผิด → แสดง error message ใน form |
| logout clears session and redirects to `/login` | กด logout → POST `/auth/logout` → redirect `/login` |

---

### 3. `dashboard.cy.js` — 6 tests (เขียนใหม่)

ทดสอบ dashboard overview: StatsBar, layout, และ initial data load

| Test | สิ่งที่ตรวจสอบ |
|------|--------------|
| shows StatsBar with correct total device count | 5 devices → "Total Devices" แสดง 5 |
| shows online/offline counts | 3 ONLINE, 2 OFFLINE → card ถูกต้อง |
| shows critical alert count | 2 CRITICAL unacked → "Critical Alerts" = 2 |
| shows events-per-minute from stats API | `lastMinute: 42` → "Events / min" แสดง 42 |
| shows warning color on buffered count > 0 | `replayQueueSize: 5` → card มี warning styling |
| device list renders after data loads | devices load → `sensor-1`, `sensor-2` ปรากฏใน DOM |

---

### 4. `device-filters.cy.js` — 7 tests

ทดสอบ DeviceTable filtering, sorting, และ selection

| Test | สิ่งที่ตรวจสอบ |
|------|--------------|
| search filters device list by name | พิมพ์ "sensor-a" → เหลือเฉพาะ device ที่ชื่อตรง |
| status filter shows only ONLINE devices | เลือก "Online" → ซ่อน OFFLINE devices |
| lifecycle filter shows only ACTIVE devices | เลือก "ACTIVE" → ซ่อน lifecycle อื่น |
| device count label reflects filtered result | filter เหลือ 2 จาก 5 → แสดง "2 of 5 devices" |
| clear button resets all filters | filter แล้วกด Clear → แสดง devices ครบ |
| clicking a device row selects it | คลิก row → row มี `aria-selected="true"` |
| selected device triggers telemetry chart load | เลือก device → GET `/telemetry/{id}/latest` ถูกเรียก |

---

### 5. `telemetry-chart.cy.js` — 6 tests

ทดสอบ TelemetryChart tab switching และ time window

| Test | สิ่งที่ตรวจสอบ |
|------|--------------|
| shows Temperature/Humidity tab by default | default tab คือ "Temperature / Humidity" |
| switching to Smoke tab loads smoke data | คลิก "Smoke (ppm)" → chart แสดง smoke axis |
| switching to Motion tab loads motion data | คลิก "Motion" → chart แสดง motion data |
| switching time window to 1h calls range API | คลิก "1h" → GET `/telemetry/{id}/range?from=...` ถูกเรียก |
| switching time window to 24h calls hourly API | คลิก "24h" → GET `/telemetry/{id}/hourly?from=...` ถูกเรียก |
| switching time window to 7d calls hourly API | คลิก "7d" → GET `/telemetry/{id}/hourly?from=...` ถูกเรียก |

---

### 6. `alerts.cy.js` — 5 tests

ทดสอบ AlertList filter tabs และ acknowledge flow

| Test | สิ่งที่ตรวจสอบ |
|------|--------------|
| shows all alerts by default | render → แสดง alert ทั้งหมด |
| unacknowledged badge shows correct count | 2 unacked alerts → badge แสดง "2" |
| clicking Unacknowledged tab filters list | คลิก tab → แสดงเฉพาะ unacked |
| ADMIN sees Acknowledge button on unacked alert | role ADMIN + unacked alert → ปุ่ม "Ack" ปรากฏ |
| clicking Acknowledge calls API and removes from unacked tab | กด Ack → PUT `/alerts/{id}/acknowledge` ถูกเรียก |

---

### 7. `admin.cy.js` — 6 tests

ทดสอบ DeviceManagement component (ADMIN-only)

| Test | สิ่งที่ตรวจสอบ |
|------|--------------|
| ADMIN sees lifecycle controls | role ADMIN → dropdown lifecycle แสดงอยู่ |
| OPERATOR does not see lifecycle controls | role OPERATOR → ไม่มี lifecycle controls ใน DOM |
| lifecycle transition calls PATCH API | เปลี่ยน lifecycle → PATCH `/devices/{id}/lifecycle` ถูกเรียก |
| firmware input validates semver format | กรอก "not-semver" → แสดง validation error |
| firmware update calls PATCH API with correct body | กรอก "2.1.0" แล้ว submit → PATCH `/devices/{id}/firmware` ถูกเรียก |
| DECOMMISSIONED device disables all controls | lifecycle = DECOMMISSIONED → controls ทั้งหมด disabled |

---

### 8. `edge-cases.cy.js` — 4 tests

ทดสอบ error states และ banner notifications

| Test | สิ่งที่ตรวจสอบ |
|------|--------------|
| shows OfflineBanner when network goes offline | `cy.goOffline()` → banner "You are offline" ปรากฏ |
| dismissing OfflineBanner hides it | กด X บน banner → banner หาย |
| shows VersionBanner on api-version mismatch | response header `api-version: 2` → banner "A new version is available" ปรากฏ |
| shows VersionBanner on 406 response | API คืน 406 → banner "Client outdated" ปรากฏ |

---

## สรุปภาพรวม

| กลุ่ม | Test Files | Test Cases |
|-------|-----------|------------|
| Authentication | 1 | 5 |
| Dashboard Overview | 1 | 6 |
| Device Filters | 1 | 7 |
| Telemetry Chart | 1 | 6 |
| Alerts | 1 | 5 |
| Admin Features | 1 | 6 |
| Edge Cases | 1 | 4 |
| **รวม** | **7 files** | **39 tests** |

---

## Fixtures ที่ต้องสร้าง

### `devices.json`
```json
[
  { "id": "uuid-1", "name": "sensor-alpha", "status": "ONLINE",  "lifecycleStatus": "ACTIVE",       "location": "Factory A", "firmwareVersion": "1.2.0", "lastSeen": "<iso>" },
  { "id": "uuid-2", "name": "sensor-beta",  "status": "OFFLINE", "lifecycleStatus": "ACTIVE",       "location": "Factory B", "firmwareVersion": "1.1.0", "lastSeen": null },
  { "id": "uuid-3", "name": "sensor-gamma", "status": "ONLINE",  "lifecycleStatus": "PROVISIONED",  "location": "Factory A", "firmwareVersion": "1.0.0", "lastSeen": "<iso>" },
  { "id": "uuid-4", "name": "sensor-delta", "status": "ONLINE",  "lifecycleStatus": "INACTIVE",     "location": "Factory C", "firmwareVersion": "1.2.0", "lastSeen": "<iso>" },
  { "id": "uuid-5", "name": "sensor-omega", "status": "OFFLINE", "lifecycleStatus": "DECOMMISSIONED","location": "Factory C", "firmwareVersion": "0.9.0", "lastSeen": null }
]
```

### `alerts.json`
```json
[
  { "id": "a1", "deviceId": "uuid-1", "level": "CRITICAL", "message": "Temperature exceeded 80°C", "acknowledged": false, "createdAt": "<iso>" },
  { "id": "a2", "deviceId": "uuid-2", "level": "CRITICAL", "message": "Smoke level exceeded 200 ppm", "acknowledged": false, "createdAt": "<iso>" },
  { "id": "a3", "deviceId": "uuid-3", "level": "WARNING",  "message": "Temperature above 70°C",     "acknowledged": true,  "createdAt": "<iso>" }
]
```

### `stats.json`
```json
{ "lastMinute": 42, "replayQueueSize": 5 }
```

### `telemetry.json`
```json
[
  { "deviceId": "uuid-1", "temperature": 72.4, "humidity": 55.0, "smokePpm": 15.0, "motion": false, "timestamp": "<iso>" },
  { "deviceId": "uuid-1", "temperature": 75.1, "humidity": 53.2, "smokePpm": 18.0, "motion": true,  "timestamp": "<iso>" }
]
```

---

## ลำดับการ Implement (แนะนำ)

```
Priority 1 — Setup & Infrastructure
  cypress.config.js → fixtures → support/commands.js → support/e2e.js

Priority 2 — Core flow ที่ครอบคลุมกว้างที่สุด
  auth.cy.js → dashboard.cy.js

Priority 3 — Feature tests
  device-filters.cy.js → alerts.cy.js → telemetry-chart.cy.js

Priority 4 — Role-based & edge cases
  admin.cy.js → edge-cases.cy.js
```

---

## Cypress Configuration

```js
// cypress.config.js
const { defineConfig } = require('cypress')

module.exports = defineConfig({
  e2e: {
    baseUrl: 'http://localhost:3000',
    viewportWidth: 1440,
    viewportHeight: 900,
    defaultCommandTimeout: 8000,
    video: false,
    screenshotOnRunFailure: true,
    specPattern: 'cypress/e2e/**/*.cy.{js,jsx}',
    supportFile: 'cypress/support/e2e.js',
  },
})
```

## package.json scripts ที่ต้องเพิ่ม

```json
"test:e2e": "cypress run --e2e",
"test:e2e:open": "cypress open --e2e"
```

> **หมายเหตุ:** script `"test"` เดิม (`cypress run --e2e`) ยังใช้งานได้ แต่แนะนำให้แยก script ให้ชัดเจนระหว่าง E2E และ Unit tests (เมื่อ implement Unit tests แล้ว)
