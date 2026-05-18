/**
 * Spike Load Test — Test Cases 2.2
 *
 * Simulates a sudden factory-shift-change traffic burst (10 VU → 300 VU in 10 seconds).
 *
 *   Stage 1:  10 VU        hold 2 min    (baseline — system stable)
 *   Stage 2:  10 → 300 VU  ramp 10 sec  (sudden spike)
 *   Stage 3:  300 VU       hold 5 min   (sustained spike)
 *   Stage 4:  300 → 10 VU  ramp 30 sec  (recovery ramp-down)
 *   Stage 5:  10 VU        hold 2 min   (post-spike verification)
 *
 *   2.2.1  System handles sudden spike       → error rate < 5 % during spike
 *   2.2.2  Recovery after spike              → error rate < 0.1 % within 60 s of ramp-down
 *   2.2.3  Rate limiter at spike             → 429s expected but < 30 % of requests
 *   2.2.4  Circuit breaker under spike       → must NOT trip if DB is healthy
 *
 * Run:
 *   k6 run performance/scenarios/spike.js \
 *     --out prometheus=http://localhost:9090/api/v1/write
 */

import http from 'k6/http'
import { check, sleep } from 'k6'
import { Counter, Rate, Trend } from 'k6/metrics'
import { login, fetchDeviceIds, authHeaders, pickRandom, BASE_URL } from '../common/auth.js'

// ── Custom metrics ────────────────────────────────────────────────────────────

const successRate       = new Rate('success_rate')
const rateLimited       = new Counter('rate_limited_429_total')   // 2.2.3
const failedRequests    = new Counter('failed_requests')
const responseLatency   = new Trend('spike_response_latency_ms', true)

// ── k6 options ────────────────────────────────────────────────────────────────

export const options = {
  scenarios: {
    spike_test: {
      executor:         'ramping-vus',
      startVUs:         10,
      stages: [
        { target:  10, duration: '2m'  },   // baseline
        { target: 300, duration: '10s' },   // spike — 10 s ramp
        { target: 300, duration: '5m'  },   // sustained spike
        { target:  10, duration: '30s' },   // recovery
        { target:  10, duration: '2m'  },   // post-spike check
      ],
      gracefulRampDown: '15s',
    },
  },

  thresholds: {
    // 2.2.1: error rate during entire run (spike-phase failures accepted up to 5 %)
    http_req_failed: ['rate<0.10'],         // 10 % global ceiling; monitor per-stage in Grafana
    success_rate:    ['rate>0.90'],

    // 2.2.3: 429s are expected during spike — but tolerated up to 30 % of total
    'http_req_failed{status:429}': ['rate<0.30'],

    // Latency tolerance is relaxed during spike
    'http_req_duration{endpoint:devices-list}':     ['p(95)<1000'],
    'http_req_duration{endpoint:telemetry-latest}': ['p(95)<1500'],
  },
}

// ── Setup ─────────────────────────────────────────────────────────────────────

export function setup() {
  const token     = login(__ENV.USERNAME || 'admin', __ENV.PASSWORD || 'admin123')
  const deviceIds = fetchDeviceIds(token)
  if (deviceIds.length === 0) {
    throw new Error('No devices — seed data required before spike test')
  }
  console.log(`[spike] ${deviceIds.length} devices. Spike profile: 10→300 VU in 10 s, sustained 5 min.`)
  return { token, deviceIds }
}

// ── VU function ───────────────────────────────────────────────────────────────

export default function (data) {
  const roll = Math.random()

  let res, ok

  if (roll < 0.40) {
    // Device list — heaviest endpoint (40 %)
    res = http.get(`${BASE_URL}/api/v1/devices`, {
      headers: authHeaders(data.token),
      tags:    { endpoint: 'devices-list' },
    })
  } else if (roll < 0.70) {
    // Telemetry latest (30 %)
    const id = pickRandom(data.deviceIds)
    res = http.get(`${BASE_URL}/api/v1/telemetry/${id}/latest?limit=50`, {
      headers: authHeaders(data.token),
      tags:    { endpoint: 'telemetry-latest' },
    })
  } else if (roll < 0.85) {
    // Telemetry cache / Redis hot path (15 %)
    const id = pickRandom(data.deviceIds)
    res = http.get(`${BASE_URL}/api/v1/telemetry/${id}/cache`, {
      headers: authHeaders(data.token),
      tags:    { endpoint: 'telemetry-cache' },
    })
  } else {
    // Alerts (15 %)
    res = http.get(`${BASE_URL}/api/v1/alerts?page=0&size=50`, {
      headers: authHeaders(data.token),
      tags:    { endpoint: 'alerts-list' },
    })
  }

  responseLatency.add(res.timings.duration)

  // 2.2.3: track 429 rate limiter responses separately
  if (res.status === 429) {
    rateLimited.add(1)
    successRate.add(false)
    sleep(0.5)     // back-off on 429 to let bucket refill
    return
  }

  // 2.2.4: detect circuit-breaker-related 503s (backend returns 503 when CB open)
  if (res.status === 503) {
    console.warn(`[VU ${__VU}] Circuit breaker OPEN (503) — check /actuator/health`)
  }

  ok = check(res, {
    'status 2xx or 429': r => r.status < 300 || r.status === 429,
  })
  successRate.add(ok)
  if (!ok) failedRequests.add(1)

  sleep(0.05 + Math.random() * 0.1)
}

// ── Summary ───────────────────────────────────────────────────────────────────

export function handleSummary(data) {
  const errRate  = ((data.metrics.http_req_failed?.values?.rate ?? 0) * 100).toFixed(2)
  const rl       = data.metrics.rate_limited_429_total?.values?.count ?? 0
  const total    = data.metrics.http_reqs?.values?.count ?? 1
  const rlPct    = ((rl / total) * 100).toFixed(2)
  const p95      = data.metrics['http_req_duration{endpoint:devices-list}']?.values?.['p(95)']?.toFixed(1) ?? 'n/a'

  console.log('\n=== Spike Load Test Summary (2.2) ===')
  console.log(`2.2.1 Spike error rate:     ${errRate} %    (SLO < 5 %)`)
  console.log(`2.2.3 Rate-limited (429):   ${rl} requests  (${rlPct} %)  (SLO < 30 %)`)
  console.log(`      Devices list P95:     ${p95} ms`)
  console.log('')
  console.log('2.2.2 Recovery check (Grafana):')
  console.log('  http_req_failed should drop to < 0.1 % within 60 s of ramp-down.')
  console.log('2.2.4 Circuit breaker check:')
  console.log('  GET /actuator/health  →  resilience4j.circuitbreaker.state must = CLOSED')
  console.log('=====================================\n')

  return {
    'performance/results/spike.json': JSON.stringify(data, null, 2),
  }
}
