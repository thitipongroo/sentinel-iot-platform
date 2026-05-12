import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import AlertList from '@/components/AlertList'
import { alertsApi } from '@/api/client'

jest.mock('@/api/client', () => ({
  alertsApi: { acknowledge: jest.fn() },
}))

const NOW = new Date().toISOString()

const ALERTS = [
  { id: 'a1', level: 'CRITICAL', message: 'Temp too high', acknowledged: false, createdAt: NOW },
  { id: 'a2', level: 'WARNING',  message: 'Smoke rising',  acknowledged: false, createdAt: NOW },
  { id: 'a3', level: 'WARNING',  message: 'Motion detected', acknowledged: true, createdAt: NOW },
]

beforeEach(() => jest.clearAllMocks())

test('renders all alerts by default', () => {
  render(<AlertList alerts={ALERTS} onAcknowledge={() => {}} userRole="OPERATOR" />)
  expect(screen.getByText('Temp too high')).toBeInTheDocument()
  expect(screen.getByText('Smoke rising')).toBeInTheDocument()
  expect(screen.getByText('Motion detected')).toBeInTheDocument()
})

test('shows empty state when no alerts', () => {
  render(<AlertList alerts={[]} onAcknowledge={() => {}} userRole="OPERATOR" />)
  expect(screen.getByText('No alerts')).toBeInTheDocument()
})

test('shows unacknowledged count badge in header', () => {
  render(<AlertList alerts={ALERTS} onAcknowledge={() => {}} userRole="OPERATOR" />)
  // 2 unacked alerts (a1 and a2)
  const badges = screen.getAllByText('2')
  expect(badges.length).toBeGreaterThan(0)
})

test('filters to unacknowledged when tab clicked', async () => {
  render(<AlertList alerts={ALERTS} onAcknowledge={() => {}} userRole="OPERATOR" />)
  await userEvent.click(screen.getByRole('button', { name: /unacknowledged/i }))
  expect(screen.getByText('Temp too high')).toBeInTheDocument()
  expect(screen.getByText('Smoke rising')).toBeInTheDocument()
  expect(screen.queryByText('Motion detected')).not.toBeInTheDocument()
})

test('shows empty state message in unacked tab when all acked', async () => {
  const allAcked = ALERTS.map(a => ({ ...a, acknowledged: true }))
  render(<AlertList alerts={allAcked} onAcknowledge={() => {}} userRole="OPERATOR" />)
  await userEvent.click(screen.getByRole('button', { name: /unacknowledged/i }))
  expect(screen.getByText('No active alerts')).toBeInTheDocument()
})

test('ADMIN sees Ack button on unacknowledged alert', () => {
  render(<AlertList alerts={ALERTS} onAcknowledge={() => {}} userRole="ADMIN" />)
  const ackButtons = screen.getAllByRole('button', { name: 'Ack' })
  expect(ackButtons.length).toBe(2)
})

test('OPERATOR does not see Ack button', () => {
  render(<AlertList alerts={ALERTS} onAcknowledge={() => {}} userRole="OPERATOR" />)
  expect(screen.queryByRole('button', { name: 'Ack' })).not.toBeInTheDocument()
})

test('clicking Ack calls alertsApi.acknowledge and onAcknowledge callback', async () => {
  alertsApi.acknowledge.mockResolvedValue({})
  const onAcknowledge = jest.fn()
  render(<AlertList alerts={ALERTS} onAcknowledge={onAcknowledge} userRole="ADMIN" />)
  await userEvent.click(screen.getAllByRole('button', { name: 'Ack' })[0])
  await waitFor(() => expect(alertsApi.acknowledge).toHaveBeenCalledWith('a1'))
  expect(onAcknowledge).toHaveBeenCalled()
})

test('CRITICAL alert has danger border styling', () => {
  render(<AlertList alerts={ALERTS} onAcknowledge={() => {}} userRole="OPERATOR" />)
  const criticalRow = screen.getByText('Temp too high').closest('div[class*="border"]')
  expect(criticalRow.className).toMatch(/sentinel-danger/)
})
