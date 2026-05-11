# Sequence Diagrams

All diagrams use [Mermaid](https://mermaid.js.org/) syntax and render natively on GitHub.

---

## 1. MQTT Telemetry Ingestion — Normal Path

The full 5-stage ingestion pipeline from sensor to database, cache, alert engine, and browser.

```mermaid
sequenceDiagram
    participant SIM as Node.js Simulator
    participant MQ as Mosquitto Broker
    participant MQTT as MqttConsumerService
    participant DB as PostgreSQL
    participant REDIS as Redis
    participant ALERT as AlertService
    participant LINE as LINE Notify
    participant WS as WebSocket Handler
    participant UI as Next.js Dashboard

    SIM->>MQ: PUBLISH factory/telemetry (QoS 1)<br/>{ deviceId, temperature, humidity, motion, smokePpm }
    MQ-->>SIM: PUBACK
    MQ->>MQTT: DELIVER message (Spring Integration channel)

    Note over MQTT: ① Parse JSON
    Note over MQTT: ② Validate fields (range checks)
    MQTT->>DB: findByName(deviceId)
    DB-->>MQTT: Device entity
    Note over MQTT: ③ Lifecycle gate (ACTIVE/PROVISIONED → proceed)

    MQTT->>MQTT: ④ TelemetryService.save() [@Retry + @CircuitBreaker]
    MQTT->>DB: INSERT telemetry row (partitioned table)
    MQTT->>REDIS: HSET device:telemetry:{id} { temperature, humidity, motion, smokePpm, ts }
    DB-->>MQTT: saved Telemetry
    MQTT->>DB: UPDATE devices SET status=ONLINE, last_seen=now

    MQTT->>ALERT: evaluate(deviceId, name, readings, capabilities)

    alt per-device critThreshold breached (capability-aware)<br/>OR temperature > 80°C / smokePpm > 200 ppm (global fallback)
        ALERT->>DB: INSERT alert (level=CRITICAL)
        ALERT->>LINE: POST /api/notify (if enabled)
    else per-device warnThreshold breached (capability-aware)<br/>OR humidity > 90% / (motion AND temp > 70°C) (global fallback)
        ALERT->>DB: INSERT alert (level=WARNING)
    end

    MQTT->>WS: broadcast(raw JSON payload)
    WS->>UI: TextMessage over WebSocket
    UI->>UI: append to chart / refresh alert list
```

---

## 2. MQTT Ingestion — DLQ Failure Paths

How invalid payloads and lifecycle-rejected devices are routed to the dead letter queue.

```mermaid
sequenceDiagram
    participant MQ as Mosquitto Broker
    participant MQTT as MqttConsumerService
    participant DLQ as mqttDlqChannel
    participant MQ2 as Mosquitto (DLQ topic)
    participant DB as PostgreSQL

    MQ->>MQTT: DELIVER message

    alt ① JSON parse failure
        MQTT->>DLQ: route with dlq-error-code=PARSE_ERROR
    else ② Field validation failure (out of range / null deviceId)
        MQTT->>DLQ: route with dlq-error-code=VALIDATION_ERROR
    else ③ Device not found in DB
        MQTT->>DB: findByName(deviceId)
        DB-->>MQTT: null
        MQTT->>DLQ: route with dlq-error-code=UNKNOWN_DEVICE
    else ④ Device INACTIVE or DECOMMISSIONED
        MQTT->>DB: findByName(deviceId)
        DB-->>MQTT: Device { lifecycleStatus=INACTIVE }
        MQTT->>DLQ: route with dlq-error-code=LIFECYCLE_REJECTED
    else ⑤ Unexpected processing exception
        MQTT->>DLQ: route with dlq-error-code=PROCESSING_ERROR
    end

    DLQ->>MQ2: PUBLISH factory/telemetry/dlq<br/>headers: { dlq-error-code, dlq-error-detail, dlq-timestamp }
    Note over MQ2: Retained for offline analysis / alerting
```

---

## 3. DB Outage — Circuit Breaker + Replay Queue

How the system buffers telemetry during a database outage and recovers without data loss.

```mermaid
sequenceDiagram
    participant MQTT as MqttConsumerService
    participant SVC as TelemetryService
    participant DB as PostgreSQL
    participant REDIS as Redis
    participant CB as CircuitBreaker (telemetryDB)
    participant REPLAY as ReplayQueueService

    Note over DB: Database becomes unavailable

    MQTT->>SVC: save(deviceId, temp, humidity, motion, smoke)
    SVC->>CB: call attempt
    CB->>DB: INSERT telemetry
    DB--xCB: DataAccessException (×5 in sliding window)
    CB->>CB: state → OPEN

    loop While CB is OPEN
        MQTT->>SVC: save(...)
        SVC->>CB: call attempt
        CB-->>SVC: CallNotPermittedException → saveFallback()
        SVC->>REDIS: HSET device:telemetry:{id} (cache stays live)
        SVC->>REDIS: RPUSH sentinel:replay:queue (serialized JSON)
    end

    Note over DB: Database recovers

    CB->>CB: wait 30s → state → HALF_OPEN
    REPLAY->>CB: check state (not OPEN)
    REPLAY->>REDIS: LPOP sentinel:replay:queue (batch 100)
    loop For each buffered message
        REPLAY->>DB: TelemetryRepository.save(telemetry)
        alt save succeeds
            REPLAY->>REPLAY: replaySuccessCounter++
        else save fails
            REPLAY->>REDIS: RPUSH sentinel:replay:queue (re-queue)
            REPLAY->>REPLAY: replayFailureCounter++
        end
    end
    CB->>CB: sufficient successes → state → CLOSED
```

---

## 4. User Authentication (Login + Refresh Token Rotation)

Login flow that produces an access token and a refresh token with automatic rotation.

```mermaid
sequenceDiagram
    participant Browser as Next.js Browser
    participant API as AuthController
    participant AUTH as AuthenticationManager
    participant DB as PostgreSQL (app_users / refresh_tokens)
    participant JWT as JwtService

    Browser->>API: POST /api/v1/auth/login { username, password }
    API->>AUTH: authenticate(username, password)
    AUTH->>DB: SELECT * FROM app_users WHERE username = ?
    DB-->>AUTH: AppUser { password_hash, role }
    AUTH->>AUTH: BCrypt.matches(raw, hash)

    alt credentials valid
        AUTH-->>API: Authentication object
        API->>JWT: generateAccessToken(username, role)
        JWT-->>API: signed JWT (15-min expiry)
        API->>DB: INSERT refresh_tokens (uuid, user, expires_in_7_days)
        API-->>Browser: 200 { accessToken, refreshToken, role, username }
        Browser->>Browser: store tokens (memory / httpOnly cookie)
    else credentials invalid
        AUTH-->>API: BadCredentialsException
        API-->>Browser: 401 Unauthorized
    end

    Note over Browser: 15 minutes later — access token expires

    Browser->>API: POST /api/v1/auth/refresh { refreshToken: "old-uuid" }
    API->>DB: SELECT refresh_token WHERE token = "old-uuid" AND NOT expired
    DB-->>API: valid RefreshToken entity
    API->>JWT: generateAccessToken(username, role)
    JWT-->>API: new signed JWT
    API->>DB: DELETE old refresh token (rotation)
    API->>DB: INSERT new refresh token (new-uuid, expires_in_7_days)
    API-->>Browser: 200 { accessToken: newJwt, refreshToken: "new-uuid", ... }
```

---

## 5. Authenticated API Request (JWT Filter + Request Correlation)

Every protected REST call passes through `RequestIdFilter` and `JwtAuthFilter` before reaching the controller.

```mermaid
sequenceDiagram
    participant Browser as Next.js Browser
    participant RID as RequestIdFilter
    participant FILTER as JwtAuthFilter
    participant JWT as JwtService
    participant CTRL as Controller
    participant SVC as Service Layer

    Browser->>RID: GET /api/v1/devices<br/>Authorization: Bearer eyJ...<br/>X-Request-ID: abc-123
    RID->>RID: MDC.put(requestId="abc-123", method="GET", path="/api/v1/devices")
    RID->>FILTER: forward

    FILTER->>JWT: extractUsername(token)
    JWT-->>FILTER: "admin"
    FILTER->>JWT: isTokenValid(token, userDetails)
    JWT-->>FILTER: true
    FILTER->>FILTER: set SecurityContext authentication

    FILTER->>CTRL: forward request
    CTRL->>SVC: findAll()
    SVC-->>CTRL: List<Device>
    CTRL-->>RID: 200 response
    RID->>RID: MDC.put(durationMs=12)
    RID->>RID: MDC.clear()
    RID-->>Browser: 200 [ {...} ]<br/>X-Request-ID: abc-123
```

---

## 6. Alert Trigger and Multi-Provider Notification

Detail of how a threshold breach propagates from ingestion to a persisted alert and one or more external notification providers.

```mermaid
sequenceDiagram
    participant MQTT as MqttConsumerService
    participant ALERT as AlertService
    participant DB as PostgreSQL (alerts)
    participant NOTIFY as NotificationService
    participant SLACK as Slack Webhook
    participant WEBHOOK as Generic Webhook
    participant LINE as LINE Notify (deprecated)

    MQTT->>ALERT: evaluate(deviceId, "sensor-1",<br/>readings={TEMPERATURE:{value:83.2,unit:"°C"}, ...},<br/>capabilities={TEMPERATURE:{critThreshold:80.0, ...}})

    ALERT->>ALERT: TEMPERATURE 83.2 > critThreshold 80.0 → CRITICAL
    ALERT->>DB: INSERT INTO alerts (device_id, level='CRITICAL',<br/>message='[sensor-1] CRITICAL: temperature 83.2°C exceeds 80.0°C')
    DB-->>ALERT: saved Alert entity

    ALERT->>NOTIFY: send("[sensor-1] CRITICAL: temperature 83.2°C ...")
    Note over NOTIFY: Iterates all enabled NotificationProvider beans

    alt SLACK_NOTIFY_ENABLED = true
        NOTIFY->>SLACK: POST hooks.slack.com/... { "text": "..." }
        SLACK-->>NOTIFY: 200 OK
    end

    alt NOTIFY_WEBHOOK_ENABLED = true
        NOTIFY->>WEBHOOK: POST your-endpoint/alert<br/>X-Sentinel-Signature: sha256=... (if secret configured)
        WEBHOOK-->>NOTIFY: 200 OK
    end

    alt LINE_NOTIFY_ENABLED = true (deprecated — LINE Notify shut down 2025-03-31)
        NOTIFY->>LINE: POST notify-api.line.me/api/notify<br/>Authorization: Bearer {token}
        LINE-->>NOTIFY: 200 (or error if token expired)
    end

    alt no providers enabled
        NOTIFY->>NOTIFY: log.debug("No notification providers enabled")
    end

    ALERT->>ALERT: SMOKE_PPM 15 < critThreshold 200 → skip
    ALERT->>ALERT: HUMIDITY 60 < warnThreshold 90 → skip
```

---

## 7. WebSocket Subscription and Realtime Update

How the browser receives live telemetry without polling.

```mermaid
sequenceDiagram
    participant Browser as Next.js Dashboard
    participant WS_CFG as WebSocketConfig
    participant HANDLER as TelemetryWebSocketHandler
    participant MQTT as MqttConsumerService

    Browser->>WS_CFG: WS handshake ws://localhost:8080/ws/telemetry
    WS_CFG->>HANDLER: afterConnectionEstablished(session)
    HANDLER->>HANDLER: sessions.add(session)
    WS_CFG-->>Browser: 101 Switching Protocols

    Note over Browser,HANDLER: Connection established — browser waits for messages

    MQTT->>HANDLER: broadcast(payload JSON string)
    loop for each open session
        HANDLER->>Browser: TextMessage { deviceId, temperature, humidity, motion, smokePpm, ts }
    end

    Browser->>Browser: parse JSON
    Browser->>Browser: update TelemetryChart state
    Browser->>Browser: if threshold crossed → refresh AlertList

    Note over Browser,HANDLER: On disconnect (tab close / network loss)
    Browser--xHANDLER: WebSocket closed
    HANDLER->>HANDLER: afterConnectionClosed(session)
    HANDLER->>HANDLER: sessions.remove(session)

    Note over Browser: useWebSocket hook retries with exponential backoff<br/>delay = min(1000 × 2^attempt, 30000) ms ± 30% jitter
    Browser->>WS_CFG: reconnect attempt
    Note over Browser: On success → attemptRef resets to 0
```

---

## 8. Device Registration and Lifecycle Transition (ADMIN Flow)

End-to-end flow for registering a device and transitioning it through lifecycle states.

```mermaid
sequenceDiagram
    participant ADMIN as Admin User (Browser)
    participant API as DeviceController
    participant SVC as DeviceService
    participant DB as PostgreSQL (devices)

    ADMIN->>API: POST /api/v1/devices<br/>Authorization: Bearer {admin_token}<br/>{ name: "sensor-4", location: "Hall C" }
    API->>API: JwtAuthFilter validates ROLE_ADMIN
    API->>SVC: create(DeviceRequest)
    SVC->>DB: existsByName("sensor-4")
    DB-->>SVC: false
    SVC->>DB: INSERT device { name, status=OFFLINE, lifecycleStatus=PROVISIONED }
    DB-->>SVC: Device { id: uuid }
    SVC-->>API: Device entity
    API-->>ADMIN: 201 Created { id, name, status="OFFLINE", lifecycleStatus="PROVISIONED" }

    Note over ADMIN: Device is provisioned — now activate it

    ADMIN->>API: PATCH /api/v1/devices/{id}/lifecycle { lifecycleStatus: "ACTIVE" }
    API->>SVC: updateLifecycle(id, ACTIVE)
    SVC->>DB: findById(id)
    DB-->>SVC: Device { lifecycleStatus=PROVISIONED }
    SVC->>SVC: PROVISIONED → ACTIVE: allowed
    SVC->>DB: UPDATE devices SET lifecycle_status='ACTIVE'
    DB-->>SVC: updated Device
    SVC-->>API: Device entity
    API-->>ADMIN: 200 { lifecycleStatus: "ACTIVE", ... }

    Note over ADMIN: Later — decommission the device

    ADMIN->>API: PATCH /api/v1/devices/{id}/lifecycle { lifecycleStatus: "DECOMMISSIONED" }
    API->>SVC: updateLifecycle(id, DECOMMISSIONED)
    SVC->>SVC: ACTIVE → DECOMMISSIONED: allowed, force status=OFFLINE
    SVC->>DB: UPDATE devices SET lifecycle_status='DECOMMISSIONED', status='OFFLINE'
    API-->>ADMIN: 200 { lifecycleStatus: "DECOMMISSIONED", status: "OFFLINE" }

    Note over ADMIN: Any further lifecycle PATCH → 409 Conflict
```

---

## 9. Device Registration (Legacy API-Only Flow)

---

## 10. Secure Device Enrollment (Token Bootstrap)

How a new IoT device obtains its MQTT credentials without requiring a pre-provisioned shared secret.

```mermaid
sequenceDiagram
    participant ADMIN as Admin User
    participant API as DeviceController
    participant ENROLL as DeviceEnrollmentService
    participant DB as PostgreSQL
    participant DEVICE as IoT Device

    Note over ADMIN: Device is in PROVISIONED state<br/>Admin generates a one-time token

    ADMIN->>API: POST /api/v1/devices/{id}/enrollment-token<br/>Authorization: Bearer {admin_token}
    API->>API: @PreAuthorize hasRole('ADMIN')
    API->>ENROLL: generateToken(deviceId, orgId, username)
    ENROLL->>DB: findByIdAndOrganizationId(deviceId, orgId)
    DB-->>ENROLL: Device (not DECOMMISSIONED)
    ENROLL->>ENROLL: SecureRandom.nextBytes(32) → rawToken (256-bit)
    ENROLL->>ENROLL: SHA-256(rawToken) → tokenHash
    ENROLL->>DB: INSERT device_enrollment_tokens<br/>{ deviceId, tokenHash, expiresAt=now+24h, createdBy }
    ENROLL->>ENROLL: auditService.log(ENROLLMENT_TOKEN_ISSUED)
    ENROLL-->>API: EnrollmentTokenResponse { tokenId, rawToken, deviceId, expiresAt }
    API-->>ADMIN: 200 { token: "gX7kPq...", expiresAt: "..." }

    Note over ADMIN,DEVICE: Admin delivers rawToken out-of-band<br/>(QR code, serial console, provisioning portal)

    DEVICE->>API: POST /api/v1/devices/enroll (unauthenticated)<br/>{ deviceId, token: "gX7kPq..." }
    API->>ENROLL: enroll(request, remoteIp)
    ENROLL->>ENROLL: SHA-256(request.token) → tokenHash
    ENROLL->>DB: findByTokenHash(tokenHash)
    DB-->>ENROLL: DeviceEnrollmentToken
    ENROLL->>ENROLL: validate deviceId match
    ENROLL->>ENROLL: isValid() → not expired AND not used
    ENROLL->>DB: UPDATE token SET used_at=now, used_by_ip=remoteIp
    ENROLL->>DB: UPDATE device SET lifecycle_status=ACTIVE, status=ONLINE
    ENROLL->>ENROLL: SecureRandom → mqttPassword (192-bit)
    ENROLL->>ENROLL: auditService.log(DEVICE_ENROLLED)
    ENROLL-->>API: mqttPassword
    API-->>DEVICE: 200 { mqttUsername: "device-{id}", mqttPassword: "..." }

    Note over DEVICE: Device connects to MQTT broker<br/>with returned credentials
    Note over DEVICE: Token is now single-use — any<br/>replay attempt returns 409
```

Minimal flow for creating a device and verifying it goes online when the simulator publishes.

```mermaid
sequenceDiagram
    participant ADMIN as Admin User (Browser)
    participant API as DeviceController
    participant SVC as DeviceService
    participant DB as PostgreSQL (devices)
    participant REDIS as Redis

    ADMIN->>API: POST /api/v1/devices { name: "sensor-4", location: "Hall C" }
    API->>SVC: create(DeviceRequest)
    SVC->>DB: existsByName("sensor-4") → false
    SVC->>DB: INSERT device
    SVC->>REDIS: SET device:status:{uuid} "OFFLINE" EX 600
    SVC-->>API: Device entity
    API-->>ADMIN: 201 Created { id, name, status="OFFLINE" }

    Note over ADMIN: Simulator must publish deviceId="sensor-4"<br/>for device to appear ONLINE
```
