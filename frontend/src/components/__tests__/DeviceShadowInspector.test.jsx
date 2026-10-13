import { render, screen } from '@testing-library/react'
import DeviceShadowInspector from '@/components/digital-twin/DeviceShadowInspector'
import { useQuery, useMutation } from '@tanstack/react-query'

jest.mock('@tanstack/react-query', () => ({
  ...jest.requireActual('@tanstack/react-query'),
  useQuery: jest.fn(),
  useMutation: jest.fn(() => ({
    mutate: jest.fn(),
    isPending: false,
  })),
  useQueryClient: () => ({
    setQueryData: jest.fn(),
    invalidateQueries: jest.fn(),
  })
}))

jest.mock('@/api/client', () => ({
  shadowsApi: {
    get: jest.fn(),
    update: jest.fn(),
  },
}))

const DEVICE = { id: 'd-101', name: 'Smart-HVAC-01' }
const MOCK_SHADOW = {
  deviceId: 'd-101',
  version: 14,
  desired: { targetTemperature: 22.0, fanSpeed: 80 },
  reported: { targetTemperature: 22.0, fanSpeed: 50 },
  delta: { fanSpeed: 80 },
  timestamp: new Date().toISOString()
}

test('renders shadow inspector with version and delta highlight', () => {
  useQuery.mockReturnValue({
    data: MOCK_SHADOW,
    isLoading: false,
    error: null,
  })

  render(<DeviceShadowInspector device={DEVICE} userRole="ADMIN" />)

  expect(screen.getByText('Device Twin / Shadow State')).toBeInTheDocument()
  expect(screen.getByText('OCC v14')).toBeInTheDocument()
  expect(screen.getByText(/1 Delta Unsynced/i)).toBeInTheDocument()
  expect(screen.getByText(/State Divergence Detected/)).toBeInTheDocument()
})

test('renders synchronized badge when delta is empty', () => {
  useQuery.mockReturnValue({
    data: {
      ...MOCK_SHADOW,
      reported: { targetTemperature: 22.0, fanSpeed: 80 },
      delta: {}
    },
    isLoading: false,
    error: null,
  })

  render(<DeviceShadowInspector device={DEVICE} userRole="OPERATOR" />)

  expect(screen.getByText(/Fully Synchronized/i)).toBeInTheDocument()
})
