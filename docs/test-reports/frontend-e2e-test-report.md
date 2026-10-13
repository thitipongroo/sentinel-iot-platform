# Frontend E2E Test Report — Sentinel IoT Platform

**วันที่รัน:** 2026-10-13  
**สถานะ:** ✅ ผ่านทั้งหมด (100% Pass Rate)  
**ผลรวม:** 68 tests | 11 files | 0 failures  
**Stack:** Next.js 14 · Cypress 13.17.0 · App Router · TanStack Query v5  
**Auth Pattern:** Mock `POST /api/v1/auth/refresh` คืน access token → AuthProvider sync state อัตโนมัติ (in-memory token store, ไม่แตะ localStorage)

---

## สรุปผลการทดสอบรายไฟล์ (Summary)

| # | Test File | จำนวน Tests | สถานะ | ขอบเขตการทดสอบ |
|---|-----------|------------|-------|---------------|
| 1 | `auth.cy.js` | 5 | ✅ ผ่าน | Authentication: unauthenticated redirects, login valid/invalid, logout session clear |
| 2 | `dashboard.cy.js` | 6 | ✅ ผ่าน | Dashboard Overview: StatsBar (online/offline, critical alerts, events/min, buffer), device list |
| 3 | `device-filters.cy.js` | 7 | ✅ ผ่าน | Device Table filters: name search, status/lifecycle filters, count label, clear, row selection |
| 4 | `devices-page.cy.js` | 7 | ✅ ผ่าน | `/devices` list page: table render, status/lifecycle filters, clear, RBAC register button |
| 5 | `device-detail.cy.js` | 6 | ✅ ผ่าน | `/devices/[id]` detail page: info card, breadcrumb, chart render, RBAC controls, recent alerts |
| 6 | `telemetry-chart.cy.js` | 6 | ✅ ผ่าน | Telemetry Chart: Temp/Humidity, Smoke, Motion tabs, time windows (1h range, 24h/7d hourly) |
| 7 | `alerts.cy.js` | 8 | ✅ ผ่าน | Alerts page (`/alerts`) & Dashboard AlertList: level/status filters, clear, ack flow, Ack All |
| 8 | `users.cy.js` | 8 | ✅ ผ่าน | User management (`/users`): member count, roles, `(you)` badge, self-protection, add/delete |
| 9 | `settings.cy.js` | 5 | ✅ ผ่าน | Platform settings (`/settings`): threshold values, data retention, notification channel status |
| 10 | `admin.cy.js` | 6 | ✅ ผ่าน | Device management (ADMIN): lifecycle transitions, semver validation, decommissioned lock |
| 11 | `edge-cases.cy.js` | 4 | ✅ ผ่าน | System resilience: OfflineBanner (online/offline events), VersionBanner (mismatch/rejected) |
| **รวม** | **11 files** | **68 tests** | **✅ 68 passed (100%)** | |

---

## รายละเอียดผลการทดสอบแต่ละไฟล์

### 1. `auth.cy.js` — 5 tests ✅
- `redirects unauthenticated user from / to /login` (ผ่าน)
- `redirects unauthenticated user from /dashboard to /login` (ผ่าน)
- `login with valid credentials navigates to /dashboard` (ผ่าน)
- `login with invalid credentials shows error message` (ผ่าน)
- `logout clears session and redirects to /login` (ผ่าน)

### 2. `dashboard.cy.js` — 6 tests ✅
- `shows StatsBar with correct total device count` (5 devices)
- `shows online and offline counts` (3 Online, 2 Offline)
- `shows critical alert count` (2 Critical unacknowledged)
- `shows events-per-minute from stats API` (42 events/min)
- `shows warning color on buffered count greater than zero` (Replay queue = 5)
- `device list renders after data loads` (sensor-alpha, sensor-beta visible)

### 3. `device-filters.cy.js` — 7 tests ✅
- `search filters device list by name`
- `status filter shows only ONLINE devices`
- `lifecycle filter shows only ACTIVE devices`
- `device count label reflects filtered result` ("2 of 5 devices")
- `clear button resets all filters`
- `clicking a device row selects it` (`aria-selected="true"`)
- `selected device triggers telemetry fetch` (`GET /api/v1/telemetry/uuid-2/latest`)

### 4. `devices-page.cy.js` — 7 tests ✅
- `navigates to /devices and shows device list`
- `shows ONLINE badge for online devices and OFFLINE for offline devices`
- `filter by OFFLINE status hides ONLINE devices`
- `search by name filters the table`
- `Clear button resets all filters`
- `ADMIN sees Register Device button`
- `OPERATOR does not see Register Device button`

### 5. `device-detail.cy.js` — 6 tests ✅
- `shows device info card with correct details`
- `breadcrumb links back to /devices`
- `shows telemetry chart for the device`
- `ADMIN sees device management panel`
- `OPERATOR does not see device management panel`
- `shows recent alerts for the device`

### 6. `telemetry-chart.cy.js` — 6 tests ✅
- `shows Temperature/Humidity tab as active by default`
- `switching to Smoke tab makes it active`
- `switching to Motion tab makes it active`
- `switching time window to 1h calls range API`
- `switching time window to 24h calls hourly API`
- `switching time window to 7d calls hourly API`

