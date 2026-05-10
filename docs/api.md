# API Reference

Base URL: `http://localhost:8080` (local) or your deployed backend URL.

All protected endpoints require:

```http
Authorization: Bearer <access_token>
```

---

## Authentication

### POST /api/auth/login

Obtain an access token and refresh token. No authentication required.

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
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000",
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

**Token lifetimes:**

- `accessToken`: 15 minutes (configurable via `JWT_EXPIRATION_MS`)
- `refreshToken`: 7 days (configurable via `JWT_REFRESH_EXPIRATION_MS`); **rotated** on every use — each `/auth/refresh` call invalidates the old token and issues a new one

---

### POST /api/auth/refresh

Exchange a refresh token for a new access token + rotated refresh token.

**Request**

```http
POST /api/auth/refresh
Content-Type: application/json
```

```json
{
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000"
}
```

**Response 200**

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "a3bb189e-8bf9-3888-9912-ace4e6543002",
  "role": "ADMIN",
  "username": "admin"
}
```

**Response 401** — Expired or unknown refresh token

---

### POST /api/auth/logout

Revoke all refresh tokens for the authenticated user.

```http
POST /api/auth/logout
Authorization: Bearer <access_token>
```

**Response 204** — No content.

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
  "lifecycleStatus": "PROVISIONED",
  "firmwareVersion": null,
  "firmwareUpdatedAt": null,
  "description": "Line A temperature sensor",
  "location": "Factory Hall B",
  "createdAt": "2024-06-01T08:00:00Z",
  "lastSeen": null
}
```

**Response 400** — Name already exists

**Response 403** — OPERATOR role attempting to create

---

### GET /api/devices

List all registered devices. **Requires ADMIN or OPERATOR role.**

**Response 200** — Array; each item has the same shape as the POST 201 response above.

---

### GET /api/devices/{id}

Fetch a single device by UUID. Returns 404 if not found.

---

### PATCH /api/devices/{id}/lifecycle

Transition a device's lifecycle status. **Requires ADMIN role.**

**Request**

```http
PATCH /api/devices/3fa85f64-5717-4562-b3fc-2c963f66afa6/lifecycle
Authorization: Bearer <admin_token>
Content-Type: application/json
```

```json
{
  "lifecycleStatus": "ACTIVE"
}
```

Valid states: `PROVISIONED → ACTIVE → INACTIVE → DECOMMISSIONED`.

`DECOMMISSIONED` is terminal — further transitions return 409. Transitioning to `INACTIVE` or `DECOMMISSIONED` also forces `status = OFFLINE`.

**Response 200** — Updated device object.

**Response 409** — Attempt to transition from `DECOMMISSIONED`.

**Response 403** — Non-ADMIN token.

---

### PATCH /api/devices/{id}/firmware

Update a device's recorded firmware version. **Requires ADMIN role.**

**Request**

```http
PATCH /api/devices/3fa85f64-5717-4562-b3fc-2c963f66afa6/firmware
Authorization: Bearer <admin_token>
Content-Type: application/json
```

```json
{
  "firmwareVersion": "1.2.3"
}
```

Version must match semver pattern (`\d+\.\d+\.\d+(-[\w.]+)?`). Rejected for `DECOMMISSIONED` devices (400).

**Response 200** — Updated device object with `firmwareVersion` and `firmwareUpdatedAt` set.

---

## Telemetry

### GET /api/telemetry/{deviceId}/latest

Returns the most recent telemetry rows from PostgreSQL, ordered by timestamp descending. Default limit: 50, max: 200.

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

Returns the latest telemetry values from Redis (sub-millisecond). All values are strings (Redis hash field type).

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

---

### GET /api/telemetry/{deviceId}/range

Returns raw telemetry within a time window. Use ISO 8601 timestamps.

```http
GET /api/telemetry/{deviceId}/range?from=2024-06-01T00:00:00Z&to=2024-06-01T23:59:59Z
Authorization: Bearer <token>
```

**Response 200** — Array of telemetry objects (same schema as `/latest`).

---

### GET /api/telemetry/{deviceId}/hourly

