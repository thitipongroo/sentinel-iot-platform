# Frontend Unit Test Plan — Sentinel IoT Dashboard

**Stack:** Next.js 14 · React 18 · Zustand · React Query · Axios  
**สถานะปัจจุบัน:** ❌ ไม่มี unit test เลย (มีแค่ Cypress E2E 1 ไฟล์)  
**เป้าหมาย:** ~65 test cases ครอบคลุม components, hooks, lib, และ API client

---

## Framework ที่จะติดตั้ง

| Package | วัตถุประสงค์ |
|---------|-------------|
| `jest` | test runner |
| `jest-environment-jsdom` | จำลอง browser DOM ใน Node |
| `@testing-library/react` | render React components + query DOM |
| `@testing-library/user-event` | จำลอง user interaction (click, type, keyboard) |
| `@testing-library/jest-dom` | DOM matchers เพิ่มเติม (`toBeInTheDocument`, `toHaveTextContent` ฯลฯ) |
| `msw` (Mock Service Worker) | mock HTTP request สำหรับ API client tests |

```bash
npm install -D jest jest-environment-jsdom \
  @testing-library/react @testing-library/user-event \
  @testing-library/jest-dom msw
```

---

## โครงสร้างไฟล์ Test

```
frontend/src/
├── components/
│   ├── __tests__/
│   │   ├── AlertList.test.jsx
│   │   ├── StatsBar.test.jsx
│   │   └── DeviceTable.test.jsx
│   └── ui/
│       └── __tests__/
│           ├── Badge.test.jsx
│           ├── Select.test.jsx
│           └── ErrorBoundary.test.jsx
├── hooks/
│   └── __tests__/
│       ├── useAuth.test.js
│       └── useWebSocket.test.js
├── lib/
│   └── __tests__/
│       ├── store.test.js
│       └── tokenStore.test.js
└── api/
    └── __tests__/
        └── client.test.js
```

---

## รายละเอียด Test แต่ละไฟล์

---

### 1. `Badge.test.jsx` — 6 tests

Component ที่ง่ายที่สุด ทดสอบ visual variant และ className mapping

| Test | สิ่งที่ตรวจสอบ |
|------|--------------|
| renders children correctly | `<Badge>Active</Badge>` → แสดง text "Active" |
| applies default variant when no variant given | ไม่ส่ง `variant` → ใช้ class `bg-sentinel-700` |
| applies success variant | `variant="success"` → มี class `text-sentinel-success` |
| applies danger/critical variants | `variant="critical"` → มี class `bg-sentinel-danger text-white` |
| applies unknown variant → fallback to default | `variant="invalid"` → fallback เป็น default class |
| merges custom className | `className="mt-2"` → class ถูก merge เข้าไป |

---

### 2. `Select.test.jsx` — 5 tests

UI primitive สำหรับ dropdown — ทดสอบ accessibility และ callback

| Test | สิ่งที่ตรวจสอบ |
|------|--------------|
| renders label and options | label + options ทุกตัวแสดงใน DOM |
| renders without label when label prop omitted | ไม่มี `<label>` element ใน DOM |
| calls onChange with selected value | เลือก option → `onChange` ถูกเรียกด้วย value ถูกต้อง |
| label is associated with select via htmlFor/id | `label[for]` ตรงกับ `select[id]` (accessibility) |
| shows currently selected value | `value="ONLINE"` → select แสดง option ที่เลือกอยู่ |

---

### 3. `ErrorBoundary.test.jsx` — 5 tests

Class component ที่ catch render error — ทดสอบ error states และ reset

| Test | สิ่งที่ตรวจสอบ |
|------|--------------|
| renders children when no error | children แสดงปกติ |
| shows fallback UI when child throws | component throw error → แสดง fallback พร้อม error message |
| shows label in fallback when label prop given | `label="Device list"` → fallback title เป็น "Device list failed to render" |
| reset button clears error state | กด "Try again" → children render ใหม่ (error ถูก clear) |
| fallback has role="alert" for accessibility | fallback element มี `role="alert"` |

---

### 4. `AlertList.test.jsx` — 9 tests

Component ที่มี state (filter tab) + conditional rendering + RBAC

