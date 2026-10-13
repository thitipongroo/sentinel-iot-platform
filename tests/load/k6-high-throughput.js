import http from 'k6/http'
import { check, sleep } from 'k6'
import { Counter, Trend, Rate } from 'k6/metrics'

// Metrics
const successRate = new Rate('success_rate')
const telemetryCacheLatency = new Trend('telemetry_cache_latency', true)
const anomalyInferenceLatency = new Trend('anomaly_inference_latency', true)
const shadowFetchLatency = new Trend('shadow_fetch_latency', true)
const failedRequests = new Counter('failed_requests')

export const options = {
  scenarios: {
    // High-Throughput Ingestion & Inference Load Profile
    high_throughput_burst: {
      executor: 'ramping-arrival-rate',
      startRate: 100,
      timeUnit: '1s',
      preAllocatedVUs: 100,
      maxVUs: 1000,
      stages: [
        { target: 1000, duration: '30s' },  // Ramp to 1,000 req/sec
        { target: 5000, duration: '60s' },  // Ramp to 5,000 req/sec
        { target: 10000, duration: '60s' }, // Peak at 10,000 req/sec
        { target: 10000, duration: '120s' },// Sustain peak 10k req/sec
        { target: 1000, duration: '30s' },  // Scale down
        { target: 0, duration: '15s' }
      ]
    }
  },
  thresholds: {
    // SLO Verification
    'telemetry_cache_latency': ['p(95)<10', 'p(99)<25'],          // Redis cache < 10ms
    'anomaly_inference_latency': ['p(95)<20', 'p(99)<50'],        // Pure-Java ML Isolation Forest < 20ms
    'shadow_fetch_latency': ['p(95)<15', 'p(99)<30'],             // Digital twin OCC read < 15ms
    'http_req_duration': ['p(95)<50', 'p(99)<100'],
    'success_rate': ['rate>0.999'],                               // 99.9% uptime SLA
    'http_req_failed': ['rate<0.001']                             // Error budget error rate < 0.1%
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
  const jwt = getJwtToken()
  const devicesRes = http.get(`${BASE_URL}/api/v1/devices`, {
    headers: { Authorization: `Bearer ${jwt}` }
  })
  let deviceIds = []
  if (devicesRes.status === 200) {
    try {
      const devs = JSON.parse(devicesRes.body)
      deviceIds = devs.map(d => d.id)
    } catch (e) {}
  }
  if (deviceIds.length === 0) {
    deviceIds = ['dddddddd-0000-0000-0000-000000000001']
  }
  return { deviceIds, token: jwt }
}

export default function (data) {
  const deviceId = data.deviceIds[Math.floor(Math.random() * data.deviceIds.length)]
  const headers = {
    Authorization: `Bearer ${data.token}`,
    'Content-Type': 'application/json'
  }

  // Weighted random distribution across hot API paths:
  // 60% Redis Telemetry Cache, 25% AI Anomaly Inference, 15% Digital Twin Shadow
  const dice = Math.random()

  if (dice < 0.60) {
    // Path 1: Hot Telemetry Cache Read
    const t0 = Date.now()
    const res = http.get(`${BASE_URL}/api/v1/telemetry/${deviceId}/cache`, {
      headers,
      tags: { endpoint: 'telemetry-cache' }
    })
    telemetryCacheLatency.add(Date.now() - t0)

    const ok = check(res, { 'cache status 200': r => r.status === 200 })
    successRate.add(ok)
    if (!ok) failedRequests.add(1)

  } else if (dice < 0.85) {
    // Path 2: Realtime ML Isolation Forest Dry-Run Scoring
    const t0 = Date.now()
    const payload = JSON.stringify({
      deviceId,
      temperature: 25.0 + (Math.random() * 10 - 5),
      humidity: 50.0 + (Math.random() * 20 - 10),
      smokePpm: 1.0 + Math.random() * 2
    })

    const res = http.post(`${BASE_URL}/api/v1/analytics/anomalies/evaluate`, payload, {
      headers,
      tags: { endpoint: 'anomalies-evaluate' }
    })
    anomalyInferenceLatency.add(Date.now() - t0)

    const ok = check(res, {
      'inference status 200': r => r.status === 200,
      'has anomaly field': r => r.body && r.body.includes('anomaly')
    })
    successRate.add(ok)
    if (!ok) failedRequests.add(1)

  } else {
    // Path 3: Digital Twin / Device Shadow Fetch
    const t0 = Date.now()
    const res = http.get(`${BASE_URL}/api/v1/devices/${deviceId}/shadow`, {
      headers,
      tags: { endpoint: 'device-shadow' }
    })
    shadowFetchLatency.add(Date.now() - t0)

    const ok = check(res, { 'shadow status 200': r => r.status === 200 })
    successRate.add(ok)
    if (!ok) failedRequests.add(1)
  }

  sleep(0.001)
}

export function handleSummary(data) {
  const p95Total = data.metrics.http_req_duration?.values?.['p(95)']
  const p95Cache = data.metrics.telemetry_cache_latency?.values?.['p(95)']
  const p95Infer = data.metrics.anomaly_inference_latency?.values?.['p(95)']
  const rps = data.metrics.http_reqs?.values?.rate
  const success = (data.metrics.success_rate?.values?.rate * 100).toFixed(3)

  console.log('\n========================================================')
  console.log('⚡ Sentinel IoT High-Throughput (10k RPS) Load Test Report')
  console.log('========================================================')
  console.log(`Peak RPS                  : ${rps?.toFixed(0)} req/sec`)
  console.log(`Success Rate              : ${success}%`)
  console.log(`Overall p95 Latency       : ${p95Total?.toFixed(1)} ms`)
  console.log(`Telemetry Cache p95       : ${p95Cache?.toFixed(1)} ms (<10ms target)`)
  console.log(`ML Anomaly Inference p95  : ${p95Infer?.toFixed(1)} ms (<20ms target)`)
  console.log('========================================================\n')

  return {
    'tests/load/high-throughput-results.json': JSON.stringify(data, null, 2)
  }
}
