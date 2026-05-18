/**
 * Multi-Tenant Load Test — Test Cases 2.5
 *
 * 5 organisations run concurrently (50 VU each = 250 VU total).
 * Each VU authenticates with its own org's admin account and may only see
 * devices belonging to that org.  Any cross-org data in a response is a leak.
 *
 *   2.5.1  Cross-tenant data isolation under load → 0 cross-tenant leaks
 *   2.5.2  RLS performance overhead              → P95 overhead < 15 % vs single-org
 *   2.5.3  TenantContext ThreadLocal under concurrency → 0 cross-tenant leaks
 *
 * Setup requirements (pre-condition):
 *   Run scripts/seed-demo.sh to create the 5 demo organisations.
 *   Each org must have an admin user and ≥ 5 devices.
 *
 *   Expected users (from seed-demo.sh):
 *     org-alpha-admin / sentinel123  (org alpha)
 *     org-beta-admin  / sentinel123  (org beta)
 *     org-gamma-admin / sentinel123  (org gamma)
 *     org-delta-admin / sentinel123  (org delta)
 *     org-epsilon-admin / sentinel123 (org epsilon)
 *
 *   Override via env vars:
 *     ORG_A_USER, ORG_A_PASS, ... ORG_E_USER, ORG_E_PASS
 *
 * Run:
 *   k6 run performance/scenarios/multi-tenant.js \
 *     --out prometheus=http://localhost:9090/api/v1/write
 *
 * Run (quick validation — 5 min):
 *   k6 run performance/scenarios/multi-tenant.js -e DURATION=5m
 */

import http from 'k6/http'
import { check, sleep } from 'k6'
import { Counter, Rate, Trend } from 'k6/metrics'
import { login, fetchDeviceIds, authHeaders, pickRandom, BASE_URL } from '../common/auth.js'
import { buildHttpThresholds } from '../common/thresholds.js'

// ── Configuration ─────────────────────────────────────────────────────────────

const DURATION = __ENV.DURATION || '10m'
const VUS_PER_ORG = 50

// Organisation credentials — override with env vars for non-demo environments
const ORGS = [
  { name: 'alpha',   user: __ENV.ORG_A_USER || 'org-alpha-admin',   pass: __ENV.ORG_A_PASS || 'sentinel123' },
  { name: 'beta',    user: __ENV.ORG_B_USER || 'org-beta-admin',    pass: __ENV.ORG_B_PASS || 'sentinel123' },
  { name: 'gamma',   user: __ENV.ORG_C_USER || 'org-gamma-admin',   pass: __ENV.ORG_C_PASS || 'sentinel123' },
  { name: 'delta',   user: __ENV.ORG_D_USER || 'org-delta-admin',   pass: __ENV.ORG_D_PASS || 'sentinel123' },
  { name: 'epsilon', user: __ENV.ORG_E_USER || 'org-epsilon-admin', pass: __ENV.ORG_E_PASS || 'sentinel123' },
]

// ── Custom metrics ────────────────────────────────────────────────────────────

const successRate           = new Rate('success_rate')
const failedRequests        = new Counter('failed_requests')
const crossTenantLeaks      = new Counter('cross_tenant_leaks')        // 2.5.1 + 2.5.3
const multiTenantLatency    = new Trend('multi_tenant_latency_ms', true)

// ── k6 options ────────────────────────────────────────────────────────────────

// Build one scenario per org so each group of VUs shares the same token/devices
const scenarios = {}
ORGS.forEach((org, idx) => {
  scenarios[`org_${org.name}`] = {
    executor:         'constant-vus',
    vus:              VUS_PER_ORG,
    duration:         DURATION,
    env:              { ORG_INDEX: String(idx) },
    exec:             'orgVu',
    gracefulStop:     '15s',
    startTime:        '0s',
  }
})

export const options = {
  scenarios,
  thresholds: {
    ...buildHttpThresholds(),

    // 2.5.1 / 2.5.3: critical — must be zero
    cross_tenant_leaks: ['count==0'],

    // 2.5.2: multi-tenant P95 should not be more than 15 % worse than single-org SLO
    // single-org SLO for /devices = 200 ms → allow up to 230 ms under multi-tenant load
    'http_req_duration{endpoint:devices-list}': ['p(95)<230'],

    http_req_failed: ['rate<0.01'],
    success_rate:    ['rate>0.99'],
  },
}

// ── Setup: authenticate all 5 orgs once ──────────────────────────────────────

