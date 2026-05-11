# Chaos Testing

Chaos experiments verify that the platform's resilience mechanisms (circuit breakers,
retries, KEDA autoscaling, Redis pub/sub failover) behave as documented under real failure conditions.

Run these experiments in a **staging environment** before promoting to production.
Never run destructive experiments against production without a maintenance window and
an incident commander on standby.

---

## Prerequisites

```bash
# Required tools
kubectl version            # Kubernetes access
helm version               # Helm 3.x
chaos-mesh version         # Chaos Mesh (or Litmus) installed in staging namespace

# Confirm staging is healthy before starting
kubectl get pods -n sentinel-staging
curl http://sentinel-staging.internal/actuator/health | jq .status
```

---

## Experiment 1 — PostgreSQL Unavailability

**Hypothesis:** When PostgreSQL is unavailable, the backend circuit breaker opens,
telemetry is buffered in the Redis replay queue, and no data is permanently lost.
After DB recovery, the replay queue drains automatically.

**Procedure:**
```bash
# 1. Establish baseline: confirm lag=0 and replay queue is empty
kubectl exec -n sentinel-staging deploy/sentinel-backend -- \
  wget -qO- http://localhost:8080/actuator/metrics/sentinel.replay.queue.size

# 2. Kill PostgreSQL
kubectl scale statefulset sentinel-postgres -n sentinel-staging --replicas=0

# 3. Send 100 telemetry events via the simulator
kubectl exec -n sentinel-staging deploy/sentinel-simulator -- \
  node index.js --burst 100

# 4. Verify circuit breaker opened
kubectl exec -n sentinel-staging deploy/sentinel-backend -- \
  wget -qO- http://localhost:8080/actuator/health | jq .components.circuitBreakers

# 5. Verify replay queue is filling (not dropping messages)
kubectl exec -n sentinel-staging deploy/sentinel-backend -- \
  wget -qO- http://localhost:8080/actuator/metrics/sentinel.replay.queue.size

# 6. Restore PostgreSQL
kubectl scale statefulset sentinel-postgres -n sentinel-staging --replicas=1

# 7. Wait 60 seconds — circuit breaker half-opens, then closes
sleep 60

# 8. Verify replay queue drained to 0 and all 100 events are in the DB
kubectl exec -n sentinel-staging deploy/sentinel-postgres-0 -- \
  psql -U sentinel -c "SELECT COUNT(*) FROM telemetry WHERE timestamp > NOW() - INTERVAL '5 minutes';"
```

**Pass criteria:**
- No HTTP 500 errors during DB outage (circuit breaker returns graceful fallback)
- All 100 events appear in DB after recovery
- Replay queue size returns to 0 within 3 minutes of DB recovery

---

## Experiment 2 — Redis Unavailability

**Hypothesis:** When Redis is unavailable, the backend logs a warning but continues
processing MQTT messages. WebSocket broadcasts fail silently (data is still persisted to DB).
After Redis recovery, WebSocket broadcasting resumes automatically.

**Procedure:**
```bash
# 1. Kill Redis
kubectl scale statefulset sentinel-redis -n sentinel-staging --replicas=0

# 2. Publish 10 telemetry events
kubectl exec -n sentinel-staging deploy/sentinel-simulator -- \
  node index.js --burst 10

# 3. Confirm events are persisted to DB despite Redis being down
kubectl exec -n sentinel-staging deploy/sentinel-postgres-0 -- \
  psql -U sentinel -c "SELECT COUNT(*) FROM telemetry WHERE timestamp > NOW() - INTERVAL '1 minute';"

# 4. Confirm no ERROR logs for telemetry processing (only WARN for Redis timeout)
kubectl logs -n sentinel-staging -l app=sentinel-backend --tail=50 | grep -E "ERROR|WARN"

# 5. Restore Redis
kubectl scale statefulset sentinel-redis -n sentinel-staging --replicas=1

# 6. Verify WebSocket broadcast resumes (connect a browser and observe live updates)
```

**Pass criteria:**
- All 10 events persisted to DB (Redis failure must not cause data loss)
- Backend logs show WARN (Redis timeout) but no ERROR for message processing
- WebSocket resumes delivering updates within 30 seconds of Redis recovery

---

## Experiment 3 — Random Pod Termination (Chaos Monkey)

**Hypothesis:** Killing a random backend pod causes no user-visible errors beyond
the Kubernetes restart window (~5 seconds). The HPA maintains replica count.

