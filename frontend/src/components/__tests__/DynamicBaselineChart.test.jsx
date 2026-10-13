import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import DynamicBaselineChart from '@/components/predictive-ai/DynamicBaselineChart'

jest.mock('recharts', () => ({
  ResponsiveContainer: ({ children }) => <div data-testid="chart-container">{children}</div>,
  ComposedChart: ({ children }) => <div>{children}</div>,
  Line: () => null,
  Area: () => null,
  XAxis: () => null,
  YAxis: () => null,
  Tooltip: () => null,
  Legend: () => null,
  ReferenceArea: () => null,
}))

const BASELINES = {
  TEMPERATURE: {
    sensorKey: 'TEMPERATURE',
    mean: 25.4,
    stdDev: 2.1,
    lowerBand: 19.1,
    upperBand: 31.7,
    sampleCount: 50,
    lastObservedValue: 26.0,
    zScore: 0.28,
    breached: false,
  },
  HUMIDITY: {
    sensorKey: 'HUMIDITY',
    mean: 50.0,
    stdDev: 5.0,
    lowerBand: 35.0,
    upperBand: 65.0,
    sampleCount: 50,
    lastObservedValue: 15.0,
    zScore: -7.0,
    breached: true,
  }
}

const TELEMETRY = [
  { timestamp: new Date().toISOString(), temperature: 25.0, humidity: 50.0, smokePpm: 1.0 },
  { timestamp: new Date().toISOString(), temperature: 26.0, humidity: 48.0, smokePpm: 1.2 },
]

test('renders dynamic baseline envelope heading and statistical pills', () => {
  render(
    <DynamicBaselineChart
      telemetry={TELEMETRY}
      baselines={BASELINES}
      selectedSensor="TEMPERATURE"
      onSelectSensor={jest.fn()}
    />
  )

  expect(screen.getByText('Dynamic Baseline Envelope')).toBeInTheDocument()
  expect(screen.getByText('25.40 °C')).toBeInTheDocument()
  expect(screen.getByText('±2.10 °C')).toBeInTheDocument()
  expect(screen.getByText('19.1 – 31.7 °C')).toBeInTheDocument()
  expect(screen.getByText(/Nominal/)).toBeInTheDocument()
})

test('switches sensor when clicking tab buttons', async () => {
  const onSelect = jest.fn()
  render(
    <DynamicBaselineChart
      telemetry={TELEMETRY}
      baselines={BASELINES}
      selectedSensor="TEMPERATURE"
      onSelectSensor={onSelect}
    />
  )

  const humBtn = screen.getByRole('button', { name: 'HUMIDITY' })
  await userEvent.click(humBtn)
  expect(onSelect).toHaveBeenCalledWith('HUMIDITY')
})

test('displays BREACH badge when baseline is breached', () => {
  render(
    <DynamicBaselineChart
      telemetry={TELEMETRY}
      baselines={BASELINES}
      selectedSensor="HUMIDITY"
      onSelectSensor={jest.fn()}
    />
  )

  expect(screen.getByText(/BREACH/)).toBeInTheDocument()
})
