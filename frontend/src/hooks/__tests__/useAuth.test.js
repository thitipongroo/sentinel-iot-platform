import { renderHook, act, waitFor } from '@testing-library/react'
import { AuthProvider, useAuth } from '@/hooks/useAuth'
import { authApi } from '@/api/client'
import { getAccessToken } from '@/lib/tokenStore'

jest.mock('@/api/client', () => ({
  authApi: {
    refresh: jest.fn(),
    login:   jest.fn(),
    logout:  jest.fn(),
  },
}))

const wrapper = ({ children }) => <AuthProvider>{children}</AuthProvider>

beforeEach(() => jest.clearAllMocks())

test('starts with loading=true then resolves to loading=false', async () => {
  authApi.refresh.mockResolvedValue({ data: { accessToken: 't', username: 'admin', role: 'ADMIN' } })
  const { result } = renderHook(() => useAuth(), { wrapper })
  expect(result.current.loading).toBe(true)
  await waitFor(() => expect(result.current.loading).toBe(false))
})

test('sets user on successful silent refresh', async () => {
  authApi.refresh.mockResolvedValue({ data: { accessToken: 'tok', username: 'alice', role: 'ADMIN' } })
  const { result } = renderHook(() => useAuth(), { wrapper })
  await waitFor(() => expect(result.current.loading).toBe(false))
  expect(result.current.user).toEqual({ username: 'alice', role: 'ADMIN' })
})

test('stays logged out if refresh fails', async () => {
  authApi.refresh.mockRejectedValue(new Error('401'))
  const { result } = renderHook(() => useAuth(), { wrapper })
  await waitFor(() => expect(result.current.loading).toBe(false))
  expect(result.current.user).toBeNull()
})

test('login() sets user and stores access token', async () => {
  authApi.refresh.mockRejectedValue(new Error('401'))
  authApi.login.mockResolvedValue({ data: { accessToken: 'new-tok', username: 'bob', role: 'OPERATOR' } })
  const { result } = renderHook(() => useAuth(), { wrapper })
  await waitFor(() => expect(result.current.loading).toBe(false))
  await act(async () => { await result.current.login('bob', 'pass') })
  expect(result.current.user).toEqual({ username: 'bob', role: 'OPERATOR' })
  expect(getAccessToken()).toBe('new-tok')
})

test('login() throws on invalid credentials', async () => {
  authApi.refresh.mockRejectedValue(new Error('401'))
  authApi.login.mockRejectedValue({ response: { status: 401 } })
  const { result } = renderHook(() => useAuth(), { wrapper })
  await waitFor(() => expect(result.current.loading).toBe(false))
  await expect(act(async () => { await result.current.login('bad', 'wrong') })).rejects.toBeDefined()
})

test('logout() clears user and access token', async () => {
  authApi.refresh.mockResolvedValue({ data: { accessToken: 'tok', username: 'admin', role: 'ADMIN' } })
  authApi.logout.mockResolvedValue({})
  const { result } = renderHook(() => useAuth(), { wrapper })
  await waitFor(() => expect(result.current.user).not.toBeNull())
  await act(async () => { await result.current.logout() })
  expect(result.current.user).toBeNull()
  expect(getAccessToken()).toBeNull()
})

test('logout() clears state even if API call fails', async () => {
  authApi.refresh.mockResolvedValue({ data: { accessToken: 'tok', username: 'admin', role: 'ADMIN' } })
  authApi.logout.mockRejectedValue(new Error('network error'))
  const { result } = renderHook(() => useAuth(), { wrapper })
  await waitFor(() => expect(result.current.user).not.toBeNull())
  await act(async () => { await result.current.logout() })
  expect(result.current.user).toBeNull()
})

test('useAuth returns null outside AuthProvider', () => {
  const { result } = renderHook(() => useAuth())
  expect(result.current).toBeNull()
})
