# Runbook: SentinelJVMHeapHigh

**Alert:** `SentinelJVMHeapHigh`
**Severity:** Warning
**Trigger:** JVM heap usage > 85% for 10 consecutive minutes

---

## Impact

High heap utilisation causes more frequent and longer GC pauses. Once heap reaches
~95%, the JVM enters emergency collection mode and p99 latency will spike above 500 ms.
An OOM error will crash the pod.

---

## Diagnosis

### 1. Confirm heap usage trend
```bash
curl -sG http://prometheus:9090/api/v1/query_range \
  --data-urlencode 'query=jvm_memory_used_bytes{area="heap",job="sentinel-backend"}/jvm_memory_max_bytes{area="heap",job="sentinel-backend"}' \
  --data-urlencode 'start=-30m' --data-urlencode 'step=1m'
```
- **Saw-tooth pattern** (rises then drops on GC): allocation rate is high but GC is working → add heap
- **Steadily rising line** (no drops): memory leak — GC cannot reclaim objects

### 2. Check allocation rate
```bash
curl -sG http://prometheus:9090/api/v1/query \
  --data-urlencode 'query=rate(jvm_gc_memory_allocated_bytes_total{job="sentinel-backend"}[5m])'
```

### 3. Check GC pause time
```bash
curl -sG http://prometheus:9090/api/v1/query \
  --data-urlencode 'query=rate(jvm_gc_pause_seconds_sum{job="sentinel-backend"}[5m])'
```

### 4. Identify large object allocations
If a memory leak is suspected, take a heap dump:
```bash
POD=$(kubectl get pods -n sentinel -l app=sentinel-backend -o jsonpath='{.items[0].metadata.name}')
kubectl exec -n sentinel "$POD" -- jmap -dump:format=b,file=/tmp/heapdump.hprof 1
kubectl cp "sentinel/$POD:/tmp/heapdump.hprof" ./heapdump.hprof
# Analyse with Eclipse MAT or VisualVM
```

Common leak sources in this codebase:
- `CopyOnWriteArraySet` in `TelemetryWebSocketHandler` growing without session cleanup
- Unclosed Kafka consumer iterators
- Accumulating entries in the Redis replay queue (check `replayQueueSize()`)

---

## Remediation

### Immediate: restart the affected pod
```bash
POD=$(kubectl get pods -n sentinel -l app=sentinel-backend -o jsonpath='{.items[0].metadata.name}')
kubectl delete pod "$POD" -n sentinel
# Kubernetes will reschedule a fresh pod; rolling restart ensures no downtime
```

### Increase heap size
```bash
kubectl set env deployment/sentinel-backend -n sentinel \
  JAVA_TOOL_OPTIONS="-Xmx2g -Xms512m"
```
Current default: unset (Spring Boot uses ~256 MB–512 MB depending on container limits).
The Helm chart `values.yaml` `backend.resources.limits.memory` must be ≥ heap + 256 MB overhead.

### Switch to ZGC for lower pause times
Add to Helm `values.yaml`:
```yaml
backend:
  env:
    JAVA_TOOL_OPTIONS: "-XX:+UseZGC -Xmx2g"
```
ZGC handles large heaps with sub-millisecond pause times, ideal for the WebSocket broadcast path.

---

## Prevention

- Set `resources.requests.memory` and `resources.limits.memory` in the Helm chart
  to force Kubernetes to schedule on nodes with sufficient RAM
- Enable heap dump on OOM in production: `-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp/`
- Monitor `jvm_memory_used_bytes` trend in the Grafana SLO dashboard
