# Runbook: SentinelTelemetryLagHigh

**Alert:** `SentinelTelemetryLagHigh`
**Severity:** Warning
**Trigger:** Consumer group `sentinel-telemetry-ingest` lag on `telemetry.raw` > 10,000 messages

---

## Impact

The backend is processing telemetry slower than devices are publishing it.
**Telemetry E2E latency SLO (99.5% of messages ingested within 5 seconds) is at risk.**
Alert evaluations and WebSocket dashboard updates will be delayed.
At 10,000 messages of lag and 40 events/sec (baseline), data is ~250 seconds behind.

---

## Diagnosis

### 1. Confirm lag and identify which partition is affected

```bash
kubectl exec -n sentinel deploy/sentinel-kafka-0 -- \
  /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --describe --group sentinel-telemetry-ingest
```

Look at `LAG` per partition. Uneven lag (one partition much higher) suggests a single
slow consumer instance, not an overall throughput problem.

### 2. Check KEDA scaling status

```bash
kubectl get scaledobject -n sentinel
kubectl describe scaledobject sentinel-backend-keda -n sentinel
# Expected: KEDA should have already scaled up replicas to drain the lag
```

### 3. Check consumer throughput

```bash
curl -sG http://prometheus:9090/api/v1/query \
  --data-urlencode 'query=rate(kafka_consumer_records_consumed_total{group="sentinel-telemetry-ingest"}[5m])'
```

### 4. Check for consumer errors (DLQ growth)

```bash
kubectl exec -n sentinel deploy/sentinel-kafka-0 -- \
  /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 \
  --describe --topic telemetry.dlq
```

If `telemetry.dlq` is growing, the consumer is failing to process messages (not just slow).

---

## Remediation

### Option A — KEDA has not scaled yet (wait 1-2 minutes)

KEDA's `lagThreshold` is 500 (prod). The ScaledObject should trigger a scale-up within
the polling interval. Verify HPA target replica count:

```bash
kubectl get hpa -n sentinel
```

### Option B — Manually scale up consumers

```bash
kubectl scale deployment sentinel-backend -n sentinel --replicas=6
```

With 3 Kafka partitions, up to 3 replicas can actively consume in parallel.
Additional replicas above 3 will be idle consumers (no extra throughput gain without
increasing partition count).

### Option C — Consumer is stuck (DLQ growing, not just slow)

1. Check consumer logs for repeated exceptions:

   ```bash
   kubectl logs -n sentinel -l app=sentinel-backend --tail=200 | grep -E "ERROR|KafkaConsumer"
   ```

2. If a poison pill message is blocking a partition, it will retry 3× then go to DLQ.
   Check DLQ contents and confirm the dead-letter consumer (`TelemetryDlqConsumer`) is running.

### Option D — Increase Kafka partitions (longer-term fix)

The current `telemetry.raw` topic has 3 partitions — max parallelism is 3 consumer instances.
To scale further:

```bash
kubectl exec -n sentinel deploy/sentinel-kafka-0 -- \
  /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 \
  --alter --topic telemetry.raw --partitions 9
# Then update KafkaConfig.java NUM_PARTITIONS constant and redeploy
```

---

## Notes

KEDA `activationLagThreshold` is 50 messages. Below that, KEDA scales to 0 replicas
(if enabled). Above 50 it scales based on `lagThreshold` (500 per replica in prod).
The fast-path remedy is always to verify KEDA acted first before manually scaling.
