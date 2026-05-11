# Runbook: SentinelLatencyP99Breach

**Alert:** `SentinelLatencyP99Breach`
**Severity:** Critical
**Trigger:** Less than 99% of requests complete within 500 ms (5-minute window)

---

## Impact

The p99 latency SLO is breached. 1%+ of users are waiting more than 500 ms.
At 1,000 req/s this means ~10 slow requests per second. Real-time dashboard updates
and alert notifications will be visibly delayed.

---

## Diagnosis

The p99 target is 5× more lenient than p95 (500 ms vs 200 ms). A p99 breach typically
indicates a more severe problem than a p95 breach — spiky tail latency from long GC pauses,
lock contention, or dependency timeouts.

### 1. Check GC pause duration

```bash
curl -sG http://prometheus:9090/api/v1/query \
  --data-urlencode 'query=histogram_quantile(0.99, rate(jvm_gc_pause_seconds_bucket{job="sentinel-backend"}[5m]))'
```

A GC pause > 300 ms will push p99 above 500 ms even if median latency is fine.

### 2. Check for DB lock contention

```bash
kubectl exec -n sentinel deploy/sentinel-postgres-0 -- \
  psql -U sentinel -c "SELECT pid, wait_event_type, wait_event, state, query FROM pg_stat_activity WHERE wait_event IS NOT NULL;"
```

### 3. Check Hikari connection queue

```bash
curl -sG http://prometheus:9090/api/v1/query \
  --data-urlencode 'query=hikaricp_connections_pending{job="sentinel-backend"}'
```

Pending connections > 0 means threads are waiting for a DB connection — a direct p99 driver.

### 4. Inspect distributed traces

In Jaeger, filter for spans > 400 ms. Look for:

- Long `SELECT` queries (missing index, table scan)
- Redis timeouts (`timeout: 2000ms` configured — a timeout event adds exactly 2s)
- Kafka producer `linger.ms` accumulation under bursty load

---

## Remediation

| Root cause | Fix |
|------------|-----|
| Long GC pauses (>200ms) | Tune GC: `-XX:+UseZGC` for low-pause; or reduce heap allocation rate |
| DB lock wait | Identify and kill blocking queries; add missing index |
| Hikari pool saturation | Increase pool size; add read replica for read-heavy endpoints |
| Redis timeout | Check Redis memory/CPU; increase `spring.data.redis.timeout` if healthy |
| Kafka producer backpressure | Reduce `linger-ms` (currently 5 ms); increase `batch-size` |

### Emergency: disable non-critical operations

If the service is under extreme load, temporarily disable telemetry aggregation:

```bash
# Increase retention cron interval to reduce DB write pressure
kubectl set env deployment/sentinel-backend -n sentinel \
  TELEMETRY_RETENTION_DAYS=90  # Extend retention to reduce delete churn
```

---

## Escalation

A p99 breach that persists > 10 minutes warrants an incident channel. The root cause
is almost never the application itself — check infrastructure (disk I/O on PostgreSQL node,
network saturation, Kubernetes node CPU steal).
