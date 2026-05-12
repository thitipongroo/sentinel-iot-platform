import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import DeviceTable from '@/components/DeviceTable'
import { useStore } from '@/lib/store'

// Render all items regardless of container height
jest.mock('@tanstack/react-virtual', () => ({
  useVirtualizer: ({ count, estimateSize }) => ({
    getVirtualItems: () =>
      Array.from({ length: count }, (_, i) => ({
        index: i,
        start: i * estimateSize(),
        size: estimateSize(),
        key: i,
      })),
    getTotalSize: () => count * estimateSize(),
  }),
}))

const DEFAULT_FILTERS = { search: '', status: 'ALL', lifecycle: 'ALL', sortBy: 'name', sortDir: 'asc' }

beforeEach(() => {
  useStore.setState({ selectedDeviceId: null, filters: { ...DEFAULT_FILTERS }, isOffline: false })
})

const DEVICES = [
  { id: 'd1', name: 'alpha-sensor', status: 'ONLINE',  lifecycleStatus: 'ACTIVE',       location: 'Factory A', firmwareVersion: '1.0.0', lastSeen: new Date().toISOString() },
  { id: 'd2', name: 'beta-sensor',  status: 'OFFLINE', lifecycleStatus: 'ACTIVE',       location: 'Factory B', firmwareVersion: '1.0.0', lastSeen: null },
  { id: 'd3', name: 'gamma-device', status: 'ONLINE',  lifecycleStatus: 'INACTIVE',     location: 'Factory A', firmwareVersion: '1.1.0', lastSeen: new Date().toISOString() },
  { id: 'd4', name: 'delta-unit',   status: 'ONLINE',  lifecycleStatus: 'PROVISIONED',  location: 'Factory C', firmwareVersion: '2.0.0', lastSeen: new Date().toISOString() },
  { id: 'd5', name: 'omega-node',   status: 'OFFLINE', lifecycleStatus: 'DECOMMISSIONED', location: 'Factory C', firmwareVersion: '0.9.0', lastSeen: null },
]

test('renders empty state when no devices', () => {
  render(<DeviceTable devices={[]} onSelect={() => {}} />)
  expect(screen.getByText('No devices registered')).toBeInTheDocument()
})

test('renders visible device names', () => {
  render(<DeviceTable devices={DEVICES} onSelect={() => {}} />)
  expect(screen.getByText('alpha-sensor')).toBeInTheDocument()
  expect(screen.getByText('beta-sensor')).toBeInTheDocument()
  expect(screen.getByText('gamma-device')).toBeInTheDocument()
})

test('search filter narrows results', async () => {
  render(<DeviceTable devices={DEVICES} onSelect={() => {}} />)
  const input = screen.getByRole('searchbox')
  await userEvent.type(input, 'alpha')
  expect(screen.getByText('alpha-sensor')).toBeInTheDocument()
  expect(screen.queryByText('beta-sensor')).not.toBeInTheDocument()
})

test('status filter shows only ONLINE devices', async () => {
  render(<DeviceTable devices={DEVICES} onSelect={() => {}} />)
  const select = screen.getByRole('combobox', { name: /status/i })
  await userEvent.selectOptions(select, 'ONLINE')
  expect(screen.getByText('alpha-sensor')).toBeInTheDocument()
  expect(screen.queryByText('beta-sensor')).not.toBeInTheDocument()
})

test('lifecycle filter shows only ACTIVE devices', async () => {
  render(<DeviceTable devices={DEVICES} onSelect={() => {}} />)
  const select = screen.getByRole('combobox', { name: /lifecycle/i })
  await userEvent.selectOptions(select, 'ACTIVE')
  expect(screen.getByText('alpha-sensor')).toBeInTheDocument()
  expect(screen.getByText('beta-sensor')).toBeInTheDocument()
  expect(screen.queryByText('gamma-device')).not.toBeInTheDocument()
})

test('clear button resets all filters', async () => {
  render(<DeviceTable devices={DEVICES} onSelect={() => {}} />)
  const input = screen.getByRole('searchbox')
  await userEvent.type(input, 'alpha')
  expect(screen.queryByText('beta-sensor')).not.toBeInTheDocument()
  await userEvent.click(screen.getByRole('button', { name: /clear/i }))
  expect(screen.getByText('beta-sensor')).toBeInTheDocument()
})

test('device count label updates with filter', async () => {
  render(<DeviceTable devices={DEVICES} onSelect={() => {}} />)
  expect(screen.getByText(`5 of 5 devices`)).toBeInTheDocument()
  const select = screen.getByRole('combobox', { name: /status/i })
  await userEvent.selectOptions(select, 'ONLINE')
  expect(screen.getByText(`3 of 5 devices`)).toBeInTheDocument()
})

test('clicking device row calls onSelect with device', async () => {
  const onSelect = jest.fn()
  render(<DeviceTable devices={DEVICES} onSelect={onSelect} />)
  await userEvent.click(screen.getByText('alpha-sensor'))
  expect(onSelect).toHaveBeenCalledWith(DEVICES[0])
})

test('Enter key on row calls onSelect', async () => {
  const onSelect = jest.fn()
  render(<DeviceTable devices={DEVICES} onSelect={onSelect} />)
  const row = screen.getByText('alpha-sensor').closest('[role="row"]')
  row.focus()
  await userEvent.keyboard('{Enter}')
  expect(onSelect).toHaveBeenCalledWith(DEVICES[0])
})

test('selected row has aria-selected=true', () => {
  render(<DeviceTable devices={DEVICES} selected={DEVICES[0]} onSelect={() => {}} />)
  const row = screen.getByText('alpha-sensor').closest('[role="row"]')
  expect(row).toHaveAttribute('aria-selected', 'true')
})

test('WebSocket message overrides device status to ONLINE', () => {
  const wsMsg = { deviceId: 'beta-sensor', temperature: 50, humidity: 40 }
  render(<DeviceTable devices={DEVICES} onSelect={() => {}} lastMessage={wsMsg} />)
  // beta-sensor is OFFLINE in DB but WS message sets it ONLINE
  const betaRow = screen.getByText('beta-sensor').closest('[role="row"]')
  expect(within(betaRow).getByText('ONLINE')).toBeInTheDocument()
})

test('shows no-match state when filters produce no results', async () => {
  render(<DeviceTable devices={DEVICES} onSelect={() => {}} />)
  const input = screen.getByRole('searchbox')
  await userEvent.type(input, 'zzznomatch')
  expect(screen.getByText('No devices match the filters')).toBeInTheDocument()
})
