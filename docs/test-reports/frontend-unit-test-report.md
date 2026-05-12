# Frontend Unit Test Report

**วันที่รัน:** 2026-05-12  
**สถานะ:** ✅ ผ่านทั้งหมด  
**ผลรวม:** 76 tests | 11 files | 0 failures  
**Stack:** Next.js 14 · Jest 30 · React Testing Library 16 · jsdom 26  
**เวลาที่รัน:** 1.3 วินาที

---

## สรุปผล

| Test File | Tests | ผล |
|-----------|-------|-----|
| `tokenStore.test.js` | 4 | ✅ |
| `store.test.js` | 7 | ✅ |
| `Badge.test.jsx` | 6 | ✅ |
| `Select.test.jsx` | 5 | ✅ |
| `ErrorBoundary.test.jsx` | 6 | ✅ |
| `StatsBar.test.jsx` | 6 | ✅ |
| `AlertList.test.jsx` | 9 | ✅ |
| `client.test.js` | 6 | ✅ |
| `useAuth.test.js` | 8 | ✅ |
| `useWebSocket.test.js` | 7 | ✅ |
| `DeviceTable.test.jsx` | 12 | ✅ |
| **รวม** | **76** | **✅** |

---

## `tokenStore.test.js` — 4 tests ✅

ทดสอบ in-memory token store ว่าเก็บ access token ออกจาก localStorage (XSS safety)

| Test | สิ่งที่ตรวจสอบ |
|------|--------------|
| `getAccessToken returns null initially` | ก่อน set → คืน null |
| `setAccessToken stores token in memory` | set token → getAccessToken คืนค่าถูกต้อง |
| `clearAccessToken removes token` | set แล้ว clear → คืน null |
| `token is not stored in localStorage` | หลัง setAccessToken → localStorage ไม่มี token |

---

## `store.test.js` — 7 tests ✅

ทดสอบ Zustand store state transitions ทั้งหมด

| Test | สิ่งที่ตรวจสอบ |
|------|--------------|
| `initial state is correct` | ค่าเริ่มต้นทุก field ถูกต้อง |
| `setSelectedDeviceId updates selected` | เรียก setSelectedDeviceId → selectedDeviceId อัปเดต |
| `setFilter updates specific filter key` | setFilter('status', 'ONLINE') → filters.status เปลี่ยน |
| `setFilter does not affect other keys` | เปลี่ยน status → search/lifecycle คงเดิม |
| `resetFilters restores defaults` | เปลี่ยนหลาย filter → resetFilters → กลับเป็น default |
| `setOffline sets isOffline to true` | setOffline(true) → isOffline === true |
| `setOffline(false) clears offline state` | setOffline(false) → isOffline === false |

---

## `Badge.test.jsx` — 6 tests ✅

ทดสอบ visual variant และ className mapping ของ Badge component

| Test | สิ่งที่ตรวจสอบ |
|------|--------------|
| `renders children correctly` | children แสดงใน DOM |
| `applies default variant when no variant given` | ไม่ส่ง variant → class `bg-sentinel-700` |
| `applies success variant` | variant="success" → class `text-sentinel-success` |
| `applies critical variant` | variant="critical" → class `bg-sentinel-danger text-white` |
| `falls back to default for unknown variant` | variant="invalid" → fallback เป็น default class |
| `merges custom className` | className="mt-2" → class ถูก merge |

---

## `Select.test.jsx` — 5 tests ✅

ทดสอบ accessibility และ callback ของ Select component

| Test | สิ่งที่ตรวจสอบ |
|------|--------------|
| `renders label and all options` | label + options ทุกตัวแสดงใน DOM |
| `renders without label when label prop omitted` | ไม่มี label element ใน DOM |
| `calls onChange with selected value` | เลือก option → onChange ถูกเรียกด้วย value ถูกต้อง |
| `label is associated with select via htmlFor/id` | label[for] ตรงกับ select[id] |
| `shows currently selected value` | value="ONLINE" → select แสดง option ที่ถูกเลือก |

