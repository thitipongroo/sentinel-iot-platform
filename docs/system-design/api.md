# API Reference

Base URL: `http://localhost:8080` (local) or your deployed backend URL.

**Versioning:** All endpoints are under `/api/v1/`. Every response includes an `API-Version: 1` header. Unversioned `/api/*` requests receive additional `Deprecation: true`, `Sunset`, and `Link` headers pointing to the versioned equivalent.

All protected endpoints require:

```http
Authorization: Bearer <access_token>
```

---

## Authentication

### POST /api/v1/auth/login

Obtain an access token and refresh token. No authentication required.

#### Request

```http
POST /api/v1/auth/login
Content-Type: application/json
```

```json
{
  "username": "admin",
  "password": "admin123"
}
```

#### Response 200

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": null,
  "role": "ADMIN",
  "username": "admin"
}
```

The refresh token is **not** returned in the JSON body. It is delivered as an `HttpOnly; Secure; SameSite=Strict` cookie:

```http
Set-Cookie: refreshToken=<token>; HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth; Max-Age=604800
```

Store the `accessToken` in memory (e.g. a module-level JS variable). Never write it to `localStorage` or `sessionStorage`.

**Response 401** — Wrong credentials

**Token lifetimes:**

- `accessToken`: 15 minutes (configurable via `JWT_EXPIRATION_MS`); stored in JS memory, not localStorage
- `refreshToken`: 7 days (configurable via `JWT_REFRESH_EXPIRATION_MS`); **rotated** on every use; SHA-256 hash stored in DB; delivered as HttpOnly cookie

---

### POST /api/v1/auth/refresh

Exchange a refresh token for a new access token. The refresh token is read from the `HttpOnly` cookie — **no request body required**.

```http
POST /api/v1/auth/refresh
Cookie: refreshToken=<token>
```

#### Response 200

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": null,
  "role": "ADMIN",
  "username": "admin"
}
```

The rotated refresh token is set as a new `HttpOnly` cookie (same attributes as login). The `refreshToken` field in the JSON body is always `null`.

**Response 401** — Expired, unknown, or already-rotated refresh token (cookie absent or invalid)

---

### POST /api/v1/auth/logout

Revoke all refresh tokens for the authenticated user and clear the refresh token cookie.

```http
POST /api/v1/auth/logout
Authorization: Bearer <access_token>
```

**Response 204** — No content. The `Set-Cookie` response header expires the `refreshToken` cookie immediately (`Max-Age=0`).

---

## Devices

### POST /api/v1/devices

Register a new device. **Requires ADMIN role.**

```http
POST /api/v1/devices
Authorization: Bearer <token>
Content-Type: application/json
```

```json
{
  "name": "sensor-1",
  "location": "Factory Hall B",
  "firmwareVersion": "2.0.0"
}
```

> `location` and `firmwareVersion` are optional. There is no `description` field.

#### Response 201

```json
{
  "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "name": "sensor-1",
  "status": "OFFLINE",
  "lifecycleStatus": "PROVISIONED",
  "location": "Factory Hall B",
  "firmwareVersion": "2.0.0",
  "lastSeen": null,
  "capabilities": null
}
```

**Response 400** — Name already exists  
**Response 403** — OPERATOR role attempting to create

---

### GET /api/v1/devices

List all registered devices with live status from Redis. **Requires ADMIN or OPERATOR role.**

**Response 200** — Array; each item has the same shape as the POST 201 response above.

---

### GET /api/v1/devices/{id}

Fetch a single device by UUID. Returns 404 if not found.

---

### PATCH /api/v1/devices/{id}/lifecycle

Transition a device's lifecycle status. **Requires ADMIN role.**

```http
PATCH /api/v1/devices/3fa85f64-5717-4562-b3fc-2c963f66afa6/lifecycle
Authorization: Bearer <admin_token>
Content-Type: application/json
```

```json
{ "lifecycleStatus": "ACTIVE" }
```

Valid states: `PROVISIONED → ACTIVE → INACTIVE → DECOMMISSIONED`.

