# ADR-0002: Stateless Multi-Replica Scaling on EKS via Redis Pub/Sub, Distributed Rate Limiting & MQTT Shared Subscriptions

- **Status:** Accepted & Implemented
- **Date:** 2026-10-11
- **Deciders:** Sentinel IoT Core Architecture Team
- **Consulted:** DevOps, Cloud Engineering, Security
- **Informed:** Backend Engineers, Site Reliability Engineers

---

## 1. Context and Problem Statement

To meet high-availability and latency SLAs under surge loads, the Sentinel IoT Spring Boot backend is required to run with **3+ horizontal replicas on AWS EKS** behind an AWS Application Load Balancer (ALB).

During initial multi-replica deployment tests, several stateful bottlenecks and collisions were discovered:
1. **MQTT Client ID Collision Loop:** `MqttConfig` hardcoded the client ID as `sentinel-backend-inbound`. When 3 pods connected to Mosquitto simultaneously, the broker severed the connection of each preceding client with the same ID, causing an endless reconnection flap loop ("ping-pong kick loop").
2. **Duplicate MQTT Inbound Messages:** Without shared subscriptions, all 3 replicas subscribed to the same MQTT wildcards, causing every sensor reading to be processed 3 times and duplicated in Kafka.
3. **Fragmented In-Memory Rate Limiting:** `RateLimitFilter` stored Bucket4j token buckets in local `ConcurrentHashMap`. An attacker could bypass rate limits by distributing brute-force requests across the 3 replicas behind the round-robin ALB.
4. **Isolated WebSocket Client Sessions:** When a browser established a WebSocket connection to Pod A, live telemetry processed by Pod B or C was never broadcast to that browser because WebSocket sessions were stored only in the local JVM heap.
5. **Scheduled Task Lock Contention:** `@Scheduled` daily retention jobs fired simultaneously on all 3 pods, creating table lock contention on PostgreSQL.

---

## 2. Decision Drivers

- **Zero-Sticky State (100% Stateless):** Any pod must be able to serve any HTTP request or WebSocket connection, and any pod can be terminated or restarted without data loss or connection thrashing.
- **Fair Message Distribution:** Incoming MQTT messages must be load-balanced evenly across all active backend pods.
- **Unified Cluster-Wide Security:** Rate limiting against brute-force and DDoS attacks must be enforced globally regardless of which pod receives the request.
- **Realtime Dashboard Reliability:** Every connected browser must receive instant live sensor updates regardless of which pod ingested the data from Kafka.

---

## 3. Considered Options

### Component 1: Realtime Telemetry Broadcast
- **Option 1A: Redis Pub/Sub Fan-Out with Local Fallback (Selected):** When any pod consumes a telemetry record from Kafka, it publishes to Redis channel `ws:telemetry`. All pods subscribe to this channel and fan out to their local WebSocket sessions. If Redis fails, each pod falls back to direct local broadcast.
- **Option 1B: Sticky Sessions on ALB:** Pin WebSocket clients to specific pods and route MQTT/Kafka traffic by tenant stickiness. *Rejected:* Introduces statefulness, causes load imbalance, and breaks horizontal autoscaling.

### Component 2: Distributed Rate Limiting
- **Option 2A: Distributed Bucket4j backed by Redis CAS via Lettuce (Selected):** Configure `LettuceBasedProxyManager` connected to Redis. Token consumption uses atomic CAS commands (`EVAL` / Redis hash). Falls back to in-memory buckets on Redis disconnect.
- **Option 2B: In-Memory Only Limiter with Divided Thresholds:** Divide per-IP limits by number of replicas (e.g. 100/3 = 33 req/min). *Rejected:* Inaccurate under dynamic HPA autoscaling (replica count varies from 2 to 10).

### Component 3: MQTT Multi-Replica Ingestion
- **Option 3A: Dynamic Instance Client IDs + MQTT 5 Shared Subscriptions (Selected):** 
  - Inject the Kubernetes Pod name into `INSTANCE_ID` using the Downward API (`metadata.name`).
  - Generate unique client IDs per pod: `${clientId}-${instanceId}-inbound`.
  - Prefix inbound topics with `$share/${mqtt.share-group}/...`. Mosquitto 2.0+ balances messages across subscribers in that group.
- **Option 3B: Dedicated Ingestion Pod (Single Replica Ingest Gateway):** Restrict MQTT consumer to a single pod, while scaling API pods separately. *Rejected:* Creates a single point of failure (SPOF) and ingestion throughput bottleneck.

---

## 4. Decision Outcome

**Chosen: Complete Stateless Multi-Replica Architecture (Options 1A, 2A, and 3A).**

### Architectural Blueprint:

