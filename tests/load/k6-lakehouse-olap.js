import http from 'k6/http'
import { check, sleep } from 'k6'
import { Trend, Rate } from 'k6/metrics'

const lakehouseLatency = new Trend('duckdb_query_latency', true)
const subSecondRate = new Rate('sub_second_rate')

export const options = {
  scenarios: {
    concurrent_analysts: {
      executor: 'ramping-vus',
      startVUs: 1,
      stages: [
        { duration: '20s', target: 10 },
        { duration: '30s', target: 30 },
        { duration: '60s', target: 50 },  // 50 concurrent OLAP analysts
        { duration: '20s', target: 0 }
      ]
    }
  },
  thresholds: {
    'duckdb_query_latency': ['p(95)<500', 'p(99)<1000'], // Sub-second SLA target
    'sub_second_rate': ['rate>0.98'],                     // >98% queries complete < 1s
    'http_req_failed': ['rate<0.01']
  }
}

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080'

let token = null

function getJwtToken() {
  if (token) return token
  const res = http.post(
    `${BASE_URL}/api/v1/auth/login`,
    JSON.stringify({ username: 'admin', password: 'admin123' }),
    { headers: { 'Content-Type': 'application/json' } }
  )
  if (res.status === 200) {
    token = JSON.parse(res.body).accessToken
  }
  return token
}

export function setup() {
  return { token: getJwtToken() }
}

export default function (data) {
  const metrics = ['TEMPERATURE', 'HUMIDITY', 'SMOKE_PPM']
  const buckets = ['DAY', 'MONTH']

  const metric = metrics[Math.floor(Math.random() * metrics.length)]
  const bucket = buckets[Math.floor(Math.random() * buckets.length)]

  const to = new Date().toISOString()
  const from = new Date(Date.now() - 90 * 24 * 60 * 60 * 1000).toISOString()

  const payload = JSON.stringify({
    metric,
    bucket,
    from,
    to
  })

  const t0 = Date.now()
  const res = http.post(`${BASE_URL}/api/v1/analytics/lakehouse/query`, payload, {
    headers: {
      Authorization: `Bearer ${data.token}`,
      'Content-Type': 'application/json'
    },
    tags: { endpoint: 'lakehouse-duckdb-olap' }
  })
  const duration = Date.now() - t0
  lakehouseLatency.add(duration)

  const isSubSecond = duration < 1000
  subSecondRate.add(isSubSecond)

  check(res, {
    'status is 200': r => r.status === 200,
    'query latency < 1000ms': () => isSubSecond
  })

  sleep(0.5)
}

export function handleSummary(data) {
  const p95 = data.metrics.duckdb_query_latency?.values?.['p(95)']
  const avg = data.metrics.duckdb_query_latency?.values?.avg
  const subSec = (data.metrics.sub_second_rate?.values?.rate * 100).toFixed(1)

  console.log('\n========================================================')
  console.log('🦆 Sentinel IoT DuckDB Lakehouse OLAP Load Test Report')
  console.log('========================================================')
  console.log(`Average Query Latency   : ${avg?.toFixed(1)} ms`)
  console.log(`p95 Query Latency       : ${p95?.toFixed(1)} ms (<500ms target)`)
  console.log(`Sub-Second Success Rate : ${subSec}% (>98% target)`)
  console.log('========================================================\n')

  return {
    'tests/load/lakehouse-olap-results.json': JSON.stringify(data, null, 2)
  }
}
