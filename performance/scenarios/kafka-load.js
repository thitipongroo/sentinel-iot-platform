/**
 * Kafka / MQTT Ingestion Load — Test Cases 1.2 + 2.4
 *
 * 1.2  Kafka Telemetry Ingestion Throughput
 *   1.2.1  500 msg/sec sustained   → consumer lag < 5,000 msg
 *   1.2.2  1,000 msg/sec sustained → consumer lag < 10,000 msg
 *   1.2.3  DLQ rate at peak        → DLQ rate < 0.5 %
 *   1.2.4  JDBC batch efficiency   → verified via Prometheus (not k6 metric)
 *
 * 2.4  Kafka Consumer Load Test
 *   2.4.1  2,000 msg/sec           → consumer lag < 20,000 msg
 *   2.4.2  Partition imbalance     → verified via Kafka CLI (note below)
 *   2.4.3  Consumer recovery       → manual: stop consumer 60 s then time catch-up
 *   2.4.4  DLQ under load (5 % malformed messages)
 *   2.4.5  JDBC batch insert rate  → verified via pg_stat_user_tables
 *
 * Requires: xk6-mqtt extension
 *   Build: xk6 build --with github.com/grafana/xk6-mqtt
 *   Docs:  https://github.com/grafana/xk6-mqtt
 *
 * Run (target 500 msg/sec):
 *   ./k6 run performance/scenarios/kafka-load.js -e RATE=500
 *
 * Run (target 1,000 msg/sec, test 1.2.2 / 2.4.1):
 *   ./k6 run performance/scenarios/kafka-load.js -e RATE=2000
 *
 * Run (with 5 % malformed messages, test 1.2.3 / 2.4.4):
 *   ./k6 run performance/scenarios/kafka-load.js -e DLQ_TEST=true
 *
 * Post-run checks (manual — k6 cannot query Kafka directly):
 *   Consumer lag:  kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
 *                    --describe --group sentinel-telemetry-ingest
 *   DB insert rate: psql -c "SELECT n_tup_ins FROM pg_stat_user_tables WHERE relname='telemetry'"
 *   DLQ depth:     kafka-console-consumer.sh --topic factory/telemetry/dlq --from-beginning --max-messages 10
 */

import mqtt from 'k6/x/mqtt'
import { check, sleep } from 'k6'
import { Counter, Rate, Trend } from 'k6/metrics'

// ── Configuration ─────────────────────────────────────────────────────────────

const BROKER_URL   = __ENV.MQTT_BROKER || 'localhost:1883'
const MQTT_TOPIC   = __ENV.MQTT_TOPIC  || 'factory/telemetry'
const MQTT_USER    = __ENV.MQTT_USER   || ''
const MQTT_PASS    = __ENV.MQTT_PASS   || ''
const TARGET_RATE  = parseInt(__ENV.RATE    || '500')   // msg/sec total
const DLQ_TEST     = __ENV.DLQ_TEST === 'true'          // inject 5 % malformed

// ── Custom metrics ────────────────────────────────────────────────────────────

const publishedCount  = new Counter('mqtt_published_total')
const dlqCount        = new Counter('mqtt_dlq_injected_total')   // intentionally malformed
const publishLatency  = new Trend('mqtt_publish_latency_ms', true)
const publishRate     = new Rate('mqtt_publish_success_rate')

// ── k6 options ────────────────────────────────────────────────────────────────

// Total VUs × iterations-per-second ≈ TARGET_RATE
// At 1 msg per VU per second, VU count = TARGET_RATE.
const VUS = TARGET_RATE

export const options = {
  scenarios: {
    mqtt_publishers: {
      executor:         'constant-arrival-rate',
      rate:             TARGET_RATE,
      timeUnit:         '1s',
      duration:         '3m',        // 3 min sustained (covers 1.2.1 – 1.2.3 validation window)
      preAllocatedVUs:  Math.ceil(VUS / 10),
      maxVUs:           VUS * 2,
    },
  },
  thresholds: {
    mqtt_publish_success_rate: ['rate>=0.995'],  // < 0.5 % publish failures (1.2.3)
    mqtt_publish_latency_ms:   ['p(95)<100'],    // publish round-trip P95 < 100 ms
  },
}

// ── Helpers ───────────────────────────────────────────────────────────────────

