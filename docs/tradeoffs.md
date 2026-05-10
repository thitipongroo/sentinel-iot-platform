# Design Tradeoffs

Every architectural decision involves a tradeoff. This document captures the key choices made in Sentinel IoT Platform, the alternatives considered, and the reasoning behind each decision.

---

## 1. Next.js 14 (App Router) vs Vite + React SPA

**Chosen:** Next.js 14 with App Router

| Aspect | Next.js | Vite + React SPA |
|--------|---------|-----------------|
| Routing | File-based, zero config | `react-router-dom`, manual setup |
| API proxy | `rewrites` in `next.config.mjs` | Requires Vite proxy + nginx in prod |
| Deployment | Native Vercel (zero config) | Static build + CDN + reverse proxy |
| SSR / SEO | Built-in | Not available |
| Bundle size | Tree-shaken per route | Single bundle |
| `'use client'` boundary | Server Components reduce JS sent | Everything is client-side |

**Reasoning:** The `rewrites` feature eliminates the need for a separate nginx container in production, which reduces operational complexity. Vercel's native Next.js support means the dashboard deploys with a single `git push`. The Server Component boundary keeps the initial JS payload small — layout, metadata, and static content ship with zero client JS.

**Tradeoff accepted:** Next.js adds build complexity and a larger `node_modules` footprint compared to a pure Vite SPA. The development feedback loop (`next dev`) is slightly slower than Vite HMR for large component trees.

---

## 2. MQTT vs HTTP Polling

**Chosen:** MQTT (Eclipse Mosquitto)

| Aspect | MQTT | HTTP Polling |
|--------|------|-------------|
| Connection model | Persistent TCP, event-driven | Stateless, request/response |
| Bandwidth | Minimal (only on data change) | `N × poll_freq` requests/min |
| Latency | Sub-second from device to broker | Bounded by poll interval |
| Broker requirement | Yes (Mosquitto / EMQX) | None |
| QoS guarantees | QoS 0/1/2 (exactly-once available) | None (app-layer retry needed) |
| Device battery impact | Low (keep-alive only) | High (repeated HTTP overhead) |

**Reasoning:** With 200 devices polling every 5 seconds, HTTP polling generates 200 × 12 = 2,400 requests/minute even when no values change. MQTT generates 200 × 12 = 2,400 MQTT publishes/minute but the protocol overhead per message is ~2 bytes (fixed header) vs ~500+ bytes for HTTP headers. More importantly, MQTT's persistent sessions allow devices behind NAT to receive commands (bidirectional), which HTTP polling cannot do without long-polling hacks.

**Tradeoff accepted:** MQTT requires running and maintaining a broker (Mosquitto in Docker). HTTP would have zero infrastructure overhead. For a pure read-only telemetry use case at small scale, HTTP polling is simpler.

---

## 3. Redis vs Memcached

**Chosen:** Redis 7

| Aspect | Redis | Memcached |
|--------|-------|-----------|
| Data structures | Strings, Hashes, Lists, Sets, Sorted Sets, Streams | Strings only |
| TTL per key | Yes (per-key EXPIRE) | Yes |
| Persistence | RDB + AOF (configurable) | None |
| Pub/Sub | Built-in | Not available |
| Cluster | Redis Cluster (hash slots) | Consistent hashing client-side |
| Atomic operations | Yes (INCR, GETSET, Lua scripts) | Limited |

**Reasoning:** The cache stores multi-field telemetry (`temperature`, `humidity`, `motion`, `smokePpm`, `ts`) for each device. Redis `HSET/HGET` maps directly to this structure — one Redis key per device, one hash field per sensor reading. With Memcached, each reading would require serialization to a single string and deserialization on read, adding CPU overhead and making partial field updates impossible.

Redis Pub/Sub is also used in the horizontal scaling plan (see `docs/scaling.md`) to broadcast WebSocket messages across backend replicas — a feature Memcached cannot provide.

**Tradeoff accepted:** Redis consumes more RAM than Memcached for equivalent datasets due to its richer data structures. For a pure string cache at massive scale, Memcached's simpler architecture can be faster.

---

## 4. PostgreSQL vs Time-Series Database (InfluxDB / TimescaleDB)

**Chosen:** PostgreSQL 16

| Aspect | PostgreSQL | InfluxDB | TimescaleDB |
|--------|-----------|---------|-------------|
| Data model | Relational (rows + columns) | Tag/field model | Relational (PostgreSQL extension) |
| Write throughput | ~100k rows/sec (tuned) | ~1M points/sec | ~200k rows/sec |
| Query language | SQL | Flux / InfluxQL | SQL (standard) |
| Existing JPA support | Full (Spring Data JPA) | Requires client lib | Full (same as PostgreSQL) |
| Operational overhead | Low (single DB) | High (separate stack) | Low (PostgreSQL extension) |
| Migration path | — | Requires ETL | `SELECT create_hypertable(...)` |

**Reasoning:** At the current scale (<10M rows/month), PostgreSQL with `btree` indexes on `device_id` and `timestamp` performs all range queries in under 50ms. Introducing InfluxDB would add an entirely separate database technology, a new query language (Flux), and a second persistence layer to maintain and back up. The team would need expertise in two database stacks.