`DECOMMISSIONED` is terminal — further transitions return **409**. Transitioning to `INACTIVE` or `DECOMMISSIONED` also forces `status = OFFLINE`.

**Response 200** — Updated device object.

---

### PATCH /api/v1/devices/{id}/firmware

Update a device's recorded firmware version. **Requires ADMIN role.**

```http
PATCH /api/v1/devices/3fa85f64-5717-4562-b3fc-2c963f66afa6/firmware
Authorization: Bearer <admin_token>
Content-Type: application/json
```

```json
{ "firmwareVersion": "2.1.0" }
```

Version must match semver (`\d+\.\d+\.\d+(-[\w.]+)?`). Rejected for `DECOMMISSIONED` devices (400).

**Response 200** — Updated device object.

---

### GET /api/v1/devices/{id}/capabilities

Retrieve the sensor capability map for a device. **Requires ADMIN or OPERATOR role.**

When `capabilities` is null, the alert engine falls back to global environment-variable thresholds.

#### Response 200

```json
{
  "TEMPERATURE": {
    "unit": "°C",
    "minOperational": -40.0,
    "maxOperational": 200.0,
    "warnThreshold": 75.0,
    "critThreshold": 85.0,
    "thresholdDirection": "ABOVE",
    "enabled": true,
    "decimalPlaces": 1
  },
  "HUMIDITY": {
    "unit": "%RH",
    "warnThreshold": 85.0,
    "critThreshold": 95.0,
    "thresholdDirection": "ABOVE",
    "enabled": true,
    "decimalPlaces": 0
  }
}
```

Keys are `SensorType` names (e.g. `TEMPERATURE`, `HUMIDITY`, `SMOKE_PPM`, `CO2_PPM`, `MOTION`).

---

### PUT /api/v1/devices/{id}/capabilities

Replace the full sensor capability map. **Requires ADMIN role.**  
Send an empty object `{}` to revert to global thresholds.

**Request body** — same shape as the GET response above.

**Response 200** — Updated device object (with new `capabilities` embedded).

**Response 400** — Device is `DECOMMISSIONED`.

---

### POST /api/v1/devices/{id}/enrollment-token

Generate a one-time enrollment token for a device. **Requires ADMIN role.**

The raw token is returned exactly once — store it securely and deliver it to the physical device via an out-of-band channel (e.g. QR code, provisioning portal, serial console). The database stores only the SHA-256 hash.

```http
POST /api/v1/devices/3fa85f64-5717-4562-b3fc-2c963f66afa6/enrollment-token
Authorization: Bearer <admin_token>
```

#### Response 200

```json
{
  "tokenId": "a1b2c3d4-...",
  "token": "gX7kPq2mNvR...",
  "deviceId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "expiresAt": "2025-06-02T09:00:00Z"
}
```

Token TTL is configurable via `enrollment.token.ttl-hours` (default: 24 hours).

**Response 403** — Non-ADMIN token.  
**Response 400** — Device not found, or device is `DECOMMISSIONED`.

---

### POST /api/v1/devices/enroll

Bootstrap a device using a one-time enrollment token. **No authentication required** — the token itself is the credential.

On success, the device transitions to `ACTIVE` and receives its per-device MQTT credentials.

```http
POST /api/v1/devices/enroll
Content-Type: application/json
```

```json
{
  "deviceId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "token": "gX7kPq2mNvR...",
  "publicKey": null
}
```

> `publicKey` is optional — include the device's public key if mTLS / key-based auth is desired.

#### Response 200

```json
{
  "mqttUsername": "device-3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "mqttPassword": "s3cur3pw..."
}
```

**Response 400** — Invalid token, token/device mismatch.  
**Response 409** — Token already used or expired.

---

## Telemetry

### GET /api/v1/telemetry/{deviceId}/latest

Returns the most recent telemetry rows from PostgreSQL, ordered by timestamp descending. Default limit: 50, max: 200.

```http
GET /api/v1/telemetry/3fa85f64-5717-4562-b3fc-2c963f66afa6/latest?limit=10
Authorization: Bearer <token>
```

**Response 200** — Array of telemetry objects.

