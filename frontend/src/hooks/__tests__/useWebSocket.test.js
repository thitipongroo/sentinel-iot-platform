import { renderHook, act } from '@testing-library/react'
import { useWebSocket } from '@/hooks/useWebSocket'

let mockWsInstance
class MockWebSocket {
  constructor(url) {
    this.url = url
    this.onopen = null
    this.onmessage = null
    this.onclose = null
    this.onerror = null
    this.close = jest.fn()
    mockWsInstance = this
  }
}

beforeEach(() => {
  jest.useFakeTimers()
  global.WebSocket = MockWebSocket
  mockWsInstance = null
})

afterEach(() => {
  jest.useRealTimers()
})

test('connects on mount with provided URL', () => {
  renderHook(() => useWebSocket('ws://test-host/ws'))
  expect(mockWsInstance).not.toBeNull()
  expect(mockWsInstance.url).toBe('ws://test-host/ws')
})

test('status is CONNECTED after onopen fires', () => {
  const { result } = renderHook(() => useWebSocket('ws://test-host/ws'))
  act(() => { mockWsInstance.onopen() })
  expect(result.current.status).toBe('CONNECTED')
})

test('parses JSON message from onmessage', () => {
  const { result } = renderHook(() => useWebSocket('ws://test-host/ws'))
  act(() => { mockWsInstance.onmessage({ data: '{"t":1,"temp":72.4}' }) })
  expect(result.current.lastMessage).toEqual({ t: 1, temp: 72.4 })
})

test('stores raw string if JSON parse fails', () => {
  const { result } = renderHook(() => useWebSocket('ws://test-host/ws'))
  act(() => { mockWsInstance.onmessage({ data: 'not-valid-json' }) })
  expect(result.current.lastMessage).toBe('not-valid-json')
})

test('status is RECONNECTING after onclose fires', () => {
  const { result } = renderHook(() => useWebSocket('ws://test-host/ws'))
  act(() => { mockWsInstance.onopen() })
  act(() => { mockWsInstance.onclose() })
  expect(result.current.status).toBe('RECONNECTING')
})

test('schedules reconnect setTimeout after onclose', () => {
  const setSpy = jest.spyOn(global, 'setTimeout')
  renderHook(() => useWebSocket('ws://test-host/ws'))
  act(() => { mockWsInstance.onclose() })
  expect(setSpy).toHaveBeenCalled()
})

test('cleans up WebSocket and timer on unmount', () => {
  const clearSpy = jest.spyOn(global, 'clearTimeout')
  const { unmount } = renderHook(() => useWebSocket('ws://test-host/ws'))
  act(() => { mockWsInstance.onclose() })
  unmount()
  expect(mockWsInstance.close).toHaveBeenCalled()
  expect(clearSpy).toHaveBeenCalled()
})
