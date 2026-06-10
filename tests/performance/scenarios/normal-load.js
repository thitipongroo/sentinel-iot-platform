/**
 * Normal Load — Test Cases 1.1 + 1.3
 *
 * 1.1  API Response Time Under Normal Load (50 VU, 10 min)
 *   1.1.1  POST /auth/login            P95 < 300 ms
 *   1.1.2  GET  /devices               P95 < 200 ms
 *   1.1.3  GET  /devices/{id}          P95 < 200 ms
 *   1.1.4  GET  /telemetry/{id}/latest P95 < 300 ms
 *   1.1.5  GET  /telemetry/{id}/range  P95 < 800 ms  (30 VU)
 *   1.1.6  GET  /alerts                P95 < 200 ms
 *
 * 1.3  Redis Cache Performance
 *   1.3.1  GET /telemetry/{id}/cache  → custom cacheHitRate metric ≥ 90 %
 *   1.3.2  GET /devices               → Redis DB-1 (JWT blocklist) measured via P99 auth overhead
 *
 * Run:
 *   k6 run tests/performance/scenarios/normal-load.js \
 *     --out prometheus=http://localhost:9090/api/v1/write
 *
 * Environment variables (optional):
 *   BASE_URL   default http://localhost:8080
 *   USERNAME   default admin
 *   PASSWORD   default admin123
 */

import http from 'k6/http'
import { check, sleep } from 'k6'
import { Counter, Rate, Trend } from 'k6/metrics'
import { subHours } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js'
import { login, fetchDeviceIds, authHeaders, pickRandom, BASE_URL } from '../common/auth.js'
import { buildHttpThresholds } from '../common/thresholds.js'

// ── Custom metrics ────────────────────────────────────────────────────────────

const successRate       = new Rate('success_rate')
const cacheHitRate      = new Rate('telemetry_cache_hit_rate')   // 1.3.1
const cacheLatency      = new Trend('telemetry_cache_latency_ms', true)
const failedRequests    = new Counter('failed_requests')

// ── k6 options ────────────────────────────────────────────────────────────────

export const options = {
  // Separate scenarios let us set different VU counts per endpoint (e.g. 30 VU for range)
  // and produce per-endpoint tagged metrics for Grafana dashboards.
  scenarios: {
    // 1.1.1 — Login latency
    login_latency: {
      executor:          'ramping-vus',
      startVUs:          0,
      stages:            [{ target: 50, duration: '1m' }, { target: 50, duration: '8m' }, { target: 0, duration: '1m' }],
      exec:              'testLogin',
      gracefulRampDown:  '15s',
    },
    // 1.1.2 — Device list latency
    device_list: {
      executor:         'ramping-vus',
      startVUs:         0,
      stages:           [{ target: 50, duration: '1m' }, { target: 50, duration: '8m' }, { target: 0, duration: '1m' }],
      exec:             'testDeviceList',
      gracefulRampDown: '15s',
    },
    // 1.1.3 — Device detail latency
    device_detail: {
      executor:         'ramping-vus',
      startVUs:         0,
      stages:           [{ target: 50, duration: '1m' }, { target: 50, duration: '8m' }, { target: 0, duration: '1m' }],
      exec:             'testDeviceDetail',
      gracefulRampDown: '15s',
    },
    // 1.1.4 — Telemetry latest (DB read path)
    telemetry_latest: {
      executor:         'ramping-vus',
      startVUs:         0,
      stages:           [{ target: 50, duration: '1m' }, { target: 50, duration: '8m' }, { target: 0, duration: '1m' }],
      exec:             'testTelemetryLatest',
      gracefulRampDown: '15s',
    },
    // 1.1.5 — Telemetry range (heavier DB query, 30 VU)
    telemetry_range: {
      executor:         'ramping-vus',
      startVUs:         0,
      stages:           [{ target: 30, duration: '1m' }, { target: 30, duration: '8m' }, { target: 0, duration: '1m' }],
      exec:             'testTelemetryRange',
      gracefulRampDown: '15s',
    },
    // 1.1.6 — Alert list
    alert_list: {
      executor:         'ramping-vus',
      startVUs:         0,
      stages:           [{ target: 50, duration: '1m' }, { target: 50, duration: '8m' }, { target: 0, duration: '1m' }],
      exec:             'testAlertList',
      gracefulRampDown: '15s',
    },
    // 1.3.1 — Redis cache hit rate
    telemetry_cache: {
      executor:         'ramping-vus',
      startVUs:         0,
      stages:           [{ target: 50, duration: '1m' }, { target: 50, duration: '8m' }, { target: 0, duration: '1m' }],
      exec:             'testTelemetryCache',
      gracefulRampDown: '15s',
    },
  },

  thresholds: {
    ...buildHttpThresholds(),
    telemetry_cache_hit_rate: ['rate>=0.90'],     // 1.3.1: ≥ 90 % cache hits
    telemetry_cache_latency_ms: ['p(99)<5'],      // 1.3.2: Redis P99 < 5 ms
  },
}