**v1 payload** (`schemaVersion=1` — fixed scalar fields):

```json
[
  {
    "id": "uuid",
    "deviceId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "schemaVersion": 1,
    "timestamp": "2025-06-01T09:45:12Z",
    "temperature": 72.4,
    "humidity": 58.2,
    "motion": false,
    "smokePpm": 12.5,
    "readings": {
      "TEMPERATURE": { "value": 72.4, "unit": "°C",   "quality": "GOOD" },
      "HUMIDITY":    { "value": 58.2, "unit": "%RH",  "quality": "GOOD" },
      "SMOKE_PPM":   { "value": 12.5, "unit": "ppm",  "quality": "GOOD" },
      "MOTION":      { "value": 0.0,  "unit": "bool", "quality": "GOOD" }
    }
  }
]
```

> For v1 payloads, `readings` is synthesised from scalar fields by the ingest pipeline — both representations are present.

**v2 payload** (`schemaVersion=2` — dynamic readings + edge metadata):

```json
[
  {
    "id": "uuid",
    "deviceId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "schemaVersion": 2,
    "timestamp": "2025-06-01T09:45:12Z",
    "temperature": null,
    "humidity": null,
    "motion": null,
    "smokePpm": null,
    "readings": {
      "TEMPERATURE": { "value": 72.4,   "unit": "°C",   "quality": "GOOD" },
      "HUMIDITY":    { "value": 58.2,   "unit": "%RH",  "quality": "GOOD" },
      "CO2_PPM":     { "value": 412.0,  "unit": "ppm",  "quality": "GOOD" },
      "BATTERY_PCT": { "value": 87.0,   "unit": "%",    "quality": "GOOD" },
      "SIGNAL_RSSI": { "value": -67.0,  "unit": "dBm",  "quality": "GOOD" }
    },
    "edgeFirmwareVersion": "2.4.1",
    "edgeIp": "192.168.1.42",
    "edgeUptimeSeconds": 86400,
    "edgeRssi": -67,
    "edgeBatteryPct": 87,
    "edgeFreeHeapBytes": 42680,
    "edgeProtocol": "MQTT_TLS"
  }
]
```

---

### GET /api/v1/telemetry/{deviceId}/cache

Returns the latest telemetry values from Redis (sub-millisecond). All values are strings (Redis hash field type).

#### Response 200

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

### GET /api/v1/telemetry/{deviceId}/range

Returns raw telemetry within a time window. Use ISO 8601 timestamps.

```http
GET /api/v1/telemetry/{deviceId}/range?from=2025-06-01T00:00:00Z&to=2025-06-01T23:59:59Z
Authorization: Bearer <token>
```

**Response 200** — Array of telemetry objects (same schema as `/latest`).

---

### GET /api/v1/telemetry/{deviceId}/hourly

Returns hourly aggregated telemetry. Aggregates persist beyond the raw retention window and power the dashboard 24h / 7d chart modes.

```http
GET /api/v1/telemetry/{deviceId}/hourly?from=2025-06-01T00:00:00Z&to=2025-06-07T23:59:59Z
Authorization: Bearer <token>
```

#### Response 200

```json
[
  {
    "id": "uuid",
    "deviceId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "hourBucket": "2025-06-01T14:00:00Z",
    "tempAvg": 71.4, "tempMin": 65.2, "tempMax": 88.1,
    "humAvg": 58.0,  "humMin": 45.0,  "humMax": 72.0,
    "smokeAvg": 23.5, "smokeMax": 310.0,
    "motionCount": 7,
    "sampleCount": 720
  }
]
```

---

### GET /api/v1/telemetry/stats

Returns event throughput and replay queue depth.

#### Response 200

```json
{
  "lastMinute": 42,
  "replayQueueSize": 0
}
```

`replayQueueSize > 0` means the circuit breaker has tripped and telemetry is being buffered to the Redis replay queue.

---

## Alerts

### GET /api/v1/alerts

Returns the 50 most recent alerts (acknowledged and unacknowledged), newest first.

#### Response 200

