import { getAccessToken, setAccessToken, clearAccessToken } from '@/lib/tokenStore'

beforeEach(() => {
  clearAccessToken()
})

test('getAccessToken returns null initially', () => {
  expect(getAccessToken()).toBeNull()
})

test('setAccessToken stores token in memory', () => {
  setAccessToken('my-token-abc')
  expect(getAccessToken()).toBe('my-token-abc')
})

test('clearAccessToken removes token', () => {
  setAccessToken('my-token-abc')
  clearAccessToken()
  expect(getAccessToken()).toBeNull()
})

test('token is not stored in localStorage', () => {
  setAccessToken('my-token-abc')
  const stored = Object.values(localStorage).find((v) => v === 'my-token-abc')
  expect(stored).toBeUndefined()
})
