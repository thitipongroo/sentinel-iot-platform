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
  login: (username, password) => api.post('/auth/login', { username, password })
}

export const devicesApi = {
  list: () => api.get('/devices'),
  get: (id) => api.get(`/devices/${id}`),
  create: (data) => api.post('/devices', data)
}

export const telemetryApi = {
  latest: (deviceId, limit = 50) => api.get(`/telemetry/${deviceId}/latest`, { params: { limit } }),
  cached: (deviceId) => api.get(`/telemetry/${deviceId}/cache`),
  stats: () => api.get('/telemetry/stats')
}

export const alertsApi = {
  list: () => api.get('/alerts'),
  unacknowledged: () => api.get('/alerts/unacknowledged'),
  acknowledge: (id) => api.put(`/alerts/${id}/acknowledge`)
}

export default api