```json
[
  {
    "id": "uuid",
    "deviceId": "uuid",
    "organizationId": "uuid",
    "level": "CRITICAL",
    "message": "[sensor-1] CRITICAL: temperature 83.2°C exceeds 80.0°C threshold",
    "acknowledged": false,
    "createdAt": "2025-06-01T09:45:12Z"
  }
]
```

**Alert levels:**

| Level | Trigger |
| --- | --- |
| `CRITICAL` | Per-device `critThreshold` breached (capability-aware), OR temperature > 80 °C / smoke > 200 ppm (global fallback) |
| `WARNING` | Per-device `warnThreshold` breached (capability-aware), OR humidity > 90 % / motion+temp>70 °C (global fallback) |

---

### GET /api/v1/alerts/unacknowledged

Returns only unacknowledged alerts, ordered newest first.

---

### PUT /api/v1/alerts/{id}/acknowledge

Mark an alert as acknowledged. **Requires ADMIN role.**

**Response 204** — No content.  
**Response 403** — Non-ADMIN token.

---

## WebSocket

### WS /ws/telemetry

Real-time telemetry stream. A valid JWT access token must be supplied as a query parameter — the handshake is rejected with HTTP 401 if the token is absent, expired, or revoked.

#### Connect

```js
const ws = new WebSocket(`ws://localhost:8080/ws/telemetry?token=${accessToken}`)
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

The backend delivers messages only to sessions belonging to the **same organization** as the authenticated user (tenant-filtered broadcast). In a multi-replica deployment the Redis Pub/Sub channel `ws:telemetry` ensures every replica broadcasts to its own local sessions with the same tenant filtering.

---

## Observability Endpoints

### GET /actuator/health

No authentication required. Returns liveness and readiness probe status (K8s-compatible when `management.endpoint.health.probes.enabled=true`).

```json
{
  "status": "UP",
  "components": {
    "db":           { "status": "UP" },
    "redis":        { "status": "UP" },
    "livenessState":  { "status": "UP" },
    "readinessState": { "status": "UP" }
  }
}
```

### GET /actuator/prometheus

Prometheus text format. No authentication required (restrict in production via firewall/ingress).

---

## Request Correlation

Every response includes an `X-Request-ID` header echoing the request ID. Send your own to trace distributed calls:

```http
GET /api/v1/devices
X-Request-ID: my-frontend-correlation-id
```

The value is injected into every backend log line as `requestId` in the MDC.

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
| 429 | Rate limit exceeded (Bucket4j — 10 req/min per IP on auth endpoints, 100 req/min on all other endpoints) |
| 500 | Internal server error |

---

## Role Matrix

| Endpoint | ADMIN | OPERATOR | Unauthenticated |
| --- | --- | --- | --- |
| POST /api/v1/auth/login | ✅ | ✅ | ✅ |
| POST /api/v1/auth/refresh | ✅ | ✅ | ✅ |
| POST /api/v1/auth/logout | ✅ | ✅ | ❌ |
| POST /api/v1/devices | ✅ | ❌ | ❌ |
| GET /api/v1/devices | ✅ | ✅ | ❌ |
| GET /api/v1/devices/{id} | ✅ | ✅ | ❌ |
| PATCH /api/v1/devices/{id}/lifecycle | ✅ | ❌ | ❌ |
| PATCH /api/v1/devices/{id}/firmware | ✅ | ❌ | ❌ |
| GET /api/v1/devices/{id}/capabilities | ✅ | ✅ | ❌ |
| PUT /api/v1/devices/{id}/capabilities | ✅ | ❌ | ❌ |
| POST /api/v1/devices/{id}/enrollment-token | ✅ | ❌ | ❌ |
| POST /api/v1/devices/enroll | ✅ | ✅ | ✅ (token is the credential) |
| GET /api/v1/telemetry/* | ✅ | ✅ | ❌ |
| GET /api/v1/alerts | ✅ | ✅ | ❌ |
| GET /api/v1/alerts/unacknowledged | ✅ | ✅ | ❌ |
| PUT /api/v1/alerts/{id}/acknowledge | ✅ | ❌ | ❌ |
