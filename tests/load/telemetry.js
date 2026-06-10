import http from 'k6/http'
import { check, sleep } from 'k6'
import { Counter, Trend, Rate } from 'k6/metrics'

const successRate = new Rate('success_rate')
const telemetryLatency = new Trend('telemetry_latency', true)
const failedRequests = new Counter('failed_requests')

export const options = {
  scenarios: {
    // Ramp HTTP requests against GET /api/telemetry/{id}/cache (Redis-backed hot read path).
    // This measures end-to-end API latency under sustained load, not MQTT ingestion throughput.
    sustained_load: {
      executor: 'ramping-arrival-rate',
      startRate: 10,
      timeUnit: '1s',
      preAllocatedVUs: 50,
      maxVUs: 200,
      stages: [
        { target: 100, duration: '30s' },
        { target: 500, duration: '60s' },
        { target: 1000, duration: '60s' },
        { target: 1000, duration: '120s' }, // sustain at peak
        { target: 0, duration: '30s' }
      ]
    }
  },
  thresholds: {
    http_req_duration: ['p(95)<200', 'p(99)<500'],
    success_rate: ['rate>0.95'],
    http_req_failed: ['rate<0.05']
  }
}

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080'

// Cache JWT token
let token = null

function getToken() {
  if (token) return token
  const resp = http.post(`${BASE_URL}/api/v1/auth/login`,
    JSON.stringify({ username: 'admin', password: 'admin123' }),
    { headers: { 'Content-Type': 'application/json' } }
  )
  if (resp.status === 200) {
    token = JSON.parse(resp.body).accessToken
  }
  return token
}

export function setup() {
  const jwt = getToken()
  const devicesResp = http.get(`${BASE_URL}/api/v1/devices`, {
    headers: { Authorization: `Bearer ${jwt}` }
  })
  const devices = JSON.parse(devicesResp.body)
  return { deviceIds: devices.map(d => d.id), token: jwt }
}

export default function (data) {
  if (!data.deviceIds || data.deviceIds.length === 0) {
    sleep(1)
    return
  }

  const deviceId = data.deviceIds[Math.floor(Math.random() * data.deviceIds.length)]

  const start = Date.now()
  const resp = http.get(`${BASE_URL}/api/v1/telemetry/${deviceId}/cache`, {
    headers: { Authorization: `Bearer ${data.token}` },
    tags: { endpoint: 'telemetry-cache' }
  })
  telemetryLatency.add(Date.now() - start)

  const ok = check(resp, {
    'status is 200': r => r.status === 200,
    'response time < 200ms': r => r.timings.duration < 200
  })

  successRate.add(ok)
  if (!ok) failedRequests.add(1)

  sleep(0.001) // 1ms between VU iterations
}

export function handleSummary(data) {
  const p95 = data.metrics.http_req_duration?.values?.['p(95)']
  const p99 = data.metrics.http_req_duration?.values?.['p(99)']
  const rps = data.metrics.http_reqs?.values?.rate

  console.log('\n=== Sentinel IoT Load Test Summary ===')
  console.log(`Peak RPS:        ${rps?.toFixed(0)} req/sec`)
  console.log(`p95 latency:     ${p95?.toFixed(0)}ms`)
  console.log(`p99 latency:     ${p99?.toFixed(0)}ms`)
  console.log(`Success rate:    ${(data.metrics.success_rate?.values?.rate * 100).toFixed(2)}%`)
  console.log('======================================\n')

  return {
    'tests/load/results.json': JSON.stringify(data, null, 2)
  }
}
