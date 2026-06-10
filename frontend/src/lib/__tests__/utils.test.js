import { relativeTime } from '@/lib/utils'

test('returns em dash for null', () => {
  expect(relativeTime(null)).toBe('—')
})

test('returns em dash for undefined', () => {
  expect(relativeTime(undefined)).toBe('—')
})

test('returns seconds ago for recent timestamp', () => {
  const ts = new Date(Date.now() - 5_000).toISOString()
  expect(relativeTime(ts)).toBe('5s ago')
})

test('returns minutes ago for timestamp over 60s', () => {
  const ts = new Date(Date.now() - 90_000).toISOString()
  expect(relativeTime(ts)).toBe('2m ago')
})

test('returns hours ago for timestamp over 1h', () => {
  const ts = new Date(Date.now() - 2 * 3_600_000).toISOString()
  expect(relativeTime(ts)).toBe('2h ago')
})

test('returns days ago for timestamp over 24h', () => {
  const ts = new Date(Date.now() - 2 * 86_400_000).toISOString()
  expect(relativeTime(ts)).toBe('2d ago')
})

test('boundary: exactly 59s returns seconds', () => {
  const ts = new Date(Date.now() - 59_000).toISOString()
  expect(relativeTime(ts)).toMatch(/s ago$/)
})

test('boundary: exactly 60s returns minutes', () => {
  const ts = new Date(Date.now() - 60_000).toISOString()
  expect(relativeTime(ts)).toMatch(/m ago$/)
})
