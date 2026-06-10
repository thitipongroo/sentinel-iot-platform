import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import AppShell from '@/components/AppShell'
import { useAuth } from '@/hooks/useAuth'
import { useWebSocket } from '@/hooks/useWebSocket'
import { useQuery } from '@tanstack/react-query'

jest.mock('@/hooks/useAuth', () => ({ useAuth: jest.fn() }))
jest.mock('@/hooks/useWebSocket', () => ({ useWebSocket: jest.fn() }))
jest.mock('@tanstack/react-query', () => ({ useQuery: jest.fn() }))
jest.mock('@/api/client', () => ({ alertsApi: { unacknowledged: jest.fn() } }))
jest.mock('@/lib/queryClient', () => ({ qk: { alertsUnacked: () => ['alerts-unacked'] } }))
jest.mock('@/components/ui/OfflineBanner', () => () => null)

const { useRouter, usePathname } = require('next/navigation')

const ADMIN_USER    = { username: 'alice', role: 'ADMIN'    }
const OPERATOR_USER = { username: 'bob',   role: 'OPERATOR' }

beforeEach(() => {
  jest.clearAllMocks()
  useRouter.mockReturnValue({ replace: jest.fn(), push: jest.fn(), prefetch: jest.fn() })
  usePathname.mockReturnValue('/dashboard')
  useWebSocket.mockReturnValue({ status: 'CONNECTED' })
  useQuery.mockReturnValue({ data: [] })
})

test('shows loading screen when auth is loading', () => {
  useAuth.mockReturnValue({ user: null, logout: jest.fn(), loading: true })
  render(<AppShell>content</AppShell>)
  expect(screen.getByText('Loading…')).toBeInTheDocument()
})

test('shows loading screen when user is null', () => {
  useAuth.mockReturnValue({ user: null, logout: jest.fn(), loading: false })
  render(<AppShell>content</AppShell>)
  expect(screen.getByText('Loading…')).toBeInTheDocument()
})

test('calls router.replace(/login) when unauthenticated', () => {
  const replace = jest.fn()
  useRouter.mockReturnValue({ replace, push: jest.fn(), prefetch: jest.fn() })
  useAuth.mockReturnValue({ user: null, logout: jest.fn(), loading: false })
  render(<AppShell>content</AppShell>)
  expect(replace).toHaveBeenCalledWith('/login')
})

test('renders children when user is authenticated', () => {
  useAuth.mockReturnValue({ user: ADMIN_USER, logout: jest.fn(), loading: false })
  render(<AppShell><div data-testid="page-content">hello</div></AppShell>)
  expect(screen.getByTestId('page-content')).toBeInTheDocument()
})

test('ADMIN user sees Users nav link', () => {
  useAuth.mockReturnValue({ user: ADMIN_USER, logout: jest.fn(), loading: false })
  render(<AppShell>content</AppShell>)
  expect(screen.getByRole('link', { name: /users/i })).toBeInTheDocument()
})

test('OPERATOR user does not see Users nav link', () => {
  useAuth.mockReturnValue({ user: OPERATOR_USER, logout: jest.fn(), loading: false })
  render(<AppShell>content</AppShell>)
  expect(screen.queryByRole('link', { name: /users/i })).not.toBeInTheDocument()
})

test('shows unacked alert count badge when count > 0', () => {
  useAuth.mockReturnValue({ user: ADMIN_USER, logout: jest.fn(), loading: false })
  useQuery.mockReturnValue({ data: [{ id: 'a1' }, { id: 'a2' }] })
  render(<AppShell>content</AppShell>)
  expect(screen.getByText('2')).toBeInTheDocument()
})

test('shows WS CONNECTED status in header', () => {
  useAuth.mockReturnValue({ user: ADMIN_USER, logout: jest.fn(), loading: false })
  useWebSocket.mockReturnValue({ status: 'CONNECTED' })
  render(<AppShell>content</AppShell>)
  expect(screen.getByText(/WS CONNECTED/)).toBeInTheDocument()
})

test('shows WS RECONNECTING status in header', () => {
  useAuth.mockReturnValue({ user: ADMIN_USER, logout: jest.fn(), loading: false })
  useWebSocket.mockReturnValue({ status: 'RECONNECTING' })
  render(<AppShell>content</AppShell>)
  expect(screen.getByText(/WS RECONNECTING/)).toBeInTheDocument()
})

test('shows username and role in sidebar footer', () => {
  useAuth.mockReturnValue({ user: ADMIN_USER, logout: jest.fn(), loading: false })
  render(<AppShell>content</AppShell>)
  expect(screen.getByText('alice')).toBeInTheDocument()
  expect(screen.getByText('ADMIN')).toBeInTheDocument()
})

test('logout button calls logout()', async () => {
  const logout = jest.fn()
  useAuth.mockReturnValue({ user: ADMIN_USER, logout, loading: false })
  render(<AppShell>content</AppShell>)
  await userEvent.click(screen.getByRole('button', { name: /logout/i }))
  expect(logout).toHaveBeenCalled()
})

test('active nav link is highlighted for current pathname', () => {
  usePathname.mockReturnValue('/devices')
  useAuth.mockReturnValue({ user: ADMIN_USER, logout: jest.fn(), loading: false })
  render(<AppShell>content</AppShell>)
  const devicesLink = screen.getByRole('link', { name: /devices/i })
  expect(devicesLink.className).toMatch(/sentinel-accent/)
})