```mermaid
graph TD
    Device[IoT Devices / Gateways] -->|MQTT Publish| Mosquitto[Mosquitto Broker]
    
    subgraph EKS Cluster [EKS Cluster - 3+ Replicas]
        Pod1[Backend Pod 1<br/>ID: backend-x1]
        Pod2[Backend Pod 2<br/>ID: backend-x2]
        Pod3[Backend Pod 3<br/>ID: backend-x3]
    end
    
    Mosquitto -->|"$share/sentinel/tenants/+/telemetry"<br/>Round-Robin Distribution| Pod1
    Mosquitto -.->|Round-Robin| Pod2
    Mosquitto -.->|Round-Robin| Pod3
    
    Pod1 -->|Publish| Kafka[Kafka Cluster<br/>telemetry.raw (3 Partitions)]
    Pod2 -->|Publish| Kafka
    Pod3 -->|Publish| Kafka
    
    Kafka -->|Partition 0| Pod1
    Kafka -->|Partition 1| Pod2
    Kafka -->|Partition 2| Pod3
    
    Pod1 -->|Publish to ws:telemetry| Redis[(Redis Cluster)]
    Redis -->|Pub/Sub Fan-out| Pod1
    Redis -->|Pub/Sub Fan-out| Pod2
    Redis -->|Pub/Sub Fan-out| Pod3
    
    Pod1 -->|Local Sessions| Client1[Browser Client 1]
    Pod2 -->|Local Sessions| Client2[Browser Client 2]
    Pod3 -->|Local Sessions| Client3[Browser Client 3]
    
    ALB[AWS ALB] -->|Round Robin HTTP| Pod1 & Pod2 & Pod3
    Pod1 & Pod2 & Pod3 <-->|Atomic Token CAS| Redis
```

### Key Changes Implemented:
1. **MQTT Suffix & Shared Topics:** In [`MqttConfig.java`](file:///Users/thitipongroo/workspace/github/sentinel-iot-platform/backend/src/main/java/com/sentinel/iot/config/MqttConfig.java), dynamically append `resolveInstanceSuffix()` to `clientId` and wrap inbound topics with `$share/${shareGroup}/...`.
2. **Topic Normalization:** In [`MqttConsumerService.java`](file:///Users/thitipongroo/workspace/github/sentinel-iot-platform/backend/src/main/java/com/sentinel/iot/service/MqttConsumerService.java), strip `$share/<group>/` prefixes when extracting tenant identifiers.
3. **Redis WebSockets:** In [`RedisWebSocketConfig.java`](file:///Users/thitipongroo/workspace/github/sentinel-iot-platform/backend/src/main/java/com/sentinel/iot/config/RedisWebSocketConfig.java) and [`WebSocketBroadcastPublisher.java`](file:///Users/thitipongroo/workspace/github/sentinel-iot-platform/backend/src/main/java/com/sentinel/iot/websocket/WebSocketBroadcastPublisher.java), convert payloads to string envelope `<orgId>|<rawPayload>` and broadcast across instances.
4. **Redis CAS Rate Limiter:** In [`RateLimitConfig.java`](file:///Users/thitipongroo/workspace/github/sentinel-iot-platform/backend/src/main/java/com/sentinel/iot/config/RateLimitConfig.java) and [`RateLimitFilter.java`](file:///Users/thitipongroo/workspace/github/sentinel-iot-platform/backend/src/main/java/com/sentinel/iot/security/RateLimitFilter.java), coordinate Bucket4j token allocations via Redis Lettuce CAS.
5. **Kubernetes Downward API:** In [`backend-deployment.yaml`](file:///Users/thitipongroo/workspace/github/sentinel-iot-platform/infra/helm/sentinel-iot/templates/backend-deployment.yaml) and [`backend-rollout.yaml`](file:///Users/thitipongroo/workspace/github/sentinel-iot-platform/infra/helm/sentinel-iot/templates/backend-rollout.yaml), pass `metadata.name` into `INSTANCE_ID`.

---

## 5. Consequences & Verification

- **Ping-Pong Reconnect Elimination:** Replicas connect with unique client IDs; zero client disconnection flaps observed.
- **Deduplication:** MQTT shared subscription ensures single-delivery ingestion across pods before entering Kafka.
- **Fail-Safe Operation:** If Redis is down, rate limiting falls back to in-memory buckets, and WebSocket publishers broadcast directly to local connections.
- **Automated Testing:** Validated with 285 automated unit, security, and concurrency tests.

---

## 6. References
- [Architecture System Design](file:///Users/thitipongroo/workspace/github/sentinel-iot-platform/docs/system-design/architecture.md)
- [Scaling Guide](file:///Users/thitipongroo/workspace/github/sentinel-iot-platform/docs/system-design/scaling.md)
- [Design Tradeoffs Document](file:///Users/thitipongroo/workspace/github/sentinel-iot-platform/docs/system-design/tradeoffs.md)
