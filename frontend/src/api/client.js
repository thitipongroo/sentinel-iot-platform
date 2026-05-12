import axios from 'axios'
import { getAccessToken, clearAccessToken } from '@/lib/tokenStore'

// The backend sets `API-Version: 1` on every response (ApiVersionFilter).
// If the version bumps to a breaking change, this client will surface a warning
// instead of silently rendering stale or broken UI.
const EXPECTED_API_VERSION = '1'

const api = axios.create({
  baseURL: '/api/v1',
  timeout: 10000,
  // withCredentials is required for the HttpOnly refresh-token cookie to be
  // sent automatically on cross-origin requests (dev: :3000 → :8080).
  withCredentials: true,
  headers: {
    // Signal the expected contract version to the backend so it can reject
    // mismatched clients with HTTP 406 in a future breaking-change scenario.
    'Accept-API-Version': EXPECTED_API_VERSION,
  }
})

api.interceptors.request.use(config => {
  const token = getAccessToken()
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

api.interceptors.response.use(
  res => {
    // Detect API contract mismatch before it causes silent data corruption.
    const serverVersion = res.headers?.['api-version']
    if (serverVersion && serverVersion !== EXPECTED_API_VERSION) {
      console.warn(
        `[sentinel] API version mismatch: expected ${EXPECTED_API_VERSION}, ` +
        `got ${serverVersion}. Reload the page to get the latest client.`
      )
      // Emit a custom browser event so the UI can show a "Please refresh" banner.
      if (typeof window !== 'undefined') {
        window.dispatchEvent(
          new CustomEvent('sentinel:api-version-mismatch', {
            detail: { expected: EXPECTED_API_VERSION, actual: serverVersion }
          })
        )
      }
    }
    return res
  },
  err => {
    if (err.response?.status === 401 && typeof window !== 'undefined') {
      clearAccessToken()
      window.location.href = '/login'
    }
    // Surface HTTP 406 (Not Acceptable) when the backend rejects the client version
    if (err.response?.status === 406 && typeof window !== 'undefined') {
      console.error('[sentinel] API contract rejected by server — client is outdated. Please refresh.')
      window.dispatchEvent(new CustomEvent('sentinel:api-version-rejected'))
    }
    return Promise.reject(err)
  }
)

export const authApi = {
  login:   (username, password) => api.post('/auth/login', { username, password }),
  // No body needed — the HttpOnly refresh-token cookie is sent automatically
  refresh: ()                   => api.post('/auth/refresh'),
  logout:  ()                   => api.post('/auth/logout')
}

export const devicesApi = {
  list:            ()              => api.get('/devices'),
  get:             (id)            => api.get(`/devices/${id}`),
  create:          (data)          => api.post('/devices', data),
  updateLifecycle: (id, status)    => api.patch(`/devices/${id}/lifecycle`, { lifecycleStatus: status }),
  updateFirmware:  (id, version)   => api.patch(`/devices/${id}/firmware`, { firmwareVersion: version })
}

export const telemetryApi = {
  latest: (deviceId, limit = 50) =>
    api.get(`/telemetry/${deviceId}/latest`, { params: { limit } }),
  cached: (deviceId) =>
    api.get(`/telemetry/${deviceId}/cache`),
  range: (deviceId, from, to) =>
    api.get(`/telemetry/${deviceId}/range`, { params: { from: from.toISOString(), to: to.toISOString() } }),
  hourly: (deviceId, from, to) =>
    api.get(`/telemetry/${deviceId}/hourly`, { params: { from: from.toISOString(), to: to.toISOString() } }),
  stats: () =>
    api.get('/telemetry/stats')
}

export const alertsApi = {
  list:            ()    => api.get('/alerts'),
  unacknowledged:  ()    => api.get('/alerts/unacknowledged'),
  acknowledge:     (id)  => api.put(`/alerts/${id}/acknowledge`)
}

export const usersApi = {
  list:          ()                        => api.get('/users'),
  create:        (data)                    => api.post('/users', data),
  delete:        (username)                => api.delete(`/users/${username}`),
  changeRole:    (username, role)          => api.patch(`/users/${username}/role`, { role }),
  resetPassword: (username, newPassword)   => api.patch(`/users/${username}/password`, { newPassword }),
}

export const settingsApi = {
  get:    ()     => api.get('/settings'),
  update: (data) => api.patch('/settings', data),
}

export default api