const DEVICES = (__ENV.DEVICES || 'sensor-1,sensor-2,sensor-3').split(',')

function pickDevice() {
  return DEVICES[Math.floor(Math.random() * DEVICES.length)]
}

function buildValidPayload(deviceId) {
  const tempSpike  = Math.random() < 0.05
  const smokeSpike = Math.random() < 0.03
  return JSON.stringify({
    deviceId,
    temperature: tempSpike  ? (81  + Math.random() * 14).toFixed(2) : (60 + Math.random() * 18).toFixed(2),
    humidity:    (35 + Math.random() * 50).toFixed(2),
    motion:      Math.random() < 0.20,
    smokePpm:    smokeSpike ? (201 + Math.random() * 149).toFixed(2) : (5 + Math.random() * 45).toFixed(2),
    timestamp:   Date.now(),
  })
}

// 1.2.3 / 2.4.4: intentionally malformed — triggers DLQ path in MqttConsumerService
function buildMalformedPayload() {
  return `{not valid json ${Date.now()}`
}

// ── VU lifecycle ──────────────────────────────────────────────────────────────

let client

export function setup() {
  // Validate broker connectivity once before starting the load
  const probe = new mqtt.Client(
    [BROKER_URL],
    MQTT_USER, MQTT_PASS,
    false,
    `k6-probe-${Date.now()}`,
    true,
    false, null, 5
  )
  probe.connect()
  probe.close()
  console.log(`[kafka-load] Broker ${BROKER_URL} reachable. Target rate: ${TARGET_RATE} msg/sec. DLQ injection: ${DLQ_TEST}`)
}

export default function () {
  // Each VU creates its own MQTT client (persistent connection)
  if (!client) {
    client = new mqtt.Client(
      [BROKER_URL],
      MQTT_USER, MQTT_PASS,
      false,
      `k6-${__VU}-${Date.now()}`,
      true,
      false, null, 5
    )
    client.connect()
  }

  const deviceId  = pickDevice()
  // 2.4.4: inject 5 % malformed messages when DLQ_TEST=true
  const isMalformed = DLQ_TEST && Math.random() < 0.05
  const payload   = isMalformed ? buildMalformedPayload() : buildValidPayload(deviceId)

  const start = Date.now()
  try {
    client.publish(MQTT_TOPIC, payload, 1, false)
    const latency = Date.now() - start
    publishLatency.add(latency)
    publishedCount.add(1)
    publishRate.add(true)
    if (isMalformed) dlqCount.add(1)
    check(latency, { 'publish < 100 ms': l => l < 100 })
  } catch (e) {
    publishRate.add(false)
    console.warn(`[VU ${__VU}] MQTT publish error: ${e}`)
  }
}

export function teardown() {
  if (client) {
    try { client.close() } catch (_) {}
  }
}

// ── Summary ───────────────────────────────────────────────────────────────────

export function handleSummary(data) {
  const published = data.metrics.mqtt_published_total?.values?.count ?? 0
  const dlq       = data.metrics.mqtt_dlq_injected_total?.values?.count ?? 0
  const p95pub    = data.metrics.mqtt_publish_latency_ms?.values?.['p(95)']?.toFixed(1) ?? 'n/a'
  const successPct = ((data.metrics.mqtt_publish_success_rate?.values?.rate ?? 0) * 100).toFixed(2)

  console.log('\n=== Kafka/MQTT Load Test Summary (1.2 + 2.4) ===')
  console.log(`Messages published:    ${published}`)
  console.log(`DLQ injected (5 %):    ${dlq}`)
  console.log(`Publish P95 latency:   ${p95pub} ms`)
  console.log(`Publish success rate:  ${successPct} %  (SLO ≥ 99.5 %)`)
  console.log('')
  console.log('Next steps (manual verification):')
  console.log('  Consumer lag:  kafka-consumer-groups.sh --describe --group sentinel-telemetry-ingest')
  console.log('  DB insert rate: SELECT n_tup_ins FROM pg_stat_user_tables WHERE relname=\'telemetry\'')
  console.log('  DLQ depth:     kafka-console-consumer.sh --topic factory/telemetry/dlq --from-beginning')
  console.log('=================================================\n')

  return {
    'performance/results/kafka-load.json': JSON.stringify(data, null, 2),
  }
}