| Test | สิ่งที่ตรวจสอบ |
|------|--------------|
| renders all alerts by default | แสดง alert ทั้งหมดเมื่อเปิดครั้งแรก |
| shows empty state when no alerts | ไม่มี alert → แสดง "No alerts" |
| shows unacknowledged count badge in header | มี 2 unacked → badge แสดง "2" |
| filters to unacknowledged when tab clicked | คลิก "Unacknowledged" tab → แสดงเฉพาะ unacked |
| shows empty state message in unacked tab | unacked tab ว่าง → แสดง "No active alerts" |
| ADMIN sees Ack button on unacked alert | `userRole="ADMIN"` + unacked alert → แสดงปุ่ม "Ack" |
| OPERATOR does not see Ack button | `userRole="OPERATOR"` → ไม่มีปุ่ม "Ack" |
| clicking Ack calls API and triggers onAcknowledge | กด Ack → `alertsApi.acknowledge(id)` ถูกเรียก + `onAcknowledge` callback ถูกเรียก |
| CRITICAL alert has danger styling | alert level CRITICAL → มี class danger |

---

### 5. `StatsBar.test.jsx` — 6 tests

Pure presentational component — ทดสอบ calculation logic จาก props

| Test | สิ่งที่ตรวจสอบ |
|------|--------------|
| shows correct total device count | `devices` 5 ตัว → "Total Devices" = 5 |
| calculates online count correctly | 3 ใน 5 ONLINE → "Online" = 3, "Offline" = 2 |
| shows critical unacknowledged alert count | 2 CRITICAL unacked + 1 WARNING → "Critical Alerts" = 2 |
| shows 0 for buffered when replayQueueSize is 0 | `stats.replayQueueSize=0` → "Buffered" = 0 ด้วย gray color |
| shows warning color for buffered > 0 | `stats.replayQueueSize=5` → "Buffered" = 5 ด้วย warning color |
| shows events per minute from stats.lastMinute | `stats.lastMinute=42` → "Events / min" = 42 |

---

### 6. `DeviceTable.test.jsx` — 12 tests

Component ซับซ้อนที่สุด — virtualised list, filter/sort, keyboard nav, WebSocket

| Test | สิ่งที่ตรวจสอบ |
|------|--------------|
| renders empty state when no devices | ไม่มี device → แสดง "No devices registered" |
| renders visible devices | devices 3 ตัว → แสดงชื่อใน DOM |
| search filter narrows results | พิมพ์ "sensor" → แสดงเฉพาะ device ที่ name/location มีคำนั้น |
| status filter shows only ONLINE devices | เลือก "Online" → ซ่อน OFFLINE devices |
| lifecycle filter works | เลือก "ACTIVE" → ซ่อน device lifecycle อื่น |
| clear button resets all filters | กด Clear → filters กลับสู่ค่าเริ่มต้น |
| device count label updates with filter | filter เหลือ 2 จาก 5 → แสดง "2 of 5 devices" |
| clicking device row calls onSelect | คลิก row → `onSelect(device)` ถูกเรียก |
| Enter key on row calls onSelect | กด Enter บน row → `onSelect(device)` ถูกเรียก |
| selected row has aria-selected=true | `selected.id` ตรงกับ row → `aria-selected="true"` |
| WebSocket message overrides device status | `lastMessage.deviceId` ตรงกับ device → แสดง ONLINE แม้ DB บอก OFFLINE |
| shows no-match state when filter has no results | filter ที่ไม่มีผล → "No devices match the filters" |

---

### 7. `useAuth.test.js` — 8 tests

Custom hook + Context Provider — ทดสอบ auth state lifecycle

| Test | สิ่งที่ตรวจสอบ |
|------|--------------|
| starts with loading=true then resolves | mount → `loading=true` → refresh call complete → `loading=false` |
| sets user on successful silent refresh | `authApi.refresh()` สำเร็จ → `user` ถูก set |
| stays logged out if refresh fails | `authApi.refresh()` ล้มเหลว → `user=null`, `loading=false` |
| login() sets user and calls setAccessToken | เรียก `login(u, p)` → user ถูก set + `setAccessToken` ถูกเรียก |
| login() throws on invalid credentials | API คืน 401 → `login()` throw error |
| logout() clears user and access token | เรียก `logout()` → `user=null` + `clearAccessToken` ถูกเรียก |
| logout() still clears state even if API fails | API logout throw error → state ยังถูก clear |
| useAuth returns null outside provider | เรียก `useAuth()` นอก `AuthProvider` → คืน null |

---

### 8. `useWebSocket.test.js` — 7 tests

Custom hook ที่มี reconnect logic — ทดสอบด้วย mock WebSocket

