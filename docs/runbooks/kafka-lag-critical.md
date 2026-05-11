# Runbook: SentinelTelemetryLagCritical

**Alert:** `SentinelTelemetryLagCritical`
**Severity:** Critical (page immediately)
**Trigger:** Consumer group `sentinel-telemetry-ingest` lag on `telemetry.raw` > 50,000 messages

---

## Impact

**Devices are accumulating data faster than the pipeline can process.** At baseline
ingest rate (40 events/sec), 50,000 messages of lag = ~21 minutes of telemetry backlog.
Alerts are delayed by at least 21 minutes. Dashboard data is severely stale.
If lag keeps growing, Kafka topic retention (7 days) may eventually cause message loss.

---

## Immediate Response (first 5 minutes)

### 1. Scale up consumers immediately
```bash
# Max replicas is capped by number of Kafka partitions (currently 3)
kubectl scale deployment sentinel-backend -n sentinel --replicas=3
# If already at 3, increasing further won't help — see Option D below
```

### 2. Verify KEDA ScaledObject is not conflicting
```bash
kubectl get scaledobject sentinel-backend-keda -n sentinel -o yaml | grep -A5 maxReplicaCount
# If KEDA max is lower than 3, patch it:
kubectl patch scaledobject sentinel-backend-keda -n sentinel \
  --type=merge -p '{"spec":{"maxReplicaCount":20}}'
```

### 3. Confirm consumers are actually processing
```bash
kubectl exec -n sentinel deploy/sentinel-kafka-0 -- \
  /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --describe --group sentinel-telemetry-ingest
# Lag should be decreasing. If all LAG values are frozen, consumers are deadlocked.
```

---

## Diagnosis

### Consumer deadlock scenario
```bash
kubectl logs -n sentinel -l app=sentinel-backend --tail=500 | grep -E "ERROR|deadlock|timeout|CircuitBreaker"
```

If `@CircuitBreaker(name="telemetryDB")` is in OPEN state, the consumer is discarding
messages to the Redis replay queue rather than persisting to PostgreSQL. The replay queue
drains every 30 seconds (100 messages/batch) — not enough to drain a 50k backlog.

### Database bottleneck
```bash
# Check active connections and long-running queries
kubectl exec -n sentinel deploy/sentinel-postgres-0 -- \
  psql -U sentinel -c "SELECT pid, now() - pg_stat_activity.query_start AS duration, query, state FROM pg_stat_activity WHERE state != 'idle' ORDER BY duration DESC LIMIT 10;"
```

---

## Remediation

### Option A — DB circuit breaker is open (replay queue filling)
1. Restore PostgreSQL
2. Circuit breaker moves to half-open after 30s → auto-recovers
3. Monitor: replay queue drains at 100 msg / 30s; Kafka lag drains as consumers catch up

### Option B — Genuine throughput overload (lag growing despite 3 replicas)
Increase Kafka partitions to allow more parallelism:
```bash
kubectl exec -n sentinel deploy/sentinel-kafka-0 -- \
  /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 \
  --alter --topic telemetry.raw --partitions 9
kubectl scale deployment sentinel-backend -n sentinel --replicas=9
```
**Warning:** Increasing partitions is irreversible. Plan for this in advance.

### Option C — Unprocessable messages filling the topic
If a firmware bug is producing malformed payloads that repeatedly fail:
```bash
# Check DLQ size
kubectl exec -n sentinel deploy/sentinel-kafka-0 -- \
  /opt/kafka/bin/kafka-run-class.sh kafka.tools.GetOffsetShell \
  --broker-list localhost:9092 --topic telemetry.dlq --time -1
```
If DLQ is growing exponentially, consider temporarily pausing the affected device group
via the API: `PATCH /api/v1/devices/{id}/lifecycle` → `INACTIVE`.

---

## Escalation

If lag is not decreasing within **10 minutes** of scaling:
1. Page the database on-call (likely a PostgreSQL issue)
2. Consider pausing ingest temporarily by scaling MQTT broker connections
3. Open war room in incident Slack channel

See also: [Kafka lag warning runbook](./kafka-lag.md)
