import { useStore } from '@/lib/store'

const DEFAULT_FILTERS = { search: '', status: 'ALL', lifecycle: 'ALL', sortBy: 'name', sortDir: 'asc' }

beforeEach(() => {
  useStore.setState({ selectedDeviceId: null, filters: { ...DEFAULT_FILTERS }, isOffline: false })
})

test('initial state is correct', () => {
  const { selectedDeviceId, filters, isOffline } = useStore.getState()
  expect(selectedDeviceId).toBeNull()
  expect(filters).toEqual(DEFAULT_FILTERS)
  expect(isOffline).toBe(false)
})

test('setSelectedDeviceId updates selected', () => {
  useStore.getState().setSelectedDeviceId('device-abc')
  expect(useStore.getState().selectedDeviceId).toBe('device-abc')
})

test('setFilter updates specific filter key', () => {
  useStore.getState().setFilter('status', 'ONLINE')
  expect(useStore.getState().filters.status).toBe('ONLINE')
})

test('setFilter does not affect other keys', () => {
  useStore.getState().setFilter('status', 'ONLINE')
  const { filters } = useStore.getState()
  expect(filters.search).toBe('')
  expect(filters.lifecycle).toBe('ALL')
  expect(filters.sortBy).toBe('name')
})

test('resetFilters restores defaults', () => {
  useStore.getState().setFilter('status', 'ONLINE')
  useStore.getState().setFilter('search', 'sensor')
  useStore.getState().setFilter('lifecycle', 'ACTIVE')
  useStore.getState().resetFilters()
  expect(useStore.getState().filters).toEqual(DEFAULT_FILTERS)
})

test('setOffline sets isOffline to true', () => {
  useStore.getState().setOffline(true)
  expect(useStore.getState().isOffline).toBe(true)
})

test('setOffline(false) clears offline state', () => {
  useStore.getState().setOffline(true)
  useStore.getState().setOffline(false)
  expect(useStore.getState().isOffline).toBe(false)
})