export function setup() {
  const orgData = ORGS.map(org => {
    try {
      const token     = login(org.user, org.pass)
      const deviceIds = fetchDeviceIds(token)
      const orgId     = extractOrgId(token)
      if (deviceIds.length === 0) {
        throw new Error(`Org '${org.name}': no devices — run seed-demo.sh`)
      }
      console.log(`[multi-tenant] org=${org.name} orgId=${orgId} devices=${deviceIds.length}`)
      return { name: org.name, token, deviceIds, orgId }
    } catch (e) {
      throw new Error(`Failed to set up org '${org.name}': ${e.message}`)
    }
  })

  console.log(`[multi-tenant] ${ORGS.length} orgs × ${VUS_PER_ORG} VU = ${ORGS.length * VUS_PER_ORG} total VUs. Duration: ${DURATION}`)
  return orgData
}

// ── VU function ───────────────────────────────────────────────────────────────

export function orgVu(allOrgData) {
  // Each scenario passes ORG_INDEX via env so each group of VUs uses its own org
  const idx  = parseInt(__ENV.ORG_INDEX ?? '0')
  const data = allOrgData[idx]
  const roll = Math.random()
  let res, ok

  if (roll < 0.40) {
    // Device list — primary isolation check
    res = http.get(`${BASE_URL}/api/v1/devices`, {
      headers: authHeaders(data.token),
      tags:    { endpoint: 'devices-list', org: data.name },
    })
    ok = check(res, { 'devices 200': r => r.status === 200 })

    // 2.5.1 / 2.5.3: scan every device in the response for cross-org leak
    if (res.status === 200 && data.orgId) {
      try {
        const devices = JSON.parse(res.body)
        for (const d of devices) {
          if (d.organizationId && d.organizationId !== data.orgId) {
            crossTenantLeaks.add(1)
            console.error(
              `[VU ${__VU}] CROSS-TENANT LEAK org=${data.name}: ` +
              `device ${d.id} has orgId ${d.organizationId}, expected ${data.orgId}`
            )
          }
        }
      } catch (_) {}
    }

  } else if (roll < 0.70) {
    const id = pickRandom(data.deviceIds)
    res = http.get(`${BASE_URL}/api/v1/telemetry/${id}/latest?limit=50`, {
      headers: authHeaders(data.token),
      tags:    { endpoint: 'telemetry-latest', org: data.name },
    })
    ok = check(res, { 'telemetry 200': r => r.status === 200 })

  } else if (roll < 0.85) {
    const id = pickRandom(data.deviceIds)
    res = http.get(`${BASE_URL}/api/v1/telemetry/${id}/cache`, {
      headers: authHeaders(data.token),
      tags:    { endpoint: 'telemetry-cache', org: data.name },
    })
    ok = check(res, { 'cache 200': r => r.status === 200 })

  } else {
    res = http.get(`${BASE_URL}/api/v1/alerts?page=0&size=50`, {
      headers: authHeaders(data.token),
      tags:    { endpoint: 'alerts-list', org: data.name },
    })
    ok = check(res, { 'alerts 200': r => r.status === 200 })
  }

  multiTenantLatency.add(res.timings.duration)
  successRate.add(ok)
  if (!ok) failedRequests.add(1)

  sleep(0.2 + Math.random() * 0.3)
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
  const leaks    = data.metrics.cross_tenant_leaks?.values?.count ?? 0
  const p95      = data.metrics['http_req_duration{endpoint:devices-list}']?.values?.['p(95)']?.toFixed(1) ?? 'n/a'
  const errRate  = ((data.metrics.http_req_failed?.values?.rate ?? 0) * 100).toFixed(2)
  const total    = data.metrics.http_reqs?.values?.count ?? 0

  console.log('\n=== Multi-Tenant Load Test Summary (2.5) ===')
  console.log(`Total requests:               ${total}  (${ORGS.length} orgs × ${VUS_PER_ORG} VU)`)
  console.log(`2.5.1/2.5.3 Cross-tenant leaks: ${leaks}   (SLO = 0)`)
  console.log(`2.5.2 Device list P95:          ${p95} ms  (SLO < 230 ms = 200 ms + 15 % RLS overhead)`)
  console.log(`Error rate:                     ${errRate} %`)
  console.log('')
  console.log('Grafana: compare http_req_duration{endpoint:devices-list} per org tag.')
  console.log('No org should have consistently higher latency than the others (partition imbalance).')
  console.log('=============================================\n')

  return {
    'performance/results/multi-tenant.json': JSON.stringify(data, null, 2),
  }
}
