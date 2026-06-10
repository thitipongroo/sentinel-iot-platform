/**
 * Auth helper shared by all performance/load scenarios.
 *
 * Handles:
 *  - Single-user login (admin) for standard scenarios
 *  - Multi-user login for multi-tenant scenarios (one token per org)
 *  - Fetching registered device IDs after login
 */

import http from 'k6/http'
import { check } from 'k6'

export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080'

const JSON_HEADERS = { 'Content-Type': 'application/json' }

/**
 * Logs in and returns the access token.
 * Throws on failure so setup() surfaces the error clearly.
 */
export function login(username, password) {
  const res = http.post(
    `${BASE_URL}/api/v1/auth/login`,
    JSON.stringify({ username, password }),
    { headers: JSON_HEADERS, tags: { endpoint: 'auth-login' } }
  )
  check(res, { 'login 200': r => r.status === 200 })
  if (res.status !== 200) {
    throw new Error(`Login failed for '${username}': HTTP ${res.status} — ${res.body}`)
  }
  return JSON.parse(res.body).accessToken
}

/**
 * Returns all device IDs visible to the given token.
 * Used in setup() to supply VUs with a device pool to query.
 */
export function fetchDeviceIds(token) {
  const res = http.get(`${BASE_URL}/api/v1/devices`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  if (res.status !== 200) return []
  return JSON.parse(res.body).map(d => d.id)
}

/**
 * Builds the standard Authorization header object.
 */
export function authHeaders(token) {
  return { Authorization: `Bearer ${token}` }
}

/**
 * Picks a random element from an array.
 * Used by VUs to select a device ID from the shared pool.
 */
export function pickRandom(arr) {
  return arr[Math.floor(Math.random() * arr.length)]
}
