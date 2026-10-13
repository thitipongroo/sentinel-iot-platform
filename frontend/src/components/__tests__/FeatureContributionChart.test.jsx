import { render, screen } from '@testing-library/react'
import FeatureContributionChart from '@/components/predictive-ai/FeatureContributionChart'

jest.mock('recharts', () => ({
  ResponsiveContainer: ({ children }) => <div data-testid="chart-container">{children}</div>,
  BarChart: ({ children }) => <div>{children}</div>,
  Bar: () => null,
  XAxis: () => null,
  YAxis: () => null,
  Tooltip: () => null,
  Cell: () => null,
}))

test('renders nominal message when score is below 0.65', () => {
  render(<FeatureContributionChart contributions={{}} anomalyScore={0.15} />)

  expect(screen.getByText('Explainable AI (XAI)')).toBeInTheDocument()
  expect(screen.getByText('All Features Operating Nominally')).toBeInTheDocument()
  expect(screen.getByText('15.0%')).toBeInTheDocument()
})

test('renders chart container and elevated anomaly score when score is >= 0.65', () => {
  const contributions = {
    temperature: 55.4,
    humidity: 25.1,
    smokePpm: 19.5
  }

  render(<FeatureContributionChart contributions={contributions} anomalyScore={0.88} />)

  expect(screen.getByTestId('chart-container')).toBeInTheDocument()
  expect(screen.getByText('88.0%')).toBeInTheDocument()
})
