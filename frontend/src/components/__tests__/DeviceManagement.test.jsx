import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import DeviceManagement from '@/components/DeviceManagement'
import { devicesApi } from '@/api/client'

jest.mock('@/api/client', () => ({
  devicesApi: {
    updateLifecycle: jest.fn(),
    updateFirmware:  jest.fn(),
  },
}))

const ACTIVE_DEVICE = {
  id:              'd1',
  name:            'sensor-alpha',
  lifecycleStatus: 'ACTIVE',
  firmwareVersion: '1.0.0',
}

beforeEach(() => jest.clearAllMocks())

test('renders nothing when device is not provided', () => {
  const { container } = render(<DeviceManagement onUpdate={() => {}} />)
  expect(container.firstChild).toBeNull()
})

test('renders device name in heading', () => {
  render(<DeviceManagement device={ACTIVE_DEVICE} onUpdate={() => {}} />)
  expect(screen.getByRole('heading')).toHaveTextContent('sensor-alpha')
})

test('shows current lifecycle status', () => {
  render(<DeviceManagement device={ACTIVE_DEVICE} onUpdate={() => {}} />)
  // The current status span
  expect(screen.getByText('ACTIVE')).toBeInTheDocument()
})

test('shows transition buttons for all statuses except the current one', () => {
  render(<DeviceManagement device={ACTIVE_DEVICE} onUpdate={() => {}} />)
  expect(screen.getByRole('button', { name: '→ PROVISIONED' })).toBeInTheDocument()
  expect(screen.getByRole('button', { name: '→ INACTIVE' })).toBeInTheDocument()
  expect(screen.getByRole('button', { name: '→ DECOMMISSIONED' })).toBeInTheDocument()
  expect(screen.queryByRole('button', { name: '→ ACTIVE' })).not.toBeInTheDocument()
})

test('all lifecycle buttons are disabled for a decommissioned device', () => {
  const device = { ...ACTIVE_DEVICE, lifecycleStatus: 'DECOMMISSIONED' }
  render(<DeviceManagement device={device} onUpdate={() => {}} />)
  const lifecycleBtns = screen.getAllByRole('button').filter(b => b.textContent.startsWith('→'))
  expect(lifecycleBtns.length).toBeGreaterThan(0)
  lifecycleBtns.forEach(btn => expect(btn).toBeDisabled())
})

test('clicking lifecycle button calls updateLifecycle and onUpdate', async () => {
  devicesApi.updateLifecycle.mockResolvedValue({})
  const onUpdate = jest.fn()
  render(<DeviceManagement device={ACTIVE_DEVICE} onUpdate={onUpdate} />)

  await userEvent.click(screen.getByRole('button', { name: '→ INACTIVE' }))

  await waitFor(() => {
    expect(devicesApi.updateLifecycle).toHaveBeenCalledWith('d1', 'INACTIVE')
    expect(onUpdate).toHaveBeenCalled()
  })
})

test('lifecycle failure shows error message', async () => {
  devicesApi.updateLifecycle.mockRejectedValue({ response: { data: { message: 'Transition not allowed' } } })
  render(<DeviceManagement device={ACTIVE_DEVICE} onUpdate={() => {}} />)

  await userEvent.click(screen.getByRole('button', { name: '→ INACTIVE' }))

  await waitFor(() => expect(screen.getByText('Transition not allowed')).toBeInTheDocument())
})

test('invalid semver shows validation error and does not call API', async () => {
  render(<DeviceManagement device={ACTIVE_DEVICE} onUpdate={() => {}} />)
  await userEvent.type(screen.getByPlaceholderText('e.g. 1.2.3'), 'not-valid')
  await userEvent.click(screen.getByRole('button', { name: /update/i }))

  expect(screen.getByText(/semver/i)).toBeInTheDocument()
  expect(devicesApi.updateFirmware).not.toHaveBeenCalled()
})

test('valid semver calls updateFirmware and onUpdate', async () => {
  devicesApi.updateFirmware.mockResolvedValue({})
  const onUpdate = jest.fn()
  render(<DeviceManagement device={ACTIVE_DEVICE} onUpdate={onUpdate} />)

  await userEvent.type(screen.getByPlaceholderText('e.g. 1.2.3'), '2.1.0')
  await userEvent.click(screen.getByRole('button', { name: /update/i }))

  await waitFor(() => {
    expect(devicesApi.updateFirmware).toHaveBeenCalledWith('d1', '2.1.0')
    expect(onUpdate).toHaveBeenCalled()
  })
})

test('firmware Update button is disabled when input is empty', () => {
  render(<DeviceManagement device={ACTIVE_DEVICE} onUpdate={() => {}} />)
  expect(screen.getByRole('button', { name: /update/i })).toBeDisabled()
})

test('shows current firmware version', () => {
  render(<DeviceManagement device={ACTIVE_DEVICE} onUpdate={() => {}} />)
  expect(screen.getByText('1.0.0')).toBeInTheDocument()
})

test('shows unknown for missing firmware version', () => {
  const device = { ...ACTIVE_DEVICE, firmwareVersion: null }
  render(<DeviceManagement device={device} onUpdate={() => {}} />)
  expect(screen.getByText('unknown')).toBeInTheDocument()
})
