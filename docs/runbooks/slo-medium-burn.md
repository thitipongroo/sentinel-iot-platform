# Runbook: SentinelSLOMediumBurn

**Alert:** `SentinelSLOMediumBurn`
**Severity:** Critical (page)
**Trigger:** 6-hour error budget burn rate > 6× (consuming ~5% of monthly budget per 6 hours)

---

## Impact

Budget exhaustion estimated within **~5 days** at current rate. Not an immediate emergency,
but the trend will cross into fast-burn territory if not investigated. Users may be
experiencing intermittent errors that haven't reached sustained-failure levels.

---

## Diagnosis

### 1. Identify the error pattern
```bash
# Error rate over 6h window
curl -sG http://prometheus:9090/api/v1/query \
  --data-urlencode 'query=sentinel:error_budget_burn_rate:6h'

# Which status codes are contributing
curl -sG http://prometheus:9090/api/v1/query_range \
  --data-urlencode 'query=rate(http_server_requests_seconds_count{job="sentinel-backend",status=~"5.."}[30m])' \
  --data-urlencode 'start=-6h' --data-urlencode 'step=5m'
```

### 2. Check for memory pressure or GC pauses
```bash
# JVM heap usage
curl -sG http://prometheus:9090/api/v1/query \
  --data-urlencode 'query=jvm_memory_used_bytes{area="heap",job="sentinel-backend"}/jvm_memory_max_bytes{area="heap",job="sentinel-backend"}'

# GC pause time
curl -sG http://prometheus:9090/api/v1/query \
  --data-urlencode 'query=rate(jvm_gc_pause_seconds_sum{job="sentinel-backend"}[5m])'
```

### 3. Check Kafka consumer lag trend
```bash
kubectl exec -n sentinel deploy/sentinel-kafka-0 -- \
  /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
  --describe --group sentinel-telemetry-ingest
```

### 4. Review trace data in Jaeger
Open `http://jaeger:16686` and filter by `service=sentinel-backend`, `tags: error=true`
over the last 6 hours. Look for slow spans in `TelemetryService` or database calls.

---

## Remediation

### Gradual scale-out
```bash
# Add one replica and monitor error rate
kubectl scale deployment sentinel-backend -n sentinel --replicas=$(
  kubectl get deploy sentinel-backend -n sentinel -o jsonpath='{.spec.replicas}' | awk '{print $1+1}')
```

### Database connection pool
If errors cluster around DB timeouts, increase the Hikari pool size:
```bash
kubectl set env deployment/sentinel-backend -n sentinel \
  SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=30
```

### Non-urgent rollback
If errors started after a deploy but are not yet critical, schedule a rollback in the next
30-minute window:
```bash
kubectl argo rollouts undo sentinel-backend -n sentinel
```

---

## Escalation

If burn rate exceeds 14.4× within the next hour, the `SentinelSLOFastBurn` alert will fire.
Treat this as the warning window — resolve before escalation.

See also: [SentinelSLOFastBurn runbook](./slo-fast-burn.md)
