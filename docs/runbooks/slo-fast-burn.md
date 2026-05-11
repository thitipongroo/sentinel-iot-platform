# Runbook: SentinelSLOFastBurn

**Alert:** `SentinelSLOFastBurn`
**Severity:** Critical (page immediately)
**Trigger:** 1-hour error budget burn rate > 14.4× (consuming ~2% of monthly budget per hour)

---

## Impact

At this burn rate the **entire monthly error budget will be exhausted within ~50 hours**.
Users are experiencing elevated HTTP 5xx errors. Availability SLO (99.9%) is at immediate risk.

---

## Diagnosis

### 1. Confirm the alert is real (not a metrics spike)

```bash
# Check current error ratio
kubectl exec -n sentinel deploy/sentinel-backend -- \
  wget -qO- http://localhost:8080/actuator/metrics/http.server.requests | jq .

# Query Prometheus for error rate
curl -sG http://prometheus:9090/api/v1/query \
  --data-urlencode 'query=sentinel:http_error_ratio:rate5m'
```

### 2. Identify which endpoint is failing

```bash
# Top error-generating endpoints in last 5 minutes
curl -sG http://prometheus:9090/api/v1/query \
  --data-urlencode 'query=topk(5, rate(http_server_requests_seconds_count{job="sentinel-backend",status=~"5.."}[5m]))'
```

### 3. Check backend pod health

```bash
kubectl get pods -n sentinel -l app=sentinel-backend
kubectl logs -n sentinel -l app=sentinel-backend --tail=100 | grep -E "ERROR|WARN"
```

### 4. Check downstream dependencies

```bash
# PostgreSQL connectivity
kubectl exec -n sentinel deploy/sentinel-backend -- \
  wget -qO- http://localhost:8080/actuator/health | jq .components.db

# Redis connectivity
kubectl exec -n sentinel deploy/sentinel-backend -- \
  wget -qO- http://localhost:8080/actuator/health | jq .components.redis

# Kafka consumer lag
kubectl exec -n sentinel deploy/sentinel-kafka-0 -- \
  /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
  --describe --group sentinel-telemetry-ingest
```

### 5. Check recent deployments

```bash
kubectl rollout history deployment/sentinel-backend -n sentinel
kubectl rollout history argo-rollouts/sentinel-backend -n sentinel
```

---

## Remediation

### Option A — Rollback (if recent deploy is the cause)

```bash
# Argo Rollouts blue/green: abort promotion and revert to stable
kubectl argo rollouts abort sentinel-backend -n sentinel
kubectl argo rollouts undo sentinel-backend -n sentinel
```

### Option B — Scale out (if under load)

```bash
# Increase replicas temporarily
kubectl scale deployment sentinel-backend -n sentinel --replicas=6

# Or update HPA max
kubectl patch hpa sentinel-backend-hpa -n sentinel \
  -p '{"spec":{"maxReplicas":20}}'
```

### Option C — Circuit breaker / dependency failure

If a downstream service (PostgreSQL, Redis) is failing:

1. Check if the circuit breaker has opened: `GET /actuator/health` → `circuitBreakers`
2. Restore the failing dependency
3. The circuit breaker will half-open and recover automatically (30s window)

---

## Escalation

If not resolved within **15 minutes**:

- Page the on-call database engineer if PostgreSQL errors
- Page the infrastructure team if Kubernetes node issues
- Consider [enabling maintenance mode](../architecture.md#maintenance-mode) to stop user-facing errors

---

## Post-incident

File an incident report referencing: error budget consumed, root cause, and follow-up tasks.
See [incident flow runbook](./incident-flow.md) for the full post-mortem template.
