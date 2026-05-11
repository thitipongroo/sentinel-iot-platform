# Incident Response Flow

This document defines the standard process for declaring, managing, and closing incidents
in the Sentinel IoT Platform.

---

## Severity Levels

| Level | Definition | Response Time | Example |
|-------|-----------|---------------|---------|
| **P0** | Total outage — platform completely unavailable | Immediate (5 min) | All API endpoints 503 |
| **P1** | Critical degradation — SLO breach, data loss risk | 15 minutes | Fast burn alert firing, Kafka lag critical |
| **P2** | Partial degradation — subset of users affected | 1 hour | p95 latency SLO breach, single pod crash-looping |
| **P3** | Minor issue — no user impact | Next business day | JVM heap warning, slow burn |

---

## Incident Lifecycle

### 1. Declare (0–5 minutes)
- On-call receives page from Alertmanager
- Create incident channel: `#incident-YYYY-MM-DD-<short-description>`
- Post initial message:
  ```
  🔴 INCIDENT DECLARED — [P0/P1/P2]
  Alert: <alert name>
  Time: <UTC timestamp>
  Incident commander: @<name>
  Status: Investigating
  ```
- Assign roles: **Incident Commander** (IC), **Technical Lead**, **Communications Lead**

### 2. Investigate (5–30 minutes)
Follow the relevant runbook for the alert that fired:
- [SentinelSLOFastBurn](./slo-fast-burn.md)
- [SentinelSLOMediumBurn](./slo-medium-burn.md)
- [SentinelTelemetryLagCritical](./kafka-lag-critical.md)
- [SentinelLatencyP99Breach](./latency-p99.md)
- [SentinelJVMHeapHigh](./jvm-heap.md)

Post updates to the incident channel **every 10 minutes** while investigating.

### 3. Mitigate (variable)
Implement the smallest change that stops the bleeding:
- Rollback > fix-forward for production incidents
- Scale out > optimise for latency incidents
- Restart > debug for memory leak incidents

Announce mitigation start in channel:
```
🟡 MITIGATING — [action being taken]
ETA: <estimated resolution time>
```

### 4. Resolve
Once user impact stops:
```
🟢 RESOLVED
Duration: <start> → <end> (<total minutes>)
Root cause (preliminary): <one sentence>
Error budget consumed: ~<N> minutes
```

Mark the Alertmanager alert as resolved if it hasn't auto-resolved.

### 5. Post-mortem (within 48 hours)

Complete this template:

```markdown
## Incident Post-Mortem: <title>

**Date:** YYYY-MM-DD
**Duration:** HH:MM
**Severity:** P0/P1/P2
**Error budget consumed:** N minutes (X% of monthly budget)

### Timeline (UTC)
- HH:MM — Alert fired
- HH:MM — Investigation started
- HH:MM — Root cause identified
- HH:MM — Mitigation deployed
- HH:MM — Incident resolved

### Root Cause
[1-2 sentences describing what failed and why]

### Contributing Factors
- Factor 1
- Factor 2

### What Went Well
- 

### What Went Poorly
- 

### Action Items
| Action | Owner | Due date |
|--------|-------|----------|
| | | |
```

---

## Key Contacts & Tools

| Tool | URL | Purpose |
|------|-----|---------|
| Grafana | `http://grafana:3001` | SLO dashboard, metrics |
| Jaeger | `http://jaeger:16686` | Distributed traces |
| Prometheus | `http://prometheus:9090` | Raw metrics / ad-hoc queries |
| Kafka UI | `kubectl port-forward svc/kafka 9092` | Consumer group lag |
| Actuator | `http://backend:8080/actuator/health` | Circuit breaker state |

---

## Communication Templates

### Status page update (external)
```
[INVESTIGATING] We are investigating elevated error rates on the Sentinel IoT Platform.
The platform remains available but some requests may fail. Updates every 15 minutes.
Started: <UTC time>
```

### Resolution notice (external)
```
[RESOLVED] The incident affecting the Sentinel IoT Platform has been resolved.
Duration: <N> minutes. A post-mortem will be published within 48 hours.
```
