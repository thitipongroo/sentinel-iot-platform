/**
 * Centralised SLO definitions — shared across performance and load scenarios.
 *
 * Source of truth: docs/test-plans/performance-test-plan.md §SLO
 *                  docs/test-plans/load-test-plan.md       §SLO
 *
 * Usage:
 *   import { buildHttpThresholds, buildWsThresholds } from '../common/thresholds.js'
 *   export const options = { thresholds: buildHttpThresholds() }
 */

// ── Per-endpoint P95 / P99 targets (ms) ──────────────────────────────────────

export const SLO = {
  login: {
    p95: 300,
    p99: 500,
    errorRate: 0.001,
  },
  deviceList: {
    p95: 200,
    p99: 400,
    errorRate: 0.001,
  },
  deviceDetail: {
    p95: 200,
    p99: 400,
    errorRate: 0.001,
  },
  telemetryLatest: {
    p95: 300,
    p99: 600,
    errorRate: 0.001,
  },
  telemetryCache: {
    p95: 200,
    p99: 500,
    errorRate: 0.001,
  },
  telemetryRange: {
    p95: 800,
    p99: 1500,
    errorRate: 0.005,
  },
  alertList: {
    p95: 200,
    p99: 400,
    errorRate: 0.001,
  },
  deviceCreate: {
    p95: 400,
    p99: 800,
    errorRate: 0.001,
  },
  deviceLifecycle: {
    p95: 400,
    p99: 800,
    errorRate: 0.001,
  },
}

// WebSocket SLOs (delivery latency from Kafka to client)
export const WS_SLO = {
  broadcast100: { p95: 150, p99: 300 },
  broadcast500: { p95: 300, p99: 500 },
}

// ── Threshold builders ────────────────────────────────────────────────────────

/**
 * Builds k6 thresholds for all HTTP endpoints using tag-scoped metrics.
 * Use `tags: { endpoint: '<name>' }` in each request to enable per-endpoint tracking.
 */
export function buildHttpThresholds() {
  return {
    // Global
    http_req_failed:  ['rate<0.01'],
    success_rate:     ['rate>0.99'],

    // Per-endpoint (k6 tag-scoped thresholds)
    'http_req_duration{endpoint:auth-login}':        [`p(95)<${SLO.login.p95}`,           `p(99)<${SLO.login.p99}`],
    'http_req_duration{endpoint:devices-list}':      [`p(95)<${SLO.deviceList.p95}`,       `p(99)<${SLO.deviceList.p99}`],
    'http_req_duration{endpoint:device-detail}':     [`p(95)<${SLO.deviceDetail.p95}`,     `p(99)<${SLO.deviceDetail.p99}`],
    'http_req_duration{endpoint:telemetry-latest}':  [`p(95)<${SLO.telemetryLatest.p95}`,  `p(99)<${SLO.telemetryLatest.p99}`],
    'http_req_duration{endpoint:telemetry-cache}':   [`p(95)<${SLO.telemetryCache.p95}`,   `p(99)<${SLO.telemetryCache.p99}`],
    'http_req_duration{endpoint:telemetry-range}':   [`p(95)<${SLO.telemetryRange.p95}`,   `p(99)<${SLO.telemetryRange.p99}`],
    'http_req_duration{endpoint:alerts-list}':       [`p(95)<${SLO.alertList.p95}`,        `p(99)<${SLO.alertList.p99}`],
    'http_req_duration{endpoint:device-create}':     [`p(95)<${SLO.deviceCreate.p95}`,     `p(99)<${SLO.deviceCreate.p99}`],
    'http_req_duration{endpoint:device-lifecycle}':  [`p(95)<${SLO.deviceLifecycle.p95}`,  `p(99)<${SLO.deviceLifecycle.p99}`],
  }
}

/**
 * Builds k6 thresholds for WebSocket broadcast latency.
 * Populated by custom Trend metrics in websocket.js.
 */
export function buildWsThresholds() {
  return {
    ws_broadcast_latency_ms: [
      `p(95)<${WS_SLO.broadcast100.p95}`,
      `p(99)<${WS_SLO.broadcast100.p99}`,
    ],
    ws_msgs_received:  ['count>0'],
    http_req_failed:   ['rate<0.01'],
  }
}