// ── Setup: authenticate + collect device pool ─────────────────────────────────

export function setup() {
  const token     = login(__ENV.USERNAME || 'admin', __ENV.PASSWORD || 'admin123')
  const deviceIds = fetchDeviceIds(token)
  if (deviceIds.length === 0) {
    throw new Error('No devices found — run seed-demo.sh before the performance test')
  }
  return { token, deviceIds }
}

// ── Scenario functions ────────────────────────────────────────────────────────

// 1.1.1
export function testLogin() {
  const res = http.post(
    `${BASE_URL}/api/v1/auth/login`,
    JSON.stringify({ username: __ENV.USERNAME || 'admin', password: __ENV.PASSWORD || 'admin123' }),
    { headers: { 'Content-Type': 'application/json' }, tags: { endpoint: 'auth-login' } }
  )
  const ok = check(res, { 'login 200': r => r.status === 200, 'login < 300 ms': r => r.timings.duration < 300 })
  successRate.add(ok)
  if (!ok) failedRequests.add(1)
  sleep(1)
}

// 1.1.2
export function testDeviceList(data) {
  const res = http.get(`${BASE_URL}/api/v1/devices`, {
    headers: authHeaders(data.token),
    tags:    { endpoint: 'devices-list' },
  })
  const ok = check(res, { 'devices 200': r => r.status === 200, 'devices < 200 ms': r => r.timings.duration < 200 })
  successRate.add(ok)
  if (!ok) failedRequests.add(1)
  sleep(0.5)
}

// 1.1.3
export function testDeviceDetail(data) {
  const id  = pickRandom(data.deviceIds)
  const res = http.get(`${BASE_URL}/api/v1/devices/${id}`, {
    headers: authHeaders(data.token),
    tags:    { endpoint: 'device-detail' },
  })
  const ok = check(res, { 'device detail 200': r => r.status === 200, 'device detail < 200 ms': r => r.timings.duration < 200 })
  successRate.add(ok)
  if (!ok) failedRequests.add(1)
  sleep(0.5)
}

// 1.1.4
export function testTelemetryLatest(data) {
  const id  = pickRandom(data.deviceIds)
  const res = http.get(`${BASE_URL}/api/v1/telemetry/${id}/latest?limit=50`, {
    headers: authHeaders(data.token),
    tags:    { endpoint: 'telemetry-latest' },
  })
  const ok = check(res, { 'telemetry latest 200': r => r.status === 200, 'telemetry latest < 300 ms': r => r.timings.duration < 300 })
  successRate.add(ok)
  if (!ok) failedRequests.add(1)
  sleep(0.5)
}

