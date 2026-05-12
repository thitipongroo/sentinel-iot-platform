import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import Select from '@/components/ui/Select'

const OPTIONS = [
  { value: 'ALL',     label: 'All' },
  { value: 'ONLINE',  label: 'Online' },
  { value: 'OFFLINE', label: 'Offline' },
]

test('renders label and all options', () => {
  render(<Select id="s1" label="Status" value="ALL" onChange={() => {}} options={OPTIONS} />)
  expect(screen.getByText('Status')).toBeInTheDocument()
  expect(screen.getByRole('option', { name: 'All' })).toBeInTheDocument()
  expect(screen.getByRole('option', { name: 'Online' })).toBeInTheDocument()
  expect(screen.getByRole('option', { name: 'Offline' })).toBeInTheDocument()
})

test('renders without label when label prop omitted', () => {
  render(<Select id="s2" value="ALL" onChange={() => {}} options={OPTIONS} />)
  expect(screen.queryByRole('label')).not.toBeInTheDocument()
})

test('calls onChange with selected value', async () => {
  const handleChange = jest.fn()
  render(<Select id="s3" label="Status" value="ALL" onChange={handleChange} options={OPTIONS} />)
  await userEvent.selectOptions(screen.getByRole('combobox'), 'ONLINE')
  expect(handleChange).toHaveBeenCalledWith('ONLINE')
})

test('label is associated with select via htmlFor/id', () => {
  render(<Select id="my-select" label="Status" value="ALL" onChange={() => {}} options={OPTIONS} />)
  const label = screen.getByText('Status')
  expect(label).toHaveAttribute('for', 'my-select')
  expect(screen.getByRole('combobox')).toHaveAttribute('id', 'my-select')
})

test('shows currently selected value', () => {
  render(<Select id="s4" label="Status" value="ONLINE" onChange={() => {}} options={OPTIONS} />)
  expect(screen.getByRole('combobox')).toHaveValue('ONLINE')
})
