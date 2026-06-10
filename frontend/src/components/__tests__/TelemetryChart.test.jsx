import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import TelemetryChart from '@/components/TelemetryChart'
import { useQuery } from '@tanstack/react-query'

jest.mock('recharts', () => ({
  ResponsiveContainer: ({ children }) => <div data-testid="chart-container">{children}</div>,
  ComposedChart:       ({ children }) => <div>{children}</div>,
  Line:          () => null,
  Area:          () => null,
  Bar:           () => null,
  XAxis:         () => null,
  YAxis:         () => null,
  CartesianGrid: () => null,
  Tooltip:       () => null,
  Legend:        () => null,
  ReferenceLine: () => null,
}))

jest.mock('@tanstack/react-query', () => ({
  useQuery: jest.fn(),
}))

jest.mock('@/api/client', () => ({
  telemetryApi: {
    hourly: jest.fn(),
    range:  jest.fn(),
  },
}))

const DEVICE    = { id: 'd1', name: 'sensor-alpha' }
const LIVE_DATA = [
  { timestamp: new Date().toISOString(), temperature: 45, humidity: 60, smokePpm: 10, motion: false },
  { timestamp: new Date().toISOString(), temperature: 50, humidity: 55, smokePpm: 15, motion: true  },
]

beforeEach(() => {
  useQuery.mockReturnValue({ data: [], isLoading: false })
})

afterEach(() => jest.clearAllMocks())

test('shows device name in heading', () => {
  render(<TelemetryChart data={[]} device={DEVICE} />)
  expect(screen.getByText('sensor-alpha')).toBeInTheDocument()
})

test('shows "Select a device" when device is null', () => {
  render(<TelemetryChart data={[]} device={null} />)
  expect(screen.getByText('Select a device')).toBeInTheDocument()
})

test('shows "No telemetry data" when live data is empty', () => {
  render(<TelemetryChart data={[]} device={DEVICE} />)
  expect(screen.getByText('No telemetry data')).toBeInTheDocument()
})

test('shows loading indicator when historical query is loading', () => {
  useQuery.mockReturnValue({ data: [], isLoading: true })
  render(<TelemetryChart data={[]} device={DEVICE} />)
  expect(screen.getByText('Loading…')).toBeInTheDocument()
})

test('renders chart container when live data is provided', () => {
  render(<TelemetryChart data={LIVE_DATA} device={DEVICE} />)
  expect(screen.getByTestId('chart-container')).toBeInTheDocument()
})

test('renders time window buttons', () => {
  render(<TelemetryChart data={[]} device={DEVICE} />)
  expect(screen.getByRole('button', { name: 'Live' })).toBeInTheDocument()
  expect(screen.getByRole('button', { name: '1h'   })).toBeInTheDocument()
  expect(screen.getByRole('button', { name: '6h'   })).toBeInTheDocument()
  expect(screen.getByRole('button', { name: '24h'  })).toBeInTheDocument()
  expect(screen.getByRole('button', { name: '7d'   })).toBeInTheDocument()
})

test('renders sensor tab buttons', () => {
  render(<TelemetryChart data={[]} device={DEVICE} />)
  expect(screen.getByRole('button', { name: 'Temperature / Humidity' })).toBeInTheDocument()
  expect(screen.getByRole('button', { name: 'Smoke (ppm)'            })).toBeInTheDocument()
  expect(screen.getByRole('button', { name: 'Motion'                 })).toBeInTheDocument()
})

test('switching to Smoke tab renders chart without crash', async () => {
  render(<TelemetryChart data={LIVE_DATA} device={DEVICE} />)
  await userEvent.click(screen.getByRole('button', { name: 'Smoke (ppm)' }))
  expect(screen.getByTestId('chart-container')).toBeInTheDocument()
})

test('switching to Motion tab renders chart without crash', async () => {
  render(<TelemetryChart data={LIVE_DATA} device={DEVICE} />)
  await userEvent.click(screen.getByRole('button', { name: 'Motion' }))
  expect(screen.getByTestId('chart-container')).toBeInTheDocument()
})