// 1.1.5 — 1-hour range query (heavier on DB / partition scan)
export function testTelemetryRange(data) {
  const id  = pickRandom(data.deviceIds)
  const to  = new Date().toISOString()
  const from = new Date(Date.now() - 3600_000).toISOString()
  const res = http.get(`${BASE_URL}/api/v1/telemetry/${id}/range?from=${from}&to=${to}`, {
    headers: authHeaders(data.token),
    tags:    { endpoint: 'telemetry-range' },
  })
  const ok = check(res, { 'telemetry range 200': r => r.status === 200, 'telemetry range < 800 ms': r => r.timings.duration < 800 })
  successRate.add(ok)
  if (!ok) failedRequests.add(1)
  sleep(1)
}

// 1.1.6
export function testAlertList(data) {
  const res = http.get(`${BASE_URL}/api/v1/alerts?page=0&size=50`, {
    headers: authHeaders(data.token),
    tags:    { endpoint: 'alerts-list' },
  })
  const ok = check(res, { 'alerts 200': r => r.status === 200, 'alerts < 200 ms': r => r.timings.duration < 200 })
  successRate.add(ok)
  if (!ok) failedRequests.add(1)
  sleep(0.5)
}

// 1.3.1 + 1.3.2 — Redis cache path: should be sub-millisecond server-side
export function testTelemetryCache(data) {
  const id    = pickRandom(data.deviceIds)
  const start = Date.now()
  const res   = http.get(`${BASE_URL}/api/v1/telemetry/${id}/cache`, {
    headers: authHeaders(data.token),
    tags:    { endpoint: 'telemetry-cache' },
  })
  const latency = Date.now() - start
  cacheLatency.add(latency)

  // 1.3.1: a non-empty response body indicates a cache hit (device has recent telemetry)
  const body = res.body
  const hit  = res.status === 200 && body && body !== '{}' && body !== 'null'
  cacheHitRate.add(hit)

  const ok = check(res, {
    'cache 200':      r => r.status === 200,
    'cache < 200 ms': r => r.timings.duration < 200,
  })
  successRate.add(ok)
  if (!ok) failedRequests.add(1)
  sleep(0.1)
}

// ── Summary ────────────────────────────────────────────────────────────────────

export function handleSummary(data) {
  const fmt = (key, pct) => {
    const v = data.metrics[key]?.values?.[pct]
    return v != null ? `${v.toFixed(1)} ms` : 'n/a'
  }

  console.log('\n=== Normal Load Test Summary (1.1 + 1.3) ===')
  console.log(`1.1.1 Login           P95: ${fmt('http_req_duration{endpoint:auth-login}',       'p(95)')}  (SLO < 300 ms)`)
  console.log(`1.1.2 Device list     P95: ${fmt('http_req_duration{endpoint:devices-list}',     'p(95)')}  (SLO < 200 ms)`)
  console.log(`1.1.3 Device detail   P95: ${fmt('http_req_duration{endpoint:device-detail}',    'p(95)')}  (SLO < 200 ms)`)
  console.log(`1.1.4 Telemetry/latest P95: ${fmt('http_req_duration{endpoint:telemetry-latest}','p(95)')}  (SLO < 300 ms)`)
  console.log(`1.1.5 Telemetry/range  P95: ${fmt('http_req_duration{endpoint:telemetry-range}', 'p(95)')}  (SLO < 800 ms)`)
  console.log(`1.1.6 Alerts           P95: ${fmt('http_req_duration{endpoint:alerts-list}',     'p(95)')}  (SLO < 200 ms)`)
  console.log(`1.3.1 Cache hit rate:  ${((data.metrics.telemetry_cache_hit_rate?.values?.rate ?? 0) * 100).toFixed(1)} %  (SLO ≥ 90 %)`)
  console.log(`1.3.2 Cache P99:       ${fmt('telemetry_cache_latency_ms', 'p(99)')}  (SLO < 5 ms)`)
  console.log(`      Error rate:       ${((data.metrics.http_req_failed?.values?.rate ?? 0) * 100).toFixed(2)} %`)
  console.log('==============================================\n')

  return {
    'performance/results/normal-load.json': JSON.stringify(data, null, 2),
  }
}
