import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
  timeout: 10000
})

api.interceptors.request.use(config => {
  if (typeof window !== 'undefined') {
    const token = localStorage.getItem('sentinel_token')
    if (token) config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

api.interceptors.response.use(
  res => res,
  err => {
    if (err.response?.status === 401 && typeof window !== 'undefined') {
      localStorage.removeItem('sentinel_token')
      window.location.href = '/login'
    }
    return Promise.reject(err)
  }
)

export const authApi = {
  login:   (username, password) => api.post('/auth/login', { username, password }),
  refresh: (refreshToken)       => api.post('/auth/refresh', { refreshToken }),
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

export default api