TimescaleDB is the preferred migration path if row counts exceed 500M, because it is a PostgreSQL extension — no application code changes are required, and all Spring Data JPA repositories continue to work unchanged.

**Tradeoff accepted:** PostgreSQL will require manual partitioning at very high scale (see `docs/scaling.md`), whereas InfluxDB handles this automatically. If write throughput exceeds ~50k events/sec, PostgreSQL becomes the bottleneck before the MQTT broker does.

---

## 5. Spring Integration vs Raw Paho MQTT Client

**Chosen:** Spring Integration with `MqttPahoMessageDrivenChannelAdapter`

| Aspect | Spring Integration | Raw Paho Client |
|--------|------------------|-----------------|
| Reconnect handling | Automatic | Manual `MqttCallback.connectionLost()` |
| Thread model | Managed by Spring's task executor | Manual thread management |
| Channel routing | Declarative (`@ServiceActivator`) | Imperative `messageArrived()` callback |
| Error handling | Spring's error channel | Manual try/catch in callback |
| Testing | Mock channels with `@SpringBootTest` | Requires live broker or manual mock |
| Complexity | Higher (Spring wiring) | Lower (pure Java) |

**Reasoning:** `@ServiceActivator(inputChannel = "mqttInputChannel")` turns message consumption into a plain Spring bean method. Spring manages thread allocation, error routing, and reconnection lifecycle. Without Spring Integration, the equivalent Paho implementation would require manual `MqttCallbackExtended.reconnect()` loops, manual thread pools for async processing, and manual error handling — approximately 200 lines of boilerplate that Spring generates from 20 lines of configuration.

**Tradeoff accepted:** Spring Integration adds a learning curve and several transitive dependencies. For a simple single-topic consumer, raw Paho is more transparent and easier to reason about. If the team is not already familiar with Spring Integration, the declarative model can feel like magic.

---

## 6. WebSocket vs Server-Sent Events (SSE)

**Chosen:** WebSocket (native Spring WS, `TextWebSocketHandler`)

| Aspect | WebSocket | Server-Sent Events |
|--------|-----------|--------------------|
| Direction | Bidirectional | Server → Client only |
| Protocol | WS / WSS upgrade | HTTP/1.1 or HTTP/2 |
| Browser support | All modern browsers | All modern browsers |
| Proxy / firewall | Some proxies block WS | Always works over HTTP |
| Reconnect | Manual (client handles) | Built-in `EventSource` retry |
| Multiplexing | Single connection, multi-channel | One connection per event stream |

**Reasoning:** SSE covers the current use case (telemetry broadcast from server to browser) perfectly and with less complexity — `EventSource` reconnects automatically, no upgrade handshake is needed, and SSE works through HTTP/2 multiplexing. However, WebSocket was chosen to leave the door open for bidirectional features: device command sending (e.g., reboot a device, change sampling rate), remote configuration, and in-browser admin actions. Retrofitting SSE to bidirectional would require adding a separate REST endpoint for the reverse channel, effectively duplicating the connection.

**Tradeoff accepted:** WebSocket connections can be blocked by some corporate proxies and older load balancers. SSE over plain HTTP is universally routable. If deployment targets heavily firewalled environments, switching to SSE with a REST reverse channel would be lower-risk.

---

## 7. JWT vs Session-Based Authentication

**Chosen:** JWT (stateless)

| Aspect | JWT | Server-side sessions |
|--------|-----|---------------------|
| State | Stateless (token is self-contained) | Stateful (session store required) |
| Horizontal scaling | Zero config — any replica can validate | All replicas must share session store |
| Token revocation | Not possible before expiry (without blocklist) | Immediate (delete session) |
| Payload size | ~300–500 bytes per request | ~50 bytes (session ID cookie) |
| Expiry | Fixed at issue time (24h here) | Sliding window (renews on activity) |

**Reasoning:** The backend is designed to be stateless and horizontally scalable. JWT allows any backend replica to validate a token independently without querying a shared session store. This makes `docker compose up --scale backend=3` work with zero additional configuration.

**Tradeoff accepted:** JWT tokens cannot be revoked before expiry. If an ADMIN account is compromised, the attacker retains access for up to 24 hours. Mitigation: shorten the expiry to 1 hour and implement refresh tokens, or maintain a Redis-based token blocklist (adds statefulness back).

---

## Summary Table

| Decision | Chosen | Main Alternative | Key Reason |
|----------|--------|-----------------|------------|
| Frontend | Next.js 14 | Vite + React | API proxy rewrites, Vercel deploy |
| Messaging | MQTT | HTTP Polling | Event-driven, low bandwidth |
| Cache | Redis | Memcached | Hash structures, Pub/Sub |
| Database | PostgreSQL | InfluxDB | SQL, JPA, zero extra stack |
| MQTT consumer | Spring Integration | Raw Paho | Declarative, auto-reconnect |
| Realtime | WebSocket | SSE | Bidirectional for future commands |
| Auth | JWT | Server sessions | Stateless, horizontal scaling |
