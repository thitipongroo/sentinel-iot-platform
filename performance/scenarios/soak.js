/**
 * Soak Test (Endurance Test) — Test Cases 2.3
 *
 * 100 VU running for 2 hours to detect memory leaks, connection leaks,
 * and long-term performance degradation.
 *
 *   Stage 1:   0 → 100 VU  over  5 min  (warm-up)
 *   Stage 2: 100 VU        hold 120 min (endurance window)
 *   Stage 3: 100 → 0 VU    over  5 min  (cool-down)
 *
 *   2.3.1  Memory stability         → JVM heap must not trend up > 20 % over 2 h
 *   2.3.2  Connection pool stability → HikariCP active connections drain on idle
 *   2.3.3  Redis connection stability → redis_connected_clients not unbounded
 *   2.3.4  Latency degradation        → P95 at t=110 min < P95 at t=10 min × 1.20
 *   2.3.5  TenantContext leak         → zero cross-tenant org_id in responses
 *   2.3.6  JWT revocation list growth → Redis DB-1 memory not unbounded
 *
 * Run (full 2-hour soak):
 *   k6 run performance/scenarios/soak.js \
 *     --out prometheus=http://localhost:9090/api/v1/write
 *
 * Run (quick smoke: 10 min warm-up + 20 min soak):
 *   k6 run performance/scenarios/soak.js -e SOAK_DURATION=20m
 *
 * Grafana snapshots: save at t=0, t=30 min, t=60 min, t=120 min
 *   jvm_memory_used_bytes{area="heap"}
 *   hikaricp_connections_active
 *   redis_connected_clients
 *   http_req_duration p95
 */

import http from 'k6/http'
import { check, sleep } from 'k6'
import { Counter, Rate, Trend } from 'k6/metrics'
import { login, fetchDeviceIds, authHeaders, pickRandom, BASE_URL } from '../common/auth.js'
import { buildHttpThresholds } from '../common/thresholds.js'

// ── Configuration ─────────────────────────────────────────────────────────────

const SOAK_DURATION = __ENV.SOAK_DURATION || '120m'

// ── Custom metrics ────────────────────────────────────────────────────────────

const successRate         = new Rate('success_rate')
const failedRequests      = new Counter('failed_requests')
const latencyTrend        = new Trend('soak_latency_ms', true)
const crossTenantViolations = new Counter('cross_tenant_violations')   // 2.3.5

// ── k6 options ────────────────────────────────────────────────────────────────

export const options = {
  scenarios: {
    soak: {
      executor:         'ramping-vus',
      startVUs:         0,
      stages: [
        { target: 100, duration: '5m'           },   // warm-up
        { target: 100, duration: SOAK_DURATION   },   // endurance
        { target:   0, duration: '5m'           },   // cool-down
      ],
      gracefulRampDown: '30s',
    },
  },

  thresholds: {
    ...buildHttpThresholds(),

    // 2.3.4: latency degradation — soak P99 must stay reasonable
    soak_latency_ms: ['p(99)<600'],

    // 2.3.5: zero cross-tenant data leaks
    cross_tenant_violations: ['count==0'],

    http_req_failed: ['rate<0.01'],
    success_rate:    ['rate>0.99'],
  },
}

// ── Setup ─────────────────────────────────────────────────────────────────────

export function setup() {
  const token     = login(__ENV.USERNAME || 'admin', __ENV.PASSWORD || 'admin123')
  const deviceIds = fetchDeviceIds(token)
  if (deviceIds.length === 0) {
    throw new Error('No devices — seed data required before soak test')
  }
  const orgId = extractOrgId(token)
  console.log(`[soak] ${deviceIds.length} devices, orgId=${orgId}. Duration: ${SOAK_DURATION} + 10 min ramp.`)
  return { token, deviceIds, orgId }
}

// ── VU function ───────────────────────────────────────────────────────────────

