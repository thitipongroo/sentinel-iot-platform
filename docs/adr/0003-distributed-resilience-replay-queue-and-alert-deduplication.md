# ADR-0003: Distributed Resilience: Replay Queue Drop Observability and Cache-Fallbacked Alert Deduplication

- **Status:** Accepted & Implemented
- **Date:** 2026-10-11
- **Deciders:** Sentinel IoT Core Architecture Team
- **Consulted:** SRE, Backend Engineers
- **Informed:** Product Operations

---

## 1. Context and Problem Statement

In production environments, partial network partitions or database failovers can cause cascading issues if the resilience mechanisms themselves lack proper safeguards:
1. **Silent Data Drops on Replay Queue Overflow:** During prolonged database outages, `RedisService.pushToReplayQueue()` capped queue depth at `TELEMETRY_REPLAY_MAX_QUEUE` (10,000 items) to prevent OOM. However, when the limit was exceeded, discarded telemetry messages were dropped silently without dedicated metric instrumentation or operator alerting.
2. **Alert Storm on Redis Outage:** `AlertDeduplicator` utilized Redis keys (`alert:dedup:...`) to enforce a cooldown period (default: 5 minutes) across device alert notifications. If Redis encountered downtime or high latency, the deduplication filter failed open and triggered notifications for every reading, creating severe notification storms (LINE, Telegram, Slack, Webhook flooding).

---

## 2. Decision Drivers

- **Zero Silent Data Loss:** When load shedding or buffer overflows occur, metrics and alerts must immediately notify on-call engineers.
- **Fail-Safe Alert Deduplication:** A temporary outage of Redis must not degrade notification channels into an alert storm.
- **Minimal Overhead:** Defensive fallback caches should be light on JVM memory.

---

## 3. Decision Outcome

**1. Replay Queue Drop Telemetry & SLO Integration:**
- In [`RedisService.java`](file:///Users/thitipongroo/workspace/github/sentinel-iot-platform/backend/src/main/java/com/sentinel/iot/service/RedisService.java), modified `pushToReplayQueue()` to return `boolean` (false when queue is full).
- In [`TelemetryService.java`](file:///Users/thitipongroo/workspace/github/sentinel-iot-platform/backend/src/main/java/com/sentinel/iot/service/TelemetryService.java), registered a Prometheus Counter `sentinel.replay.queue.overflow` that increments whenever telemetry fails to enqueue.
- In [`infra/monitoring/slo-rules.yaml`](file:///Users/thitipongroo/workspace/github/sentinel-iot-platform/infra/monitoring/slo-rules.yaml), added Prometheus alert rule `SentinelReplayQueueTelemetryDropped` (Severity: Critical) to fire if drop rate exceeds 0 over 1 minute.

**2. Two-Tier Alert Deduplication with In-Memory Fallback:**
- In [`AlertDeduplicator.java`](file:///Users/thitipongroo/workspace/github/sentinel-iot-platform/backend/src/main/java/com/sentinel/iot/service/notification/AlertDeduplicator.java), introduced a thread-safe local fallback cache (`ConcurrentHashMap<String, Instant>` with eviction sweep).
- When Redis fails, the system logs a warning and evaluates cooldown against the local cache. If the same alert was seen within the cooldown window on that instance, it is suppressed.

---

## 4. Consequences & Verification

- **Observability:** Operators gain immediate visibility into DB outage duration and data drop volume.
- **Protection Against Alert Fatigue:** Third-party webhooks (LINE, Telegram, Slack) remain protected from rate limit penalties during infrastructure outages.
- **Automated Validation:** Verified via `AlertDeduplicatorTest` and `TelemetryServiceTest` with 100% test pass rate.