| Test | สิ่งที่ตรวจสอบ |
|------|--------------|
| connects on mount with provided URL | mount → `new WebSocket(url)` ถูกเรียก |
| status is CONNECTED after onopen | `ws.onopen()` fires → `status === 'CONNECTED'` |
| parses JSON message from onmessage | `ws.onmessage({data: '{"t":1}'})` → `lastMessage` = `{t: 1}` |
| stores raw string if JSON parse fails | `ws.onmessage({data: 'not-json'})` → `lastMessage` = `'not-json'` |
| status is RECONNECTING after onclose | `ws.onclose()` fires → `status === 'RECONNECTING'` |
| schedules reconnect after close | `ws.onclose()` → `setTimeout` ถูกเรียก |
| cleans up WebSocket and timer on unmount | unmount → `ws.close()` ถูกเรียก + timer ถูก clear |

---

### 9. `store.test.js` — 7 tests

Zustand store — ทดสอบ state transitions ทั้งหมด

| Test | สิ่งที่ตรวจสอบ |
|------|--------------|
| initial state is correct | ค่าเริ่มต้นทุก field ถูกต้อง |
| setSelectedDeviceId updates selected | เรียก `setSelectedDeviceId('abc')` → `selectedDeviceId === 'abc'` |
| setFilter updates specific filter key | `setFilter('status', 'ONLINE')` → `filters.status === 'ONLINE'` |
| setFilter does not affect other keys | เปลี่ยน `status` → `search` ยังคงค่าเดิม |
| resetFilters restores defaults | เปลี่ยน filter หลายตัว → `resetFilters()` → กลับเป็น default ทั้งหมด |
| setOffline sets isOffline to true | `setOffline(true)` → `isOffline === true` |
| setOffline(false) clears offline state | `setOffline(false)` → `isOffline === false` |

---

### 10. `tokenStore.test.js` — 4 tests

In-memory token store — ทดสอบ isolation และ security boundary

| Test | สิ่งที่ตรวจสอบ |
|------|--------------|
| getAccessToken returns null initially | ก่อน set → คืน `null` |
| setAccessToken stores token in memory | `setAccessToken('abc')` → `getAccessToken()` คืน `'abc'` |
| clearAccessToken removes token | set แล้ว clear → `getAccessToken()` คืน `null` |
| token is not in localStorage (XSS safety) | หลัง `setAccessToken` → `localStorage` ไม่มี token |

---

### 11. `client.test.js` — 6 tests

Axios instance + interceptors — ทดสอบ request/response handling

| Test | สิ่งที่ตรวจสอบ |
|------|--------------|
| adds Authorization header when token exists | `setAccessToken('t')` → request มี `Authorization: Bearer t` |
| does not add Authorization when no token | token ว่าง → ไม่มี Authorization header |
| redirects to /login on 401 response | API คืน 401 → `window.location.href === '/login'` + token ถูก clear |
| dispatches api-version-mismatch event | response header มี `api-version: 2` → browser event ถูก dispatch |
| does not dispatch event when version matches | `api-version: 1` (ตรงกัน) → ไม่มี event |
| dispatches api-version-rejected on 406 | API คืน 406 → `sentinel:api-version-rejected` event ถูก dispatch |

---

## สรุปภาพรวม

| กลุ่ม | Test Files | Test Cases |
|-------|-----------|------------|
| UI Components (Badge, Select, ErrorBoundary) | 3 | 16 |
| Feature Components (AlertList, StatsBar, DeviceTable) | 3 | 27 |
| Hooks (useAuth, useWebSocket) | 2 | 15 |
| Lib (store, tokenStore) | 2 | 11 |
| API Client | 1 | 6 |
| **รวม** | **11 files** | **75 tests** |

---

## ลำดับการ Implement (แนะนำ)

```
Priority 1 — ง่าย + ไม่มี dependencies
  tokenStore → store → Badge → Select

Priority 2 — มี dependencies เล็กน้อย
  ErrorBoundary → StatsBar → AlertList

Priority 3 — ต้องการ mock
  client → useAuth → useWebSocket

Priority 4 — ซับซ้อนสุด
  DeviceTable (ต้องการ store + virtualizer + mock)
```

---

## Jest Configuration

ต้องสร้างเพิ่ม:

- `jest.config.js` — กำหนด transform สำหรับ JSX, path alias `@/`
- `jest.setup.js` — import `@testing-library/jest-dom` และ global mock สำหรับ `next/navigation`
- reset Zustand store state ระหว่างแต่ละ test ด้วย `beforeEach`

---

## วิธีรัน

```bash
cd frontend
npm run lint      # ESLint
npm run build     # Next.js production build (type-check included)
```
