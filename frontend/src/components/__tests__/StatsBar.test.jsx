import { render, screen } from '@testing-library/react'
import StatsBar from '@/components/StatsBar'

const makeDevices = (statuses) =>
  statuses.map((s, i) => ({ id: `d${i}`, name: `sensor-${i}`, status: s }))

const makeAlerts = (entries) =>
  entries.map(([level, acknowledged], i) => ({ id: `a${i}`, level, acknowledged }))

test('shows correct total device count', () => {
  render(<StatsBar devices={makeDevices(['ONLINE', 'ONLINE', 'OFFLINE', 'ONLINE', 'OFFLINE'])} alerts={[]} stats={{}} />)
  expect(screen.getByText('Total Devices')).toBeInTheDocument()
  const card = screen.getByText('Total Devices').closest('div')
  expect(card).toHaveTextContent('5')
})

test('calculates online and offline counts correctly', () => {
  render(<StatsBar devices={makeDevices(['ONLINE', 'ONLINE', 'ONLINE', 'OFFLINE', 'OFFLINE'])} alerts={[]} stats={{}} />)
  const onlineCard = screen.getByText('Online').closest('div')
  const offlineCard = screen.getByText('Offline').closest('div')
  expect(onlineCard).toHaveTextContent('3')
  expect(offlineCard).toHaveTextContent('2')
})

test('shows critical unacknowledged alert count', () => {
  const alerts = makeAlerts([['CRITICAL', false], ['CRITICAL', false], ['WARNING', false]])
  render(<StatsBar devices={[]} alerts={alerts} stats={{}} />)
  const card = screen.getByText('Critical Alerts').closest('div')
  expect(card).toHaveTextContent('2')
})

test('shows 0 for buffered when replayQueueSize is 0', () => {
  render(<StatsBar devices={[]} alerts={[]} stats={{ replayQueueSize: 0 }} />)
  const card = screen.getByText('Buffered').closest('div')
  expect(card).toHaveTextContent('0')
  expect(card.querySelector('p:last-child')).toHaveClass('text-gray-400')
})

test('shows warning color for buffered when replayQueueSize > 0', () => {
  render(<StatsBar devices={[]} alerts={[]} stats={{ replayQueueSize: 5 }} />)
  const card = screen.getByText('Buffered').closest('div')
  expect(card).toHaveTextContent('5')
  expect(card.querySelector('p:last-child')).toHaveClass('text-sentinel-warning')
})

test('shows events per minute from stats.lastMinute', () => {
  render(<StatsBar devices={[]} alerts={[]} stats={{ lastMinute: 42 }} />)
  const card = screen.getByText('Events / min').closest('div')
  expect(card).toHaveTextContent('42')
})
