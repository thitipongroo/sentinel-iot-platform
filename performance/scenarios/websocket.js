/**
 * WebSocket Broadcast Performance — Test Cases 1.4
 *
 * 1.4.1  Broadcast to 100 concurrent clients  → P95 delivery latency < 150 ms
 * 1.4.2  Broadcast to 500 concurrent clients  → P95 delivery latency < 300 ms
 * 1.4.3  Cross-instance broadcast via Redis pub/sub (structural check — all clients receive)
 *
 * How latency is measured:
 *   The MQTT publisher embeds `sentAt` (epoch ms) in the telemetry payload.
 *   The Kafka consumer broadcasts this payload verbatim to all WebSocket clients.
 *   Each receiver computes `receivedAt - sentAt` as the end-to-end broadcast latency.
 *
 * Prerequisites:
 *   1. The MQTT simulator (or kafka-load.js) must be running in a separate terminal
 *      to generate telemetry that the backend broadcasts via WebSocket.
 *   2. Backend must be up with WebSocket endpoint at /ws/telemetry.
 *
 * Run (100 clients — 1.4.1):
 *   k6 run performance/scenarios/websocket.js -e WS_CLIENTS=100 -e DURATION=5m
 *
 * Run (500 clients — 1.4.2):
 *   k6 run performance/scenarios/websocket.js -e WS_CLIENTS=500 -e DURATION=5m
 *
 * Environment variables:
 *   WS_URL      default ws://localhost:8080/ws/telemetry
 *   WS_CLIENTS  default 100
 *   DURATION    default 5m
 *   BASE_URL    default http://localhost:8080
 *   USERNAME    default admin
 *   PASSWORD    default admin123
 */

import ws from 'k6/ws'
import { check, sleep } from 'k6'
import { Counter, Gauge, Rate, Trend } from 'k6/metrics'
import { login, BASE_URL } from '../common/auth.js'
import { buildWsThresholds } from '../common/thresholds.js'

// ── Configuration ─────────────────────────────────────────────────────────────

const WS_URL    = __ENV.WS_URL     || 'ws://localhost:8080/ws/telemetry'
const WS_VUS    = parseInt(__ENV.WS_CLIENTS || '100')
const DURATION  = __ENV.DURATION   || '5m'

// ── Custom metrics ────────────────────────────────────────────────────────────

const broadcastLatency  = new Trend('ws_broadcast_latency_ms', true)   // end-to-end: sentAt → receivedAt
const messagesReceived  = new Counter('ws_msgs_received')
const connectionsActive = new Gauge('ws_connections_active')
const connectErrors     = new Counter('ws_connect_errors')
const successRate       = new Rate('success_rate')

// ── k6 options ────────────────────────────────────────────────────────────────

export const options = {
  scenarios: {
    ws_receivers: {
      executor:         'constant-vus',
      vus:              WS_VUS,
      duration:         DURATION,
      gracefulStop:     '15s',
    },
  },
  thresholds: {
    ...buildWsThresholds(),
    // Override P95 based on client count
    ws_broadcast_latency_ms: WS_VUS <= 100
      ? ['p(95)<150', 'p(99)<300']      // 1.4.1 — 100 clients
      : ['p(95)<300', 'p(99)<500'],     // 1.4.2 — 500 clients
    ws_connect_errors: ['count<5'],     // tolerate very few connect failures
  },
}

// ── Setup ─────────────────────────────────────────────────────────────────────

export function setup() {
  const token = login(__ENV.USERNAME || 'admin', __ENV.PASSWORD || 'admin123')
  console.log(`[websocket] ${WS_VUS} clients connecting to ${WS_URL}`)
  return { token }
}

// ── VU function ───────────────────────────────────────────────────────────────

export default function (data) {
  // Attach JWT via query parameter (Spring Security WebSocket handshake)
  const url = `${WS_URL}?token=${data.token}`

  const res = ws.connect(url, { tags: { endpoint: 'ws-telemetry' } }, function (socket) {
    connectionsActive.add(1)

    socket.on('open', () => {
      check(true, { 'ws connected': () => true })
      successRate.add(true)
    })

    socket.on('message', (raw) => {
      messagesReceived.add(1)

      // Measure broadcast latency when the payload carries a sentAt timestamp
      try {
        const msg = JSON.parse(raw)
        if (msg.sentAt) {
          const latency = Date.now() - msg.sentAt
          broadcastLatency.add(latency)
          check(latency, {
            'broadcast < 150 ms (1.4.1)': l => WS_VUS > 100 || l < 150,
            'broadcast < 300 ms (1.4.2)': l => WS_VUS <= 100 || l < 300,
          })
        }
        // 1.4.3: cross-instance check — verify message has expected schema
        check(msg, { 'message has deviceId': m => !!m.deviceId })
      } catch (_) {
        // Non-JSON control frames — ignore
      }
    })

    socket.on('error', (e) => {
      connectErrors.add(1)
      successRate.add(false)
      console.warn(`[VU ${__VU}] WebSocket error: ${e.error()}`)
    })

    socket.on('close', () => {
      connectionsActive.add(-1)
    })

    // Hold the connection open for the full VU duration; close cleanly at the end
    socket.setTimeout(() => socket.close(), parseDurationMs(DURATION) - 5_000)
  })

  check(res, { 'ws status 101': r => r && r.status === 101 })
}

// ── Helpers ───────────────────────────────────────────────────────────────────

function parseDurationMs(s) {
  if (s.endsWith('s')) return parseInt(s) * 1_000
  if (s.endsWith('m')) return parseInt(s) * 60_000
  if (s.endsWith('h')) return parseInt(s) * 3_600_000
  return 300_000
}

// ── Summary ───────────────────────────────────────────────────────────────────

export function handleSummary(data) {
  const p95 = data.metrics.ws_broadcast_latency_ms?.values?.['p(95)']?.toFixed(1) ?? 'n/a (no sentAt timestamps received)'
  const p99 = data.metrics.ws_broadcast_latency_ms?.values?.['p(99)']?.toFixed(1) ?? 'n/a'
  const total = data.metrics.ws_msgs_received?.values?.count ?? 0
  const errs  = data.metrics.ws_connect_errors?.values?.count ?? 0
  const sloP95 = WS_VUS <= 100 ? 150 : 300

  console.log(`\n=== WebSocket Broadcast Test Summary (1.4) — ${WS_VUS} clients ===`)
  console.log(`Messages received: ${total}`)
  console.log(`Connect errors:    ${errs}`)
  console.log(`Broadcast P95:     ${p95} ms  (SLO < ${sloP95} ms)`)
  console.log(`Broadcast P99:     ${p99} ms`)
  console.log('')
  console.log('1.4.3 Cross-instance check: run a second backend instance and confirm')
  console.log("      all clients (both instances) received the same message count.")
  console.log('      redis-cli subscribe ws:telemetry  (observe pub/sub traffic)')
  console.log('=======================================================\n')

  return {
    'performance/results/websocket.json': JSON.stringify(data, null, 2),
  }
}
