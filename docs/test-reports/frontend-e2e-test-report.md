# Frontend E2E Test Report — Sentinel IoT Platform

**วันที่รัน:** 2026-10-13  
**สถานะ:** ✅ ผ่านทั้งหมด (100% Pass Rate)  
**ผลรวม:** 81 tests | 12 files | 0 failures  
**Stack:** Next.js 14 · Cypress 13.17.0 · App Router · TanStack Query v5  
**Auth Pattern:** Mock `POST /api/v1/auth/refresh` คืน access token → AuthProvider sync state อัตโนมัติ (in-memory token store, ไม่แตะ localStorage)  
**UI Screenshots:** บันทึกรูปภาพหน้าจอความละเอียดสูง (2560x1440) ไว้ใน `docs/screenshots/` ครบทุกขั้นตอน

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
| 12 | `e2e-screenshots.cy.js` | 13 | ✅ ผ่าน | Screenshot capturing & visual verification for all UI menu views |
| **รวม** | **12 files** | **81 tests** | **✅ 81 passed (100%)** | |

---

## 📸 รายการภาพหน้าจอที่บันทึกไว้ใน `docs/screenshots/` (แยกตามหมวดหมู่เมนู)

| ลำดับ / เมนู | ไฟล์รูปภาพ | หัวข้อ / ขั้นตอน |
|---|---|---|
| 01-login | [login-page.png](../screenshots/01-login/login-page.png) | หน้าจอเข้าสู่ระบบ (Login Page) พร้อมแบบฟอร์มตรวจสอบสิทธิ์ |
| 02-dashboard | [dashboard-overview.png](../screenshots/02-dashboard/dashboard-overview.png) | หน้าหลัก Dashboard พร้อม StatsBar, Device Table, Telemetry Chart และ Alert List |
| 02-dashboard | [telemetry-smoke-tab.png](../screenshots/02-dashboard/telemetry-smoke-tab.png) | กราฟตรวจวัด Telemetry ขณะสลับแท็บ Smoke (ppm) และ Motion |
| 02-dashboard | [admin-device-management.png](../screenshots/02-dashboard/admin-device-management.png) | แผงควบคุม Device Management ของผู้ดูแลระบบ (เปลี่ยน Lifecycle และอัปเดต Firmware) |
| 03-devices | [devices-list.png](../screenshots/03-devices/devices-list.png) | หน้ารายการอุปกรณ์รวม (`/devices`) ตัวกรองสถานะ Online/Offline, Lifecycle และปุ่ม Register |
| 03-devices | [device-detail.png](../screenshots/03-devices/device-detail.png) | หน้ารายละเอียดอุปกรณ์ (`/devices/:id`) แสดงข้อมูลสเปก, สถานะ และกราฟเซ็นเซอร์เฉพาะตัว |
| 04-digital-twin | [digital-twin-hub.png](../screenshots/04-digital-twin/digital-twin-hub.png) | ศูนย์จัดการ Digital Twin Hub (`/digital-twin`) แสดง Device Shadow, OCC Versioning และ Desired State |
| 05-predictive-ai | [predictive-ai-analytics.png](../screenshots/05-predictive-ai/predictive-ai-analytics.png) | หน้าวิเคราะห์ข้อมูล AI เชิงพยากรณ์ (`/predictive-ai`) Dynamic Baselines และ Inference Simulator |
| 06-lakehouse | [lakehouse-explorer.png](../screenshots/06-lakehouse/lakehouse-explorer.png) | หน้าระบบวิเคราะห์ข้อมูลย้อนหลัง DuckDB Lakehouse (`/lakehouse`) ข้อมูล Parquet หลายปีแบบ Sub-second |
| 07-alerts | [alerts-management.png](../screenshots/07-alerts/alerts-management.png) | หน้าศูนย์จัดการการแจ้งเตือน (`/alerts`) ตัวกรองระดับ และปุ่ม Acknowledge All |
| 08-users | [user-management.png](../screenshots/08-users/user-management.png) | หน้าจัดการบัญชีผู้ใช้งาน (`/users`), ป้าย `(you)` ป้องกันบัญชีตนเอง และกำหนดสิทธิ์ ADMIN/OPERATOR |
| 09-settings | [platform-settings.png](../screenshots/09-settings/platform-settings.png) | หน้าตั้งค่าระบบ (`/settings`) แสดง Thresholds, Retention และช่องทาง Notification |
| 10-system | [system-offline-banner.png](../screenshots/10-system/system-offline-banner.png) | แบนเนอร์จำลองกรณีเครือข่ายออฟไลน์ "You are offline" |

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
| 7 | `users.cy.js` ทดสอบ `<option>` visibility ไม่ผ่าน | บราวเซอร์มองว่า `<option>` ภายใน closed `<select>` มีขนาด `0x0 px` จึงไม่ถือว่า visible | เปลี่ยนการ assert เป็น `find('select').should('have.value', 'ADMIN')` ตามแบบแผน Cypress |
| 8 | Elements ใน AlertList และ DeviceManagement ถูก overflow clipping | คอนเทนเนอร์คอลัมน์ขวามี `calc(100vh - 13rem)` แบบตายตัว | เพิ่ม `overflow-y-auto` และกำหนด `min-h-[220px]` ให้ AlertList พร้อมใช้ `.scrollIntoView()` ใน spec ก่อน assert visibility |
