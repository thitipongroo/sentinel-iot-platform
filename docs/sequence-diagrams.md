# Sequence Diagrams

All diagrams use [Mermaid](https://mermaid.js.org/) syntax and render natively on GitHub.

---

## 1. MQTT Telemetry Ingestion

The core data path from sensor to database, cache, alert engine, and browser.

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

    MQTT->>DB: findByName(deviceId)
    DB-->>MQTT: Device entity

    MQTT->>DB: save(device.status=ONLINE, lastSeen=now)
    MQTT->>DB: INSERT telemetry row
    MQTT->>REDIS: HSET device:telemetry:{id} temperature humidity motion smokePpm ts

    MQTT->>ALERT: evaluate(deviceId, temp, humidity, motion, smoke)

    alt temperature > 80°C OR smokePpm > 200
        ALERT->>DB: INSERT alert (level=CRITICAL)
        ALERT->>LINE: POST /api/notify
    else humidity > 90% OR (motion AND temp > 70°C)
        ALERT->>DB: INSERT alert (level=WARNING)
    end

    MQTT->>WS: broadcast(raw JSON payload)
    WS->>UI: TextMessage over WebSocket
    UI->>UI: append to chart data / refresh alert list
```

---

## 2. User Authentication (JWT)

Login flow that produces a JWT used on all subsequent API calls.

```mermaid
sequenceDiagram
    participant Browser as Next.js Browser
    participant API as AuthController
    participant AUTH as AuthenticationManager
    participant UDS as UserDetailsServiceImpl
    participant DB as PostgreSQL (app_users)
    participant JWT as JwtService

    Browser->>API: POST /api/auth/login<br/>{ username, password }
    API->>AUTH: authenticate(username, password)
    AUTH->>UDS: loadUserByUsername(username)
    UDS->>DB: SELECT * FROM app_users WHERE username = ?
    DB-->>UDS: AppUser { password_hash, role }
    UDS-->>AUTH: UserDetails
    AUTH->>AUTH: BCrypt.matches(raw, hash)

    alt credentials valid
        AUTH-->>API: Authentication object
        API->>JWT: generateToken(username, role)
        JWT-->>API: signed JWT (24h expiry)
        API-->>Browser: 200 { token, role, username }
        Browser->>Browser: localStorage.setItem('sentinel_token', token)
    else credentials invalid
        AUTH-->>API: BadCredentialsException
        API-->>Browser: 401 Unauthorized
    end
```

---

## 3. Authenticated API Request (JWT Filter)

Every protected REST call passes through `JwtAuthFilter` before reaching the controller.

```mermaid
sequenceDiagram
    participant Browser as Next.js Browser
    participant FILTER as JwtAuthFilter
    participant JWT as JwtService
    participant UDS as UserDetailsServiceImpl
    participant CTRL as Controller
    participant SVC as Service Layer

    Browser->>FILTER: GET /api/devices<br/>Authorization: Bearer eyJ...
    FILTER->>JWT: extractUsername(token)
    JWT-->>FILTER: "admin"
    FILTER->>UDS: loadUserByUsername("admin")
    UDS-->>FILTER: UserDetails { roles: [ROLE_ADMIN] }
    FILTER->>JWT: isTokenValid(token, userDetails)
    JWT-->>FILTER: true

    FILTER->>FILTER: set SecurityContext authentication

    FILTER->>CTRL: forward request
    CTRL->>SVC: findAll()
    SVC-->>CTRL: List<Device>
    CTRL-->>Browser: 200 [ { id, name, status, ... } ]
```

---

## 4. Alert Trigger and LINE Notification

Detail of how a threshold breach propagates to an alert record and external notification.

```mermaid
sequenceDiagram
    participant MQTT as MqttConsumerService
    participant ALERT as AlertService
    participant DB as PostgreSQL (alerts)
    participant NOTIFY as NotificationService
    participant LINE as LINE Notify API

    MQTT->>ALERT: evaluate(deviceId, "sensor-1", temp=83.2, hum=60, motion=false, smoke=15)

    ALERT->>ALERT: check temperature > 80.0 → true
    ALERT->>DB: INSERT INTO alerts (device_id, level='CRITICAL', message='[sensor-1] CRITICAL: temperature 83.2°C exceeds 80.0°C threshold')
    DB-->>ALERT: saved Alert entity

    ALERT->>NOTIFY: send("[sensor-1] CRITICAL: temperature 83.2°C ...")

    alt LINE_NOTIFY_ENABLED = true AND token set
        NOTIFY->>LINE: POST https://notify-api.line.me/api/notify<br/>Authorization: Bearer {token}<br/>message=[sensor-1] CRITICAL: ...
        LINE-->>NOTIFY: 200 { status: 200, message: "ok" }
    else not configured
        NOTIFY->>NOTIFY: log.debug("LINE Notify disabled")
    end

    ALERT->>ALERT: check smokePpm > 200 → false (skip)
    ALERT->>ALERT: check humidity > 90 → false (skip)
    ALERT->>ALERT: check motion AND temp > 70 → false (skip)
```

---

## 5. WebSocket Subscription and Realtime Update

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

    Note over Browser,HANDLER: Connection established — browser waits

    MQTT->>HANDLER: broadcast(payload JSON string)
    loop for each open session
        HANDLER->>Browser: TextMessage { deviceId, temperature, humidity, motion, smokePpm, timestamp }
    end

    Browser->>Browser: parse JSON
    Browser->>Browser: update telemetry chart state
    Browser->>Browser: check if alert threshold crossed → refresh AlertList

    Note over Browser,HANDLER: On disconnect (tab close / network loss)
    Browser--xHANDLER: WebSocket closed
    HANDLER->>HANDLER: afterConnectionClosed(session)
    HANDLER->>HANDLER: sessions.remove(session)

    Note over Browser: useWebSocket hook retries after 3s
    Browser->>WS_CFG: reconnect attempt
```

---

## 6. Device Registration (ADMIN Flow)

End-to-end flow for an ADMIN registering a new device via the API.

```mermaid
sequenceDiagram
    participant ADMIN as Admin User (Browser)
    participant API as DeviceController
    participant SVC as DeviceService
    participant DB as PostgreSQL (devices)
    participant REDIS as Redis

    ADMIN->>API: POST /api/devices<br/>Authorization: Bearer {admin_token}<br/>{ name: "sensor-4", location: "Hall C" }

    API->>API: JwtAuthFilter validates ROLE_ADMIN
    API->>SVC: create(DeviceRequest)
    SVC->>DB: existsByName("sensor-4")
    DB-->>SVC: false

    SVC->>SVC: new Device(name="sensor-4", status="OFFLINE")
    SVC->>DB: save(device)
    DB-->>SVC: Device { id: uuid, createdAt: now }

    SVC->>REDIS: SET device:status:{uuid} "OFFLINE" EX 600
    SVC-->>API: Device entity
    API-->>ADMIN: 201 Created { id, name, status="OFFLINE", createdAt }

    Note over ADMIN: Simulator must publish deviceId="sensor-4"<br/>for device to appear ONLINE
```
