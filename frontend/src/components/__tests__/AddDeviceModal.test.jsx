import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import AddDeviceModal from '@/components/AddDeviceModal'

beforeEach(() => jest.clearAllMocks())

test('renders modal heading', () => {
  render(<AddDeviceModal onClose={() => {}} onCreate={() => {}} />)
  expect(screen.getByText('Register New Device')).toBeInTheDocument()
})

test('renders Name, Description and Location fields', () => {
  render(<AddDeviceModal onClose={() => {}} onCreate={() => {}} />)
  expect(screen.getByPlaceholderText('sensor-101')).toBeInTheDocument()
  expect(screen.getByPlaceholderText('Assembly line sensor')).toBeInTheDocument()
  expect(screen.getByPlaceholderText('Building 3 — Zone A')).toBeInTheDocument()
})

test('cancel button calls onClose', async () => {
  const onClose = jest.fn()
  render(<AddDeviceModal onClose={onClose} onCreate={() => {}} />)
  await userEvent.click(screen.getByRole('button', { name: /cancel/i }))
  expect(onClose).toHaveBeenCalled()
})

test('clicking the backdrop calls onClose', () => {
  const onClose = jest.fn()
  const { container } = render(<AddDeviceModal onClose={onClose} onCreate={() => {}} />)
  fireEvent.click(container.firstChild) // backdrop div
  expect(onClose).toHaveBeenCalled()
})

test('submitting with empty name shows validation error', async () => {
  render(<AddDeviceModal onClose={() => {}} onCreate={() => {}} />)
  await userEvent.click(screen.getByRole('button', { name: /register/i }))
  expect(screen.getByText('Name is required')).toBeInTheDocument()
})

test('submitting valid form calls onCreate with form values', async () => {
  const onCreate = jest.fn().mockResolvedValue(undefined)
  render(<AddDeviceModal onClose={() => {}} onCreate={onCreate} />)

  await userEvent.type(screen.getByPlaceholderText('sensor-101'), 'my-sensor')
  await userEvent.type(screen.getByPlaceholderText('Assembly line sensor'), 'Test desc')
  await userEvent.click(screen.getByRole('button', { name: /register/i }))

  expect(onCreate).toHaveBeenCalledWith({
    name: 'my-sensor',
    description: 'Test desc',
    location: '',
  })
})

test('successful onCreate calls onClose', async () => {
  const onCreate = jest.fn().mockResolvedValue(undefined)
  const onClose  = jest.fn()
  render(<AddDeviceModal onClose={onClose} onCreate={onCreate} />)

  await userEvent.type(screen.getByPlaceholderText('sensor-101'), 'my-sensor')
  await userEvent.click(screen.getByRole('button', { name: /register/i }))

  await waitFor(() => expect(onClose).toHaveBeenCalled())
})

test('onCreate rejection with response message shows that message', async () => {
  const onCreate = jest.fn().mockRejectedValue({ response: { data: { message: 'Duplicate name' } } })
  render(<AddDeviceModal onClose={() => {}} onCreate={onCreate} />)

  await userEvent.type(screen.getByPlaceholderText('sensor-101'), 'my-sensor')
  await userEvent.click(screen.getByRole('button', { name: /register/i }))

  await waitFor(() => expect(screen.getByText('Duplicate name')).toBeInTheDocument())
})

test('onCreate rejection without response falls back to generic message', async () => {
  const onCreate = jest.fn().mockRejectedValue(new Error('network error'))
  render(<AddDeviceModal onClose={() => {}} onCreate={onCreate} />)

  await userEvent.type(screen.getByPlaceholderText('sensor-101'), 'my-sensor')
  await userEvent.click(screen.getByRole('button', { name: /register/i }))

  await waitFor(() => expect(screen.getByText('Failed to create device')).toBeInTheDocument())
})

test('Register button shows Registering… while loading', async () => {
  let resolve
  const onCreate = jest.fn().mockReturnValue(new Promise(r => { resolve = r }))
  render(<AddDeviceModal onClose={() => {}} onCreate={onCreate} />)

  await userEvent.type(screen.getByPlaceholderText('sensor-101'), 'sensor-x')
  await userEvent.click(screen.getByRole('button', { name: /register/i }))

  expect(screen.getByRole('button', { name: /registering/i })).toBeDisabled()
  resolve()
})
