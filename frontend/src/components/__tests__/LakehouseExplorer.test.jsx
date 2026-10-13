import { render, screen } from '@testing-library/react'
import LakehouseExplorer from '@/components/lakehouse/LakehouseExplorer'
import { useQuery } from '@tanstack/react-query'

jest.mock('recharts', () => ({
  ResponsiveContainer: ({ children }) => <div data-testid="chart-container">{children}</div>,
  ComposedChart: ({ children }) => <div>{children}</div>,
  Line: () => null,
  Area: () => null,
  Bar: () => null,
  XAxis: () => null,
  YAxis: () => null,
  Tooltip: () => null,
  Legend: () => null,
}))

jest.mock('@tanstack/react-query', () => ({
  ...jest.requireActual('@tanstack/react-query'),
  useQuery: jest.fn(),
  useMutation: jest.fn(() => ({
    mutate: jest.fn(),
    isPending: false
  }))
}))

const DEVICES = [
  { id: 'dev-1', name: 'Machine-Alpha', location: 'Building A' },
  { id: 'dev-2', name: 'Machine-Beta', location: 'Building B' },
]

const MOCK_QUERY_RESULT = {
  executionTimeMs: 12,
  rowsScanned: 154200,
  data: [
    { bucket: '2026-08-01T00:00:00Z', avgValue: 24.5, minValue: 20.1, maxValue: 30.2, count: 5000 },
    { bucket: '2026-09-01T00:00:00Z', avgValue: 25.1, minValue: 19.8, maxValue: 32.0, count: 5200 },
  ]
}

test('renders Lakehouse Explorer with performance metrics and DuckDB speed badge', () => {
  useQuery.mockReturnValue({
    data: MOCK_QUERY_RESULT,
    isLoading: false,
    isFetching: false,
  })

  render(<LakehouseExplorer devices={DEVICES} userRole="ADMIN" />)

  expect(screen.getByText('DUCKDB ENGINE')).toBeInTheDocument()
  expect(screen.getByText('12 ms')).toBeInTheDocument()
  expect(screen.getByText('154,200')).toBeInTheDocument()
  expect(screen.getByText('STORAGE SAVINGS')).toBeInTheDocument()
  expect(screen.getByText('DuckDB Multi-Year Trend Analysis')).toBeInTheDocument()
  expect(screen.getByTestId('chart-container')).toBeInTheDocument()
  expect(screen.getByText('Cold Storage Parquet Archival Trigger')).toBeInTheDocument()
})

test('renders device filter options', () => {
  useQuery.mockReturnValue({
    data: MOCK_QUERY_RESULT,
    isLoading: false,
    isFetching: false,
  })

  render(<LakehouseExplorer devices={DEVICES} userRole="OPERATOR" />)

  expect(screen.getByText('All Fleet Devices (Aggregated)')).toBeInTheDocument()
  expect(screen.getByText('Machine-Alpha (Building A)')).toBeInTheDocument()
})
