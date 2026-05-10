# API Reference

Base URL: `http://localhost:8080` (local) or your deployed backend URL.

All protected endpoints require:

```http
Authorization: Bearer <jwt_token>
```

---

## Authentication

### POST /api/auth/login

Obtain a JWT token. No authentication required.

**Request**

```http
POST /api/auth/login
Content-Type: application/json
```

```json
{
  "username": "admin",
  "password": "admin123"
}
```

**Response 200**

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "role": "ADMIN",
  "username": "admin"
}
```

**Response 401** — Wrong credentials

```json
{
  "status": 401,
  "error": "Unauthorized"
}
```

**Token lifetime:** 24 hours (configurable via `JWT_EXPIRATION_MS`).

---

## Devices

### POST /api/devices

Register a new device. **Requires ADMIN role.**

**Request**

```http
POST /api/devices
Authorization: Bearer <token>
Content-Type: application/json
```

```json
{
  "name": "sensor-1",
  "description": "Line A temperature sensor",
  "location": "Factory Hall B"
}
```

**Response 201**

```json
{
  "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "name": "sensor-1",
  "status": "OFFLINE",
  "description": "Line A temperature sensor",
  "location": "Factory Hall B",
  "createdAt": "2024-06-01T08:00:00Z",
  "lastSeen": null
}
```

**Response 400** — Validation failed

```json
{
  "status": 400,
  "error": "Device name already exists: sensor-1"
}
```

**Response 403** — OPERATOR role attempting to create

---

### GET /api/devices

List all registered devices. Status is served from Redis cache (falls back to DB). **Requires ADMIN or OPERATOR role.**

**Request**

```http
GET /api/devices
Authorization: Bearer <token>
```

**Response 200**

```json
[
  {
    "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "name": "sensor-1",
    "status": "ONLINE",
    "description": "Line A temperature sensor",
    "location": "Factory Hall B",
    "createdAt": "2024-06-01T08:00:00Z",
    "lastSeen": "2024-06-01T09:45:12Z"
  }
]
```

---

### GET /api/devices/{id}

Fetch a single device by UUID.

**Request**

```http
GET /api/devices/3fa85f64-5717-4562-b3fc-2c963f66afa6
Authorization: Bearer <token>
```

**Response 200** — Same structure as list item above.

**Response 404**

```json
{
  "status": 404,
  "error": "Device not found: 3fa85f64-5717-4562-b3fc-2c963f66afa6"
}
```

---

## Telemetry

### GET /api/telemetry/{deviceId}/latest

Returns the most recent telemetry rows for a device, ordered by timestamp descending. Default limit: 50, max: 200.

**Request**

```http
GET /api/telemetry/3fa85f64-5717-4562-b3fc-2c963f66afa6/latest?limit=10
Authorization: Bearer <token>
```

**Response 200**

```json
[
  {
    "id": "uuid",
    "deviceId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "temperature": 72.4,
    "humidity": 58.2,
    "motion": false,
    "smokePpm": 12.5,
    "timestamp": "2024-06-01T09:45:12Z"
  }
]
```

---

### GET /api/telemetry/{deviceId}/cache

Returns the latest telemetry values from Redis (sub-millisecond read). Useful for polling current state without hitting the DB.

**Response 200**

```json
{
  "temperature": "72.4",
  "humidity": "58.2",
  "motion": "false",
  "smokePpm": "12.5",
  "ts": "1717228800000"
}
```

> Note: all values are strings since Redis hash fields are stored as strings.

---

### GET /api/telemetry/{deviceId}/range

Returns telemetry within a time window. Use ISO 8601 timestamps.

**Request**

```http
GET /api/telemetry/{deviceId}/range?from=2024-06-01T00:00:00Z&to=2024-06-01T23:59:59Z
Authorization: Bearer <token>
```

**Response 200** — Array of telemetry objects (same schema as `/latest`).

---

### GET /api/telemetry/stats

Returns the count of telemetry events received in the last 60 seconds.

**Response 200**

```json
{
  "lastMinute": 42
}
```

---

## Alerts

### GET /api/alerts

Returns the 50 most recent alerts (acknowledged and unacknowledged).

**Response 200**

```json
[
  {
    "id": "uuid",
    "deviceId": "uuid",
    "level": "CRITICAL",
    "message": "[sensor-1] CRITICAL: temperature 83.2°C exceeds 80.0°C threshold",
    "acknowledged": false,
    "createdAt": "2024-06-01T09:45:12Z"
  }
]
```

**Alert levels:**

| Level | Trigger |
|-------|---------|
| `CRITICAL` | temperature > 80 °C or smoke > 200 ppm |
| `WARNING` | humidity > 90 % or motion + temperature > 70 °C |

---

### GET /api/alerts/unacknowledged

Returns only unacknowledged alerts, ordered newest first.

---

### PUT /api/alerts/{id}/acknowledge

Mark an alert as acknowledged. **Requires ADMIN role.**

**Request**

```http
PUT /api/alerts/3fa85f64-5717-4562-b3fc-2c963f66afa6/acknowledge
Authorization: Bearer <admin_token>
```

**Response 204** — No content.

**Response 403** — Non-ADMIN token.

---

## WebSocket

### WS /ws/telemetry

Real-time telemetry stream. No authentication required at the transport layer (add an auth handshake interceptor for production hardening).

**Connect**

```js
const ws = new WebSocket('ws://localhost:8080/ws/telemetry')
```

**Incoming message** — Sent on every MQTT event processed:

```json
{
  "deviceId": "sensor-1",
  "temperature": 72.4,
  "humidity": 58.2,
  "motion": false,
  "smokePpm": 12.5,
  "timestamp": 1717200000000
}
```

The backend broadcasts to **all** connected sessions (`CopyOnWriteArraySet`). Filter by `deviceId` on the client side.

---

## Observability

### GET /actuator/health

No authentication required.

```json
{
  "status": "UP",
  "components": {
    "db": { "status": "UP" },
    "redis": { "status": "UP" },
    "diskSpace": { "status": "UP" }
  }
}
```

### GET /actuator/prometheus

Prometheus text format. No authentication required (restrict in production via firewall/ingress).

---

## Error Codes

| HTTP Status | Meaning |
|-------------|---------|
| 200 | OK |
| 201 | Created |
| 204 | No Content (acknowledge) |
| 400 | Bad request / validation failure |
| 401 | Missing or expired JWT |
| 403 | Insufficient role (e.g. OPERATOR calling POST /devices) |
| 404 | Resource not found |
| 500 | Internal server error |

---

## Role Matrix

| Endpoint | ADMIN | OPERATOR |
|----------|-------|----------|
| POST /api/auth/login | ✅ | ✅ |
| POST /api/devices | ✅ | ❌ |
| GET /api/devices | ✅ | ✅ |
| GET /api/devices/{id} | ✅ | ✅ |
| GET /api/telemetry/* | ✅ | ✅ |
| GET /api/alerts | ✅ | ✅ |
| PUT /api/alerts/{id}/acknowledge | ✅ | ❌ |
