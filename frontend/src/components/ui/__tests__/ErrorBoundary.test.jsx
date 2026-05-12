import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import ErrorBoundary from '@/components/ui/ErrorBoundary'

// Suppress console.error from intentional throws in tests
beforeEach(() => jest.spyOn(console, 'error').mockImplementation(() => {}))
afterEach(() => console.error.mockRestore())

function Bomb() {
  throw new Error('Test error message')
}

test('renders children when no error', () => {
  render(<ErrorBoundary><p>Hello</p></ErrorBoundary>)
  expect(screen.getByText('Hello')).toBeInTheDocument()
})

test('shows fallback UI when child throws', () => {
  render(<ErrorBoundary><Bomb /></ErrorBoundary>)
  expect(screen.getByRole('alert')).toBeInTheDocument()
  expect(screen.getByText('Test error message')).toBeInTheDocument()
})

test('shows label in fallback title when label prop given', () => {
  render(<ErrorBoundary label="Device list"><Bomb /></ErrorBoundary>)
  expect(screen.getByText('Device list failed to render')).toBeInTheDocument()
})

test('shows generic message when no label', () => {
  render(<ErrorBoundary><Bomb /></ErrorBoundary>)
  expect(screen.getByText('Something went wrong')).toBeInTheDocument()
})

test('reset button clears error state and re-renders children', async () => {
  let shouldThrow = true
  function MaybeThrow() {
    if (shouldThrow) throw new Error('oops')
    return <p>Recovered</p>
  }
  render(<ErrorBoundary><MaybeThrow /></ErrorBoundary>)
  expect(screen.getByRole('alert')).toBeInTheDocument()

  shouldThrow = false
  await userEvent.click(screen.getByRole('button', { name: /try again/i }))
  expect(screen.getByText('Recovered')).toBeInTheDocument()
})

test('fallback has role="alert" for accessibility', () => {
  render(<ErrorBoundary><Bomb /></ErrorBoundary>)
  expect(screen.getByRole('alert')).toBeInTheDocument()
})