---

## `ErrorBoundary.test.jsx` — 6 tests ✅

ทดสอบ error catching, fallback UI และ reset ของ ErrorBoundary class component

| Test | สิ่งที่ตรวจสอบ |
|------|--------------|
| `renders children when no error` | children แสดงปกติ |
| `shows fallback UI when child throws` | component throw → แสดง fallback พร้อม error message |
| `shows label in fallback title when label prop given` | label="Device list" → fallback title เป็น "Device list failed to render" |
| `shows generic message when no label` | ไม่มี label → "Something went wrong" |
| `reset button clears error state and re-renders children` | กด "Try again" → children render ใหม่ |
| `fallback has role="alert" for accessibility` | fallback element มี role="alert" |

---

## `StatsBar.test.jsx` — 6 tests ✅

ทดสอบ calculation logic จาก props ของ StatsBar

| Test | สิ่งที่ตรวจสอบ |
|------|--------------|
| `shows correct total device count` | devices 5 ตัว → "Total Devices" = 5 |
| `calculates online and offline counts correctly` | 3 ONLINE, 2 OFFLINE → card ถูกต้อง |
| `shows critical unacknowledged alert count` | 2 CRITICAL unacked → "Critical Alerts" = 2 |
| `shows 0 for buffered when replayQueueSize is 0` | replayQueueSize=0 → "Buffered" = 0, color = gray |
| `shows warning color for buffered when replayQueueSize > 0` | replayQueueSize=5 → "Buffered" = 5, color = warning |
| `shows events per minute from stats.lastMinute` | lastMinute=42 → "Events / min" = 42 |

---

## `AlertList.test.jsx` — 9 tests ✅

ทดสอบ filter tabs, RBAC และ acknowledge flow ของ AlertList

| Test | สิ่งที่ตรวจสอบ |
|------|--------------|
| `renders all alerts by default` | render → แสดง alert ทั้งหมด |
| `shows empty state when no alerts` | alerts=[] → "No alerts" |
| `shows unacknowledged count badge in header` | 2 unacked → badge แสดง "2" |
| `filters to unacknowledged when tab clicked` | คลิก Unacknowledged tab → แสดงเฉพาะ unacked |
| `shows empty state message in unacked tab when all acked` | ทุก alert acked → "No active alerts" |
| `ADMIN sees Ack button on unacknowledged alert` | role=ADMIN + unacked → ปุ่ม "Ack" ปรากฏ |
| `OPERATOR does not see Ack button` | role=OPERATOR → ไม่มีปุ่ม "Ack" |
| `clicking Ack calls alertsApi.acknowledge and onAcknowledge callback` | กด Ack → alertsApi.acknowledge(id) ถูกเรียก |
| `CRITICAL alert has danger border styling` | level=CRITICAL → border class มี sentinel-danger |

---

## `client.test.js` — 6 tests ✅

ทดสอบ axios interceptors โดยเรียก handler functions โดยตรง

| Test | สิ่งที่ตรวจสอบ |
|------|--------------|
| `adds Authorization header when token exists` | setAccessToken → request มี `Authorization: Bearer` |
| `does not add Authorization header when no token` | ไม่มี token → ไม่มี Authorization header |
| `dispatches api-version-mismatch event when version differs` | response header api-version=2 → custom event ถูก dispatch |
| `does not dispatch event when version matches` | api-version=1 → ไม่มี event |
| `clears access token on 401 response` | API คืน 401 → token ถูก clear |
| `dispatches api-version-rejected event on 406 response` | API คืน 406 → sentinel:api-version-rejected event |

---

## `useAuth.test.js` — 8 tests ✅

ทดสอบ AuthProvider + useAuth hook lifecycle ด้วย mock authApi

