# Failure Testing Checklist

This checklist verifies that each documented failure scenario in the Sentinel IoT Platform
behaves as specified. Run this checklist before every production release and after
significant infrastructure changes.

---

## How to Use

For each scenario:
1. Ensure the staging environment is healthy (all pods running, lag = 0)
2. Trigger the failure using the provided command
3. Verify the expected behaviour within the stated timeout
4. Restore the environment
5. Record the result (Pass ✅ / Fail ❌) with date and version

---

## Failure Scenario 1 — Database Unavailable

**Expected behaviour (from architecture.md):**
- `@CircuitBreaker(name="telemetryDB")` opens after 50% failure rate (sliding window: 10 calls)
- Backend returns fallback response to Kafka consumer (message goes to replay queue)
- `sentinel:replay:queue` fills up to max 10,000 messages
- After DB recovery, circuit breaker half-opens → messages drain (100/batch every 30s)
- No permanent data loss

**Trigger:**
```bash
kubectl scale statefulset sentinel-postgres -n sentinel-staging --replicas=0
```

**Verification steps:**
```bash
# Wait 30 seconds, then check circuit breaker state
curl http://sentinel-staging.internal/actuator/health | jq '.components.circuitBreakers'
# Expected: { "sentinelDB": { "status": "CIRCUIT_OPEN" } }

# Confirm replay queue is growing, not dropping
curl http://sentinel-staging.internal/actuator/metrics/sentinel.replay.queue.size
# Expected: increasing value up to 10000

# Restore
kubectl scale statefulset sentinel-postgres -n sentinel-staging --replicas=1

# After 60s: verify replay queue drains
curl http://sentinel-staging.internal/actuator/metrics/sentinel.replay.queue.size
# Expected: value decreasing toward 0
```

**Pass criteria:** Circuit breaker opened ✓ | No data loss ✓ | Queue drained ✓

| Date | Version | Result | Notes |
|------|---------|--------|-------|
| | | | |

---

## Failure Scenario 2 — Redis Unavailable

**Expected behaviour:**
- Backend logs `WARN` for Redis timeout (2000 ms)
- Telemetry is persisted to PostgreSQL regardless
- WebSocket broadcasts fail silently (no ERROR, no user-visible data loss)
- Device status cache misses gracefully (returns null / empty)
- Replay queue operations degrade: `pushToReplayQueue` / `drainReplayQueue` throw, caught in `ReplayQueueService`

**Trigger:**
```bash
kubectl scale statefulset sentinel-redis -n sentinel-staging --replicas=0
```

**Verification steps:**
```bash
# Send telemetry events
curl -X POST http://sentinel-staging.internal/api/v1/devices/test/telemetry \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"temperature":25.0,"humidity":60.0}'

# Confirm DB persistence (not Redis)
kubectl exec -n sentinel-staging deploy/sentinel-postgres-0 -- \
  psql -U sentinel -c "SELECT COUNT(*) FROM telemetry WHERE timestamp > NOW() - INTERVAL '1 minute';"
# Expected: count > 0

# Check backend logs for WARN only, no ERROR for telemetry path
kubectl logs -n sentinel-staging -l app=sentinel-backend --tail=100 | grep -v "^$"
# Expected: WARN lines for Redis timeout; no ERROR for telemetry save

# Restore
kubectl scale statefulset sentinel-redis -n sentinel-staging --replicas=1
```

**Pass criteria:** DB writes continue ✓ | No ERROR logs for telemetry ✓ | WebSocket reconnects after restore ✓

| Date | Version | Result | Notes |
|------|---------|--------|-------|
| | | | |

---

## Failure Scenario 3 — MQTT Disconnection

**Expected behaviour (from MqttConsumerService):**
- Spring Integration detects TCP disconnect
- Auto-reconnect with exponential backoff (built into `DefaultMqttPahoClientFactory`)
- Reconnects within 30 seconds
- QoS 1 in-flight messages are re-delivered after reconnection (no loss)

**Trigger:**
```bash
kubectl delete pod -n sentinel-staging -l app=sentinel-mosquitto
```

**Verification steps:**
```bash
# Watch backend logs for reconnect sequence
kubectl logs -n sentinel-staging -l app=sentinel-backend -f | grep -i "mqtt"
# Expected within 30s:
#   WARN  MqttConsumerService - MQTT connection lost: ...
#   INFO  MqttConsumerService - MQTT reconnected successfully

# Send 5 messages immediately after kill (before reconnect)
# Messages should queue in the simulator's MQTT client

# After reconnect, verify all messages appear in Kafka
kubectl exec -n sentinel-staging deploy/sentinel-kafka-0 -- \
  /opt/kafka/bin/kafka-run-class.sh kafka.tools.GetOffsetShell \
  --broker-list localhost:9092 --topic telemetry.raw --time -1
```