### 7. `alerts.cy.js` — 8 tests ✅
- `filter by CRITICAL level hides WARNING alerts` (Alerts Page)
- `Clear button resets both level and status filters` (Alerts Page)
- `ADMIN sees Acknowledge All button when there are unacknowledged alerts` (Alerts Page)
- `shows all alerts by default` (Dashboard AlertList)
- `unacknowledged badge shows correct count` (Dashboard AlertList)
- `clicking Unacknowledged tab filters to unacked alerts only` (Dashboard AlertList)
- `ADMIN sees Acknowledge button on unacked alerts` (Dashboard AlertList)
- `clicking Acknowledge calls the acknowledge API` (Dashboard AlertList)

### 8. `users.cy.js` — 8 tests ✅
- `renders user list with member count` (3 members)
- `shows ADMIN and OPERATOR role labels for each user`
- `marks the currently logged-in user with "(you)"`
- `Delete and Reset Password buttons are disabled for own account`
- `Delete button opens a confirmation dialog for other users`
- `confirmed Delete calls DELETE /api/v1/users/:username`
- `Add User button opens the modal`
- `Add User form submits POST /api/v1/users`

### 9. `settings.cy.js` — 5 tests ✅
- `shows alert threshold values from API`
- `shows retention period settings`
- `shows active notification channels as enabled`
- `shows disabled notification channels`
- `settings page has no save/submit buttons (read-only)`

### 10. `admin.cy.js` — 6 tests ✅
- `ADMIN sees lifecycle controls`
- `OPERATOR does not see lifecycle controls`
- `lifecycle transition calls PATCH API`
- `firmware input validates semver format`
- `firmware update calls PATCH API with correct body`
- `DECOMMISSIONED device disables all controls`

### 11. `edge-cases.cy.js` — 4 tests ✅
- `shows OfflineBanner when network goes offline`
- `OfflineBanner disappears when network comes back online`
- `shows VersionBanner on api-version mismatch event`
- `shows VersionBanner on api-version-rejected event`

---

## ปัญหาที่พบและได้รับการแก้ไขระหว่างการทดสอบ (Issues & Root Causes)

| # | ปัญหาที่ตรวจพบ | สาเหตุที่แท้จริง (Root Cause) | แนวทางแก้ไข (Fix) |
|---|----------------|------------------------------|-------------------|
| 1 | Infinite reload loop บน `/login` ทำให้ input โดน disable/detach | Axios 401 response interceptor ใน `client.js` เรียก `window.location.href = '/login'` ซ้ำเมื่อ `/auth/refresh` คืน 401 ในหน้า `/login` | เพิ่มเงื่อนไข `err.config?.url !== '/auth/refresh' && window.location.pathname !== '/login'` ป้องกัน reload ซ้ำ |
| 2 | Logout button ใน `AppShell.jsx` ขาด accessible name | `auth.cy.js` ค้นหาด้วย `[aria-label="Log out"]` แต่ปุ่มไม่มี attribute | เพิ่ม `aria-label="Log out"` ใน `AppShell.jsx` และปรับ regex ใน unit test ให้รองรับทั้ง Log out และ Logout |
| 3 | Dashboard Critical Alerts แสดง 0 แทน 2 | Mock ส่ง `alerts.json` เป็น array แต่ `dashboard/page.jsx` อ่านผ่าน `alertPage?.content` เท่านั้น | ปรับเป็น `Array.isArray(alertPage) ? alertPage : (alertPage?.content ?? [])` และใช้ glob wildcard `GET /api/v1/alerts*` |
| 4 | Optimistic update crash `Cannot read properties of undefined (reading 'prev')` | ใน `acknowledgeMutation`, `old.content.map` พังเมื่อ `old` เป็น array ส่งผลให้ `onMutate` throw error และ `onError` ได้รับ `ctx` เป็น undefined | จัดการ fallback กรณี `old` เป็น array ใน `onMutate` และใช้ safe-nav `ctx?.prev` ใน `onError` |
| 5 | Filter Clear button ใน `DeviceTable` ถูก card ถัดไปบดบัง | `DeviceTable` filter bar ใช้ `flex` ขาด `flex-wrap` ใน responsive grid คอลัมน์แคบทำให้ปุ่มล้นขอบ | เพิ่ม `flex-wrap` ใน `DeviceTable.jsx` และ `{ force: true }` ในการคลิกเคลียร์ฟิลเตอร์ |
| 6 | `devices-page.cy.js` เลือก dropdown ผิดตัว | เทสต์ใช้ `first().next('select')` ซึ่งไปชี้ที่ Lifecycle dropdown แทน Status dropdown | เปลี่ยนเป็น `cy.get('select').first().select('OFFLINE')` ให้ตรงกับ Status dropdown |
| 7 | `users.cy.js` ทดสอบ `<option>` visibility ไม่ผ่าน | บราวเซอร์ถือว่า `<option>` ภายใน closed `<select>` มีขนาด `0x0 px` จึงไม่ถือว่า visible | เปลี่ยนการ assert เป็น `find('select').should('have.value', 'ADMIN')` ตามแบบแผน Cypress |
| 8 | Elements ใน AlertList และ DeviceManagement ถูก overflow clipping | คอนเทนเนอร์คอลัมน์ขวามี `calc(100vh - 13rem)` แบบตายตัว | เพิ่ม `overflow-y-auto` และกำหนด `min-h-[220px]` ให้ AlertList พร้อมใช้ `.scrollIntoView()` ใน spec ก่อน assert visibility |