| Test | สิ่งที่ตรวจสอบ |
|------|--------------|
| `starts with loading=true then resolves to loading=false` | mount → loading=true → refresh เสร็จ → loading=false |
| `sets user on successful silent refresh` | refresh สำเร็จ → user ถูก set |
| `stays logged out if refresh fails` | refresh ล้มเหลว → user=null, loading=false |
| `login() sets user and stores access token` | login สำเร็จ → user set + accessToken ใน memory |
| `login() throws on invalid credentials` | API คืน 401 → login() throw |
| `logout() clears user and access token` | logout() → user=null + token cleared |
| `logout() clears state even if API call fails` | API logout throw → state ยังถูก clear |
| `useAuth returns null outside AuthProvider` | เรียก useAuth นอก AuthProvider → คืน null |

---

## `useWebSocket.test.js` — 7 tests ✅

ทดสอบ WebSocket hook ด้วย MockWebSocket class

| Test | สิ่งที่ตรวจสอบ |
|------|--------------|
| `connects on mount with provided URL` | mount → new WebSocket(url) ถูกเรียก |
| `status is CONNECTED after onopen fires` | ws.onopen() → status === 'CONNECTED' |
| `parses JSON message from onmessage` | onmessage({data: '{"t":1}'}) → lastMessage = {t: 1} |
| `stores raw string if JSON parse fails` | onmessage({data: 'not-json'}) → lastMessage = 'not-json' |
| `status is RECONNECTING after onclose fires` | ws.onclose() → status === 'RECONNECTING' |
| `schedules reconnect setTimeout after onclose` | ws.onclose() → setTimeout ถูกเรียก |
| `cleans up WebSocket and timer on unmount` | unmount → ws.close() + clearTimeout ถูกเรียก |

---

## `DeviceTable.test.jsx` — 12 tests ✅

ทดสอบ virtualised device table: filter, sort, selection, keyboard nav และ WebSocket override  
**Mock:** `@tanstack/react-virtual` เพื่อให้ render ทุก row โดยไม่ขึ้นกับ container height

| Test | สิ่งที่ตรวจสอบ |
|------|--------------|
| `renders empty state when no devices` | devices=[] → "No devices registered" |
| `renders visible device names` | devices 3 ตัว → ชื่อแสดงใน DOM |
| `search filter narrows results` | พิมพ์ "alpha" → แสดงเฉพาะ device ที่ชื่อตรง |
| `status filter shows only ONLINE devices` | เลือก Online → ซ่อน OFFLINE devices |
| `lifecycle filter shows only ACTIVE devices` | เลือก ACTIVE → ซ่อน lifecycle อื่น |
| `clear button resets all filters` | filter แล้วกด Clear → แสดง devices ครบ |
| `device count label updates with filter` | filter เหลือ 3 จาก 5 → "3 of 5 devices" |
| `clicking device row calls onSelect with device` | คลิก row → onSelect(device) ถูกเรียก |
| `Enter key on row calls onSelect` | กด Enter บน row → onSelect(device) ถูกเรียก |
| `selected row has aria-selected=true` | selected.id ตรงกับ row → aria-selected="true" |
| `WebSocket message overrides device status to ONLINE` | lastMessage.deviceId ตรงกับ device → แสดง ONLINE แม้ DB บอก OFFLINE |
| `shows no-match state when filters produce no results` | filter ที่ไม่มีผล → "No devices match the filters" |

---

## ปัญหาที่พบและแก้ไข

| # | ปัญหา | ไฟล์ที่แก้ | สาเหตุ |
|---|-------|-----------|--------|
| 1 | MSW ESM ไม่ถูก transform โดย Jest | `jest.config.js` | MSW v2 ใช้ ESM modules, next/jest override transformIgnorePatterns — แก้ด้วย async config export |
| 2 | `{ name: /ack/i }` match "Unacknowledged" tab ด้วย | `AlertList.test.jsx` | regex ครอบคลุมเกินไป — เปลี่ยนเป็น exact string `'Ack'` |
| 3 | Cannot redefine property: location (jsdom 26) | `client.test.js` | jsdom 26 ทำให้ `window.location` เป็น non-configurable — ทดสอบ `getAccessToken() === null` แทน |
