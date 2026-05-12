import { render, screen } from '@testing-library/react'
import Badge from '@/components/ui/Badge'

test('renders children correctly', () => {
  render(<Badge>Active</Badge>)
  expect(screen.getByText('Active')).toBeInTheDocument()
})

test('applies default variant when no variant given', () => {
  render(<Badge>Test</Badge>)
  expect(screen.getByText('Test')).toHaveClass('bg-sentinel-700')
})

test('applies success variant', () => {
  render(<Badge variant="success">OK</Badge>)
  expect(screen.getByText('OK')).toHaveClass('text-sentinel-success')
})

test('applies critical variant', () => {
  render(<Badge variant="critical">Critical</Badge>)
  const el = screen.getByText('Critical')
  expect(el).toHaveClass('bg-sentinel-danger')
  expect(el).toHaveClass('text-white')
})

test('falls back to default for unknown variant', () => {
  render(<Badge variant="invalid">Fallback</Badge>)
  expect(screen.getByText('Fallback')).toHaveClass('bg-sentinel-700')
})

test('merges custom className', () => {
  render(<Badge className="mt-2">Merge</Badge>)
  expect(screen.getByText('Merge')).toHaveClass('mt-2')
})
