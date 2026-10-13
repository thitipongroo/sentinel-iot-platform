import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import InferenceSimulator from '@/components/predictive-ai/InferenceSimulator'
import { anomaliesApi } from '@/api/client'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'

jest.mock('@/api/client', () => ({
  anomaliesApi: {
    evaluate: jest.fn(),
  },
}))

const DEVICE_ID = '00000000-0000-0000-0000-000000000001'

function renderWithClient(ui) {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      {ui}
    </QueryClientProvider>
  )
}

beforeEach(() => {
  anomaliesApi.evaluate.mockReset()
})

test('renders simulator controls and presets', () => {
  renderWithClient(<InferenceSimulator deviceId={DEVICE_ID} deviceName="Sensor-01" />)

  expect(screen.getByText('ML Anomaly Dry-Run Simulator')).toBeInTheDocument()
  expect(screen.getByText('Nominal Baseline')).toBeInTheDocument()
  expect(screen.getByText('Thermal Runaway Crisis')).toBeInTheDocument()
  expect(screen.getByRole('button', { name: /run ai inference/i })).toBeInTheDocument()
})

test('applies preset and triggers dry-run evaluation', async () => {
  anomaliesApi.evaluate.mockResolvedValueOnce({
    data: {
      severity: 'CRITICAL',
      anomalyType: 'THERMAL_RUNAWAY',
      anomalyScore: 0.92,
      confidence: 0.98,
      summary: 'Critical Thermal Runaway pattern: High temp, low hum, smoke surge',
      featureContributions: { temperature: 60.0, smokePpm: 35.0 }
    }
  })

  renderWithClient(<InferenceSimulator deviceId={DEVICE_ID} deviceName="Sensor-01" />)

  await userEvent.click(screen.getByText('Thermal Runaway Crisis'))

  await waitFor(() => {
    expect(anomaliesApi.evaluate).toHaveBeenCalledWith(expect.objectContaining({
      deviceId: DEVICE_ID,
      temperature: 88,
      humidity: 11.5,
      smokePpm: 85
    }))
  })

  expect(await screen.findByText('THERMAL_RUNAWAY')).toBeInTheDocument()
  expect(screen.getByText('CRITICAL')).toBeInTheDocument()
  expect(screen.getByText('92.0%')).toBeInTheDocument()
})