**Pass criteria:** Reconnect within 30s ✓ | No QoS 1 message loss ✓ | No ERROR after reconnect ✓

| Date | Version | Result | Notes |
|------|---------|--------|-------|
| | | | |

---

## Failure Scenario 4 — Invalid Payload (Poison Pill)

**Expected behaviour:**
- `SchemaCompatibilityService.validate()` rejects malformed payload
- `KafkaTelemetryConsumer` catches exception after 3 retries
- `DeadLetterPublishingRecoverer` routes the message to `telemetry.dlq`
- `TelemetryDlqConsumer` logs the rejected message
- Kafka consumer offset advances (no blocking of subsequent messages)

**Trigger:**
```bash
# Publish a syntactically invalid JSON payload directly to Kafka
kubectl exec -n sentinel-staging deploy/sentinel-kafka-0 -- \
  /opt/kafka/bin/kafka-console-producer.sh \
  --bootstrap-server localhost:9092 --topic telemetry.raw <<'EOF'
{"deviceId":"bad-device","temperature":"not-a-number","timestamp":"invalid"}
EOF
```

**Verification steps:**
```bash
# Confirm message appears in DLQ after 3 retries (~3 seconds)
kubectl exec -n sentinel-staging deploy/sentinel-kafka-0 -- \
  /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 --topic telemetry.dlq \
  --from-beginning --max-messages 5

# Confirm valid messages after the poison pill are still processed
kubectl exec -n sentinel-staging deploy/sentinel-kafka-0 -- \
  /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --describe --group sentinel-telemetry-ingest
# Expected: LAG=0 (consumer caught up after routing poison pill to DLQ)
```

**Pass criteria:** Message in DLQ ✓ | No consumer offset stall ✓ | Subsequent valid messages processed ✓

| Date | Version | Result | Notes |
|------|---------|--------|-------|
| | | | |

---

## Failure Scenario 5 — Access Token Used After Logout

**Expected behaviour:**
- After `POST /api/v1/auth/logout`, the access token JTI is added to the Redis blocklist
- Any subsequent request using that token returns `401 Unauthorized` immediately
- The blocklist entry auto-expires after the token's remaining lifetime (max 15 minutes)

**Trigger:**
```bash
TOKEN=$(curl -s -X POST http://sentinel-staging.internal/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' | jq -r .accessToken)

curl -s -X POST http://sentinel-staging.internal/api/v1/auth/logout \
  -H "Authorization: Bearer $TOKEN"
# Expected: 204 No Content

curl -s http://sentinel-staging.internal/api/v1/devices \
  -H "Authorization: Bearer $TOKEN"
# Expected: 401 Unauthorized
```

**Pass criteria:** Token rejected with 401 immediately after logout ✓

| Date | Version | Result | Notes |
|------|---------|--------|-------|
| | | | |

---

## Failure Scenario 6 — JWT Key Rotation (Zero-Downtime)

**Expected behaviour:**
- During rotation, tokens signed with the old key remain valid
- After rotation window, old-key tokens are rejected
- No user sessions are interrupted during rotation

**Trigger:**
```bash
# Get a token with the current key
OLD_SECRET=$(kubectl get secret sentinel-jwt -n sentinel-staging -o jsonpath='{.data.JWT_SECRET}' | base64 -d)
TOKEN=$(curl -s -X POST http://sentinel-staging.internal/api/v1/auth/login \
  -d '{"username":"admin","password":"admin123"}' | jq -r .accessToken)

# Rotate: set previous = old, current = new
kubectl patch secret sentinel-jwt -n sentinel-staging \
  --type=merge -p "{\"data\":{\"JWT_PREVIOUS_SECRET\":\"$(echo -n "$OLD_SECRET" | base64)\",\"JWT_SECRET\":\"$(openssl rand -base64 32 | base64)\"}}"
kubectl rollout restart deployment/sentinel-backend -n sentinel-staging
kubectl rollout status deployment/sentinel-backend -n sentinel-staging

# Old token must still work (signed with previous key)
curl -s http://sentinel-staging.internal/api/v1/devices \
  -H "Authorization: Bearer $TOKEN"
# Expected: 200 OK
```

**Pass criteria:** Old token accepted during rotation window ✓ | New tokens use new key ✓

| Date | Version | Result | Notes |
|------|---------|--------|-------|
| | | | |

---

## Sign-off

Before each production release, all 6 scenarios must show a passing result dated
within the last 30 days. Failures must have a linked bug ticket and workaround documented.

| Release | Tester | All pass? | Date |
|---------|--------|-----------|------|
| | | | |
