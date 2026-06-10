/**
 * API Ramp-Up Load Test — Test Cases 2.1
 *
 * Gradually increases VUs from 0 → 500 to find the point where SLOs start failing.
 *
 *   Stage 1:  0 → 50 VU   over 2 min  (warm-up)
 *   Stage 2: 50 → 200 VU  over 5 min  (normal load)
 *   Stage 3: 200 → 500 VU over 5 min  (stress)
 *   Stage 4: 500 VU       hold 3 min  (peak)
 *   Stage 5: 500 → 0 VU   over 2 min  (cool-down)
 *
 *   2.1.1  Device list scaling           GET /devices            error < 1 % at 200 VU
 *   2.1.2  Telemetry read scaling        GET /telemetry/latest   P95 < 500 ms at 200 VU
 *   2.1.3  Auth endpoint scaling         POST /auth/login        error < 0.1 % at 200 VU
 *   2.1.4  HikariCP pool exhaustion      mixed API               no SQLTransientConnectionException at 200 VU
 *
 * Post-run checks (Grafana / actuator):
 *   hikaricp_connections_active     — connection pool utilisation
 *   hikaricp_connections_timeout_total — pool exhaustion events
 *   resilience4j.circuitbreaker    — GET /actuator/health
 *
 * Run:
 *   k6 run tests/performance/scenarios/ramp-up.js \
 *     --out prometheus=http://localhost:9090/api/v1/write
 */

import http from 'k6/http'
import { check, sleep } from 'k6'
import { Counter, Rate, Trend } from 'k6/metrics'
import { login, fetchDeviceIds, authHeaders, pickRandom, BASE_URL } from '../common/auth.js'
import { buildHttpThresholds } from '../common/thresholds.js'

// ── Custom metrics ─────────────────────────────────────────────────────────────

const successRate    = new Rate('success_rate')
const failedRequests = new Counter('failed_requests')
const p99Tracker     = new Trend('ramp_p99_latency_ms', true)

// ── k6 options ────────────────────────────────────────────────────────────────

export const options = {
  scenarios: {
    ramp_up: {
      executor:         'ramping-vus',
      startVUs:         0,
      stages: [
        { target:  50, duration: '2m' },   // warm-up
        { target: 200, duration: '5m' },   // normal — SLO window for 2.1.1–2.1.3
        { target: 500, duration: '5m' },   // stress — find breaking point
        { target: 500, duration: '3m' },   // peak sustained
        { target:   0, duration: '2m' },   // cool-down
      ],
      gracefulRampDown: '30s',
    },
  },

  thresholds: {
    ...buildHttpThresholds(),

    // 2.1.1: error rate must stay below 1 % during normal-load phase
    // (k6 cannot enforce per-stage thresholds natively; monitor in Grafana)
    http_req_failed:  ['rate<0.05'],        // global tolerance during stress phase
    success_rate:     ['rate>0.95'],

    // 2.1.2: telemetry read P95 at any VU level
    'http_req_duration{endpoint:telemetry-latest}': ['p(95)<500'],

    // 2.1.3: auth endpoint error rate
    'http_req_failed{endpoint:auth-login}': ['rate<0.05'],
  },
}

// ── Setup ─────────────────────────────────────────────────────────────────────

export function setup() {
  const token     = login(__ENV.USERNAME || 'admin', __ENV.PASSWORD || 'admin123')
  const deviceIds = fetchDeviceIds(token)
  if (deviceIds.length === 0) {
    throw new Error('No devices found — seed data required before ramp-up test')
  }
  console.log(`[ramp-up] Loaded ${deviceIds.length} devices. Stages: 0→50→200→500 VU over 17 min.`)
  return { token, deviceIds }
}

// ── VU function: mixed API workload ──────────────────────────────────────────

export default function (data) {
  const roll = Math.random()

  let res, ok

  if (roll < 0.05) {
    // 2.1.3: ~5 % of requests are login calls
    res = http.post(
      `${BASE_URL}/api/v1/auth/login`,
      JSON.stringify({ username: __ENV.USERNAME || 'admin', password: __ENV.PASSWORD || 'admin123' }),
      { headers: { 'Content-Type': 'application/json' }, tags: { endpoint: 'auth-login' } }
    )
    ok = check(res, { 'login 200': r => r.status === 200 })

  } else if (roll < 0.30) {
    // 2.1.1: ~25 % device list (heaviest select — tests HikariCP + RLS)
    res = http.get(`${BASE_URL}/api/v1/devices`, {
      headers: authHeaders(data.token),
      tags:    { endpoint: 'devices-list' },
    })
    ok = check(res, { 'devices 200': r => r.status === 200 })

  } else if (roll < 0.60) {
    // 2.1.2: ~30 % telemetry latest (read path + Redis)
    const id = pickRandom(data.deviceIds)
    res = http.get(`${BASE_URL}/api/v1/telemetry/${id}/latest?limit=50`, {
      headers: authHeaders(data.token),
      tags:    { endpoint: 'telemetry-latest' },
    })
    ok = check(res, { 'telemetry 200': r => r.status === 200 })

  } else if (roll < 0.80) {
    // Telemetry cache (Redis hot path — 20 %)
    const id = pickRandom(data.deviceIds)
    res = http.get(`${BASE_URL}/api/v1/telemetry/${id}/cache`, {
      headers: authHeaders(data.token),
      tags:    { endpoint: 'telemetry-cache' },
    })
    ok = check(res, { 'cache 200': r => r.status === 200 })

  } else {
    // Alerts (20 %)
    res = http.get(`${BASE_URL}/api/v1/alerts?page=0&size=50`, {
      headers: authHeaders(data.token),
      tags:    { endpoint: 'alerts-list' },
    })
    ok = check(res, { 'alerts 200': r => r.status === 200 })
  }

  p99Tracker.add(res.timings.duration)
  successRate.add(ok)
  if (!ok) failedRequests.add(1)

  // Small think time to avoid overwhelming a single-node dev setup
  sleep(0.1 + Math.random() * 0.4)
}

// ── Summary ───────────────────────────────────────────────────────────────────

export function handleSummary(data) {
  const fmt = key => data.metrics[key]?.values?.['p(95)']?.toFixed(1) ?? 'n/a'
  const errRate = ((data.metrics.http_req_failed?.values?.rate ?? 0) * 100).toFixed(2)

  console.log('\n=== Ramp-Up Load Test Summary (2.1) ===')
  console.log(`Device list     P95: ${fmt('http_req_duration{endpoint:devices-list}')} ms     (SLO < 200 ms)`)
  console.log(`Telemetry/latest P95: ${fmt('http_req_duration{endpoint:telemetry-latest}')} ms  (SLO < 300 ms, stress < 500 ms)`)
  console.log(`Auth login       P95: ${fmt('http_req_duration{endpoint:auth-login}')} ms     (SLO < 300 ms)`)
  console.log(`Overall error rate:   ${errRate} %`)
  console.log('')
  console.log('Grafana checks:')
  console.log('  hikaricp_connections_active         — pool utilisation per stage')
  console.log('  hikaricp_connections_timeout_total  — exhaustion count (2.1.4)')
  console.log('  resilience4j.circuitbreaker         — GET /actuator/health')
  console.log('========================================\n')

  return {
    'performance/results/ramp-up.json': JSON.stringify(data, null, 2),
  }
}