Returns hourly aggregated telemetry for a time window. Aggregates persist beyond the raw telemetry retention window and are used by the dashboard's 24h and 7d chart modes.

```http
GET /api/telemetry/{deviceId}/hourly?from=2024-06-01T00:00:00Z&to=2024-06-07T23:59:59Z
Authorization: Bearer <token>
```

**Response 200**

```json
[
  {
    "id": "uuid",
    "deviceId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "hourBucket": "2024-06-01T14:00:00Z",
    "tempAvg": 71.4,
    "tempMin": 65.2,
    "tempMax": 88.1,
    "humAvg": 58.0,
    "humMin": 45.0,
    "humMax": 72.0,
    "smokeAvg": 23.5,
    "smokeMax": 310.0,
    "motionCount": 7,
    "sampleCount": 720
  }
]
```

---

### GET /api/telemetry/stats

Returns event throughput and replay queue depth.

**Response 200**

```json
{
  "lastMinute": 42,
  "replayQueueSize": 0
}
```

`replayQueueSize` is the current depth of the Redis replay queue (`sentinel:replay:queue`). A non-zero value indicates the circuit breaker has tripped and telemetry is being buffered.

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
| --- | --- |
| `CRITICAL` | temperature > 80 °C or smoke > 200 ppm |
| `WARNING` | humidity > 90 % or (motion true and temperature > 70 °C) |

---

### GET /api/alerts/unacknowledged

Returns only unacknowledged alerts, ordered newest first.

---

### PUT /api/alerts/{id}/acknowledge

Mark an alert as acknowledged. **Requires ADMIN role.**

**Response 204** — No content.

**Response 403** — Non-ADMIN token.

---

## WebSocket

### WS /ws/telemetry

Real-time telemetry stream. The connection is not authenticated at the transport layer — add a `HandshakeInterceptor` to validate a token query parameter for production hardening.

**Connect**

```js
const ws = new WebSocket('ws://localhost:8080/ws/telemetry')
```

**Incoming message** — sent on every successfully ingested MQTT event:

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

The backend broadcasts to **all** connected sessions. Filter by `deviceId` on the client side.

---

## Observability Endpoints

### GET /actuator/health

No authentication required.

```json
{
  "status": "UP",
  "components": {
    "db":        { "status": "UP" },
    "redis":     { "status": "UP" },
    "diskSpace": { "status": "UP" }
  }
}
```

### GET /actuator/prometheus

Prometheus text format. No authentication required (restrict in production via firewall/ingress).

---

## Request Correlation

Every response includes an `X-Request-ID` header echoing the request ID. Send your own to trace distributed calls:

```http
GET /api/devices
X-Request-ID: my-frontend-correlation-id
```

The value is also injected into every backend log line as `requestId` in the MDC.

---

## Error Codes

| HTTP Status | Meaning |
| --- | --- |
| 200 | OK |
| 201 | Created |
| 204 | No Content |
| 400 | Bad request / validation failure |
| 401 | Missing, expired, or revoked JWT |
| 403 | Insufficient role |
| 404 | Resource not found |
| 409 | Conflict (e.g. DECOMMISSIONED lifecycle transition) |
| 429 | Rate limit exceeded (Bucket4j — 100 req/min per IP) |
| 500 | Internal server error |

---

## Role Matrix

| Endpoint | ADMIN | OPERATOR |
| --- | --- | --- |
| POST /api/auth/login | ✅ | ✅ |
| POST /api/auth/refresh | ✅ | ✅ |
| POST /api/auth/logout | ✅ | ✅ |
| POST /api/devices | ✅ | ❌ |
| GET /api/devices | ✅ | ✅ |
| GET /api/devices/{id} | ✅ | ✅ |
| PATCH /api/devices/{id}/lifecycle | ✅ | ❌ |
| PATCH /api/devices/{id}/firmware | ✅ | ❌ |
| GET /api/telemetry/* | ✅ | ✅ |
| GET /api/alerts | ✅ | ✅ |
| PUT /api/alerts/{id}/acknowledge | ✅ | ❌ |