export default function (data) {
  const roll = Math.random()
  let res, ok

  if (roll < 0.25) {
    res = http.get(`${BASE_URL}/api/v1/devices`, {
      headers: authHeaders(data.token),
      tags:    { endpoint: 'devices-list' },
    })
    // 2.3.5: verify every device in the response belongs to our org
    if (res.status === 200) {
      try {
        const devices = JSON.parse(res.body)
        for (const d of devices) {
          if (data.orgId && d.organizationId && d.organizationId !== data.orgId) {
            crossTenantViolations.add(1)
            console.error(`[VU ${__VU}] CROSS-TENANT LEAK: got orgId ${d.organizationId}, expected ${data.orgId}`)
          }
        }
      } catch (_) {}
    }
    ok = check(res, { 'devices 200': r => r.status === 200 })

  } else if (roll < 0.55) {
    const id = pickRandom(data.deviceIds)
    res = http.get(`${BASE_URL}/api/v1/telemetry/${id}/latest?limit=50`, {
      headers: authHeaders(data.token),
      tags:    { endpoint: 'telemetry-latest' },
    })
    ok = check(res, { 'telemetry 200': r => r.status === 200 })

  } else if (roll < 0.75) {
    const id = pickRandom(data.deviceIds)
    res = http.get(`${BASE_URL}/api/v1/telemetry/${id}/cache`, {
      headers: authHeaders(data.token),
      tags:    { endpoint: 'telemetry-cache' },
    })
    ok = check(res, { 'cache 200': r => r.status === 200 })

  } else {
    res = http.get(`${BASE_URL}/api/v1/alerts?page=0&size=50`, {
      headers: authHeaders(data.token),
      tags:    { endpoint: 'alerts-list' },
    })
    ok = check(res, { 'alerts 200': r => r.status === 200 })
  }

  latencyTrend.add(res.timings.duration)
  successRate.add(ok)
  if (!ok) failedRequests.add(1)

  sleep(0.5 + Math.random() * 0.5)
}

// ── Helpers ───────────────────────────────────────────────────────────────────

function extractOrgId(token) {
  try {
    const payload = JSON.parse(atob(token.split('.')[1]))
    return payload.org_id ?? payload.orgId ?? null
  } catch (_) {
    return null
  }
}

// ── Summary ───────────────────────────────────────────────────────────────────

export function handleSummary(data) {
  const p95       = data.metrics.soak_latency_ms?.values?.['p(95)']?.toFixed(1) ?? 'n/a'
  const p99       = data.metrics.soak_latency_ms?.values?.['p(99)']?.toFixed(1) ?? 'n/a'
  const errRate   = ((data.metrics.http_req_failed?.values?.rate ?? 0) * 100).toFixed(2)
  const leaks     = data.metrics.cross_tenant_violations?.values?.count ?? 0
  const total     = data.metrics.http_reqs?.values?.count ?? 0

  console.log('\n=== Soak Test Summary (2.3) ===')
  console.log(`Total requests:         ${total}`)
  console.log(`Soak latency P95:       ${p95} ms`)
  console.log(`Soak latency P99:       ${p99} ms`)
  console.log(`Error rate:             ${errRate} %`)
  console.log(`Cross-tenant leaks (2.3.5): ${leaks}  (SLO = 0)`)
  console.log('')
  console.log('Grafana verification:')
  console.log('  2.3.1 jvm_memory_used_bytes{area="heap"}   — no upward trend > 20 %')
  console.log('  2.3.2 hikaricp_connections_active           — drains on idle')
  console.log('  2.3.3 redis_connected_clients               — stable, not growing')
  console.log('  2.3.4 http_req_duration p95 @ t=10m vs t=110m — degradation < 20 %')
  console.log('  2.3.6 redis_memory_used_bytes (DB-1)        — stable (expired keys evicted)')
  console.log('==============================\n')

  return {
    'performance/results/soak.json': JSON.stringify(data, null, 2),
  }
}
