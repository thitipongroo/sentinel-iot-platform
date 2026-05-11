# Runbook: SentinelLatencyP95Breach

**Alert:** `SentinelLatencyP95Breach`
**Severity:** Warning
**Trigger:** Less than 95% of requests complete within 200 ms (5-minute window)

---

## Impact

The p95 latency SLO is breached. 5%+ of users are waiting longer than 200 ms per API call.
Dashboard data may feel sluggish. Real-time alerts may appear delayed.

---

## Diagnosis

### 1. Find the slow endpoint

```bash
# p95 latency per endpoint
curl -sG http://prometheus:9090/api/v1/query \
  --data-urlencode 'query=histogram_quantile(0.95, rate(http_server_requests_seconds_bucket{job="sentinel-backend"}[5m])) by (uri)'
```

### 2. Check Redis cache hit rate

The hot path (`GET /api/telemetry/{id}/cache`) is Redis-backed. A cache miss causes a DB round-trip.

```bash
# Confirm Redis is responding
kubectl exec -n sentinel deploy/sentinel-redis-0 -- redis-cli ping
kubectl exec -n sentinel deploy/sentinel-redis-0 -- redis-cli info stats | grep keyspace
```

### 3. Check PostgreSQL query latency

```bash
# Connection pool saturation
curl -sG http://prometheus:9090/api/v1/query \
  --data-urlencode 'query=hikaricp_connections_active{job="sentinel-backend"}'

# Slow query log (requires pg_stat_statements)
kubectl exec -n sentinel deploy/sentinel-postgres-0 -- \
  psql -U sentinel -c "SELECT query, mean_exec_time, calls FROM pg_stat_statements ORDER BY mean_exec_time DESC LIMIT 10;"
```

### 4. Check JVM GC

```bash
curl -sG http://prometheus:9090/api/v1/query \
  --data-urlencode 'query=rate(jvm_gc_pause_seconds_sum{job="sentinel-backend"}[5m])'
```

GC pauses over 50 ms will directly inflate p95.

### 5. Trace a slow request in Jaeger

Open `http://jaeger:16686` → service: `sentinel-backend` → sort by duration (slowest first).
Identify which span (DB, Redis, Kafka publish) is the bottleneck.

---

## Remediation

| Root cause | Fix |
|------------|-----|
| Redis cache miss (eviction or TTL too short) | Increase `TTL` in `RedisService` from 10 min; or scale Redis memory |
| DB connection pool exhausted | Increase `SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE` |
| GC pressure | Increase JVM heap: add `-Xmx2g` to `JAVA_OPTS` |
| Cold start (after scale event) | JVM warmup — latency normalises within 2–3 minutes |
| Thundering herd (many devices connecting simultaneously) | Stagger reconnects; add jitter to client intervals |

---

## Notes

The SLO threshold is **p95 < 200 ms**. The baseline (single-node Docker Compose) achieves
**p95 ≈ 112 ms** — there is 88 ms of headroom before the SLO is violated.
See [scaling.md](../scaling.md) for the capacity roadmap.