**Procedure:**
```bash
# Install Chaos Mesh (if not present)
helm install chaos-mesh chaos-mesh/chaos-mesh -n chaos-testing --create-namespace

# Define pod-kill experiment
cat <<EOF | kubectl apply -f -
apiVersion: chaos-mesh.org/v1alpha1
kind: PodChaos
metadata:
  name: sentinel-backend-pod-kill
  namespace: sentinel-staging
spec:
  action: pod-kill
  mode: one
  selector:
    namespaces: [sentinel-staging]
    labelSelectors:
      app: sentinel-backend
  scheduler:
    cron: "@every 2m"
EOF

# Run for 10 minutes while monitoring error rate
watch -n 5 "curl -s http://sentinel-staging.internal/api/v1/devices | jq length"

# Check Prometheus for 5xx spikes during pod kills
curl -sG http://prometheus-staging:9090/api/v1/query \
  --data-urlencode 'query=rate(http_server_requests_seconds_count{status=~"5.."}[1m])'

# Cleanup
kubectl delete podchaos sentinel-backend-pod-kill -n sentinel-staging
```

**Pass criteria:**
- No sustained 5xx errors (brief spike < 2 seconds during pod restart is acceptable)
- HPA restores replica count within 60 seconds
- No Kafka consumer group rebalance errors in logs

---

## Experiment 4 — Network Partition (Backend ↔ Kafka)

**Hypothesis:** If the network between the backend and Kafka is severed, the MQTT
ingest path degrades gracefully. Messages already consumed are not reprocessed after reconnection.

**Procedure:**
```bash
# Apply network chaos — drop all traffic from backend pods to Kafka
cat <<EOF | kubectl apply -f -
apiVersion: chaos-mesh.org/v1alpha1
kind: NetworkChaos
metadata:
  name: backend-kafka-partition
  namespace: sentinel-staging
spec:
  action: partition
  mode: all
  selector:
    namespaces: [sentinel-staging]
    labelSelectors:
      app: sentinel-backend
  direction: to
  target:
    mode: all
    selector:
      namespaces: [sentinel-staging]
      labelSelectors:
        app: sentinel-kafka
  duration: "2m"
EOF

# Monitor Kafka consumer lag (should stop increasing as backend cannot consume)
kubectl exec -n sentinel-staging deploy/sentinel-kafka-0 -- \
  /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
  --describe --group sentinel-telemetry-ingest

# After 2 minutes, partition lifts automatically — verify offsets resume from where they stopped
kubectl exec -n sentinel-staging deploy/sentinel-kafka-0 -- \
  /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
  --describe --group sentinel-telemetry-ingest

kubectl delete networkchaos backend-kafka-partition -n sentinel-staging
```

**Pass criteria:**
- Consumer lag builds during partition, then drains after recovery
- No duplicate telemetry records in PostgreSQL (verify with `SELECT device_id, COUNT(*) FROM telemetry GROUP BY device_id, timestamp HAVING COUNT(*) > 1`)
- No messages lost (compare Kafka offset advancement with DB insert count)

---

## Experiment 5 — MQTT Broker Restart

**Hypothesis:** When Mosquitto restarts, the backend auto-reconnects and resumes consuming.
In-flight MQTT messages (QoS 1) are re-delivered after reconnection.

**Procedure:**
```bash
# Note current Kafka offset before restart
kubectl exec -n sentinel-staging deploy/sentinel-kafka-0 -- \
  /opt/kafka/bin/kafka-run-class.sh kafka.tools.GetOffsetShell \
  --broker-list localhost:9092 --topic telemetry.raw --time -1

# Kill Mosquitto
kubectl delete pod -n sentinel-staging -l app=sentinel-mosquitto

# Send messages from simulator while Mosquitto is restarting
kubectl exec -n sentinel-staging deploy/sentinel-simulator -- \
  node index.js --burst 20 --retry

# After Mosquitto restarts (< 30 seconds), confirm messages were delivered
kubectl exec -n sentinel-staging deploy/sentinel-kafka-0 -- \
  /opt/kafka/bin/kafka-run-class.sh kafka.tools.GetOffsetShell \
  --broker-list localhost:9092 --topic telemetry.raw --time -1
# Offset should have advanced by ~20
```

**Pass criteria:**
- Backend reconnects to MQTT within 30 seconds (Spring Integration auto-reconnect)
- All 20 QoS 1 messages delivered (none lost during reconnect window)

---

## Experiment Results Log

| Date | Experiment | Environment | Pass/Fail | Notes |
|------|-----------|-------------|-----------|-------|
| | DB Unavailability | staging | | |
| | Redis Unavailability | staging | | |
| | Pod Kill | staging | | |
| | Network Partition | staging | | |
| | MQTT Restart | staging | | |

Update this table after each run. Failed experiments must be tracked as bugs before
promoting the release to production.
