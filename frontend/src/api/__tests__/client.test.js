import api from '@/api/client'
import { setAccessToken, clearAccessToken, getAccessToken } from '@/lib/tokenStore'

// Access the registered interceptor handler functions directly
const reqHandler    = () => api.interceptors.request.handlers[0].fulfilled
const resHandler    = () => api.interceptors.response.handlers[0].fulfilled
const resErrHandler = () => api.interceptors.response.handlers[0].rejected

beforeEach(() => {
  clearAccessToken()
})

// ── Request interceptor ────────────────────────────────────────────────────────

test('adds Authorization header when token exists', () => {
  setAccessToken('test-token-xyz')
  const config = { headers: {} }
  const result = reqHandler()(config)
  expect(result.headers.Authorization).toBe('Bearer test-token-xyz')
})

test('does not add Authorization header when no token', () => {
  const config = { headers: {} }
  const result = reqHandler()(config)
  expect(result.headers.Authorization).toBeUndefined()
})

// ── Response interceptor — success path ───────────────────────────────────────

test('dispatches api-version-mismatch event when version differs', () => {
  const listener = jest.fn()
  window.addEventListener('sentinel:api-version-mismatch', listener)
  resHandler()({ headers: { 'api-version': '2' }, data: {} })
  window.removeEventListener('sentinel:api-version-mismatch', listener)
  expect(listener).toHaveBeenCalled()
})

test('does not dispatch event when version matches', () => {
  const listener = jest.fn()
  window.addEventListener('sentinel:api-version-mismatch', listener)
  resHandler()({ headers: { 'api-version': '1' }, data: {} })
  window.removeEventListener('sentinel:api-version-mismatch', listener)
  expect(listener).not.toHaveBeenCalled()
})

// ── Response interceptor — error path ─────────────────────────────────────────

test('clears access token on 401 response', async () => {
  // Suppress jsdom's "not implemented: navigation" console warning
  jest.spyOn(console, 'error').mockImplementation(() => {})
  setAccessToken('some-token')
  const err = { response: { status: 401 } }
  await expect(resErrHandler()(err)).rejects.toEqual(err)
  expect(getAccessToken()).toBeNull()
  console.error.mockRestore()
})

test('dispatches api-version-rejected event on 406 response', async () => {
  const listener = jest.fn()
  window.addEventListener('sentinel:api-version-rejected', listener)
  const err = { response: { status: 406 } }
  await expect(resErrHandler()(err)).rejects.toEqual(err)
  window.removeEventListener('sentinel:api-version-rejected', listener)
  expect(listener).toHaveBeenCalled()
})
