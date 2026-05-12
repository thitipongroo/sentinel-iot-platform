'use client'

import Link from 'next/link'
import { useEffect } from 'react'
import { useRouter, usePathname } from 'next/navigation'
import { useAuth } from '@/hooks/useAuth'
import { useWebSocket } from '@/hooks/useWebSocket'
import { useQuery } from '@tanstack/react-query'
import { qk } from '@/lib/queryClient'
import { alertsApi } from '@/api/client'
import OfflineBanner from '@/components/ui/OfflineBanner'

const NAV = [
  { href: '/dashboard', label: 'Dashboard', icon: '◈' },
  { href: '/devices',   label: 'Devices',   icon: '⬡' },
  { href: '/alerts',    label: 'Alerts',    icon: '⚠', badge: true },
  { href: '/settings',  label: 'Settings',  icon: '⚙' },
]
const ADMIN_NAV = { href: '/users', label: 'Users', icon: '👤' }

export default function AppShell({ children }) {
  const { user, logout, loading } = useAuth()
  const { status: wsStatus }      = useWebSocket()
  const router   = useRouter()
  const pathname = usePathname()

  useEffect(() => {
    if (!loading && !user) router.replace('/login')
  }, [user, loading, router])

  const { data: alerts = [] } = useQuery({
    queryKey: qk.alertsUnacked(),
    queryFn:  () => alertsApi.unacknowledged().then(r => r.data),
    enabled:  !!user,
  })
  const unackedCount = alerts.length

  if (loading || !user) {
    return (
      <div className="flex items-center justify-center h-screen text-sentinel-accent">
        Loading…
      </div>
    )
  }

  const isAdmin = user.role === 'ADMIN'
  const links   = isAdmin
    ? [...NAV.slice(0, 3), ADMIN_NAV, NAV[3]]
    : NAV

  return (
    <div className="flex h-screen bg-sentinel-900 overflow-hidden">
      <OfflineBanner />

      {/* ── Sidebar ─────────────────────────────────────────────────────────── */}
      <aside className="w-56 bg-sentinel-800 border-r border-sentinel-700 flex flex-col flex-shrink-0">
        <div className="px-5 py-4 border-b border-sentinel-700">
          <span className="text-sentinel-accent text-lg font-bold">⚡ Sentinel</span>
          <span className="block text-gray-500 text-xs mt-0.5">IoT Platform</span>
        </div>

        <nav className="flex-1 px-3 py-4 space-y-0.5 overflow-y-auto">
          {links.map(link => {
            const active = pathname === link.href || pathname.startsWith(link.href + '/')
            return (
              <Link
                key={link.href}
                href={link.href}
                className={`flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm transition-colors ${
                  active
                    ? 'bg-sentinel-accent/10 text-sentinel-accent border border-sentinel-accent/20'
                    : 'text-gray-400 hover:bg-sentinel-700/50 hover:text-gray-200'
                }`}
              >
                <span className="w-5 text-center text-base">{link.icon}</span>
                <span className="flex-1">{link.label}</span>
                {link.badge && unackedCount > 0 && (
                  <span className="bg-sentinel-danger text-white text-[10px] font-bold rounded-full min-w-[18px] h-[18px] flex items-center justify-center px-1">
                    {unackedCount > 99 ? '99+' : unackedCount}
                  </span>
                )}
              </Link>
            )
          })}
        </nav>

        <div className="px-4 py-4 border-t border-sentinel-700 space-y-3">
          <div>
            <p className="text-sm text-gray-200 font-medium truncate">{user.username}</p>
            <p className="text-xs text-gray-500">{user.role}</p>
          </div>
          <button
            onClick={logout}
            className="text-xs text-gray-500 hover:text-sentinel-danger transition-colors"
          >
            Logout
          </button>
        </div>
      </aside>

      {/* ── Main area ───────────────────────────────────────────────────────── */}
      <div className="flex-1 flex flex-col overflow-hidden">
        <header className="bg-sentinel-800 border-b border-sentinel-700 px-6 py-3 flex items-center justify-end flex-shrink-0">
          <span
            className={`flex items-center gap-1.5 text-xs ${
              wsStatus === 'CONNECTED' ? 'text-sentinel-success' : 'text-sentinel-warning'
            }`}
          >
            <span
              className={`w-1.5 h-1.5 rounded-full ${
                wsStatus === 'CONNECTED'
                  ? 'bg-sentinel-success animate-pulse'
                  : 'bg-sentinel-warning'
              }`}
            />
            WS {wsStatus}
          </span>
        </header>

        <main className="flex-1 overflow-y-auto p-6">
          {children}
        </main>
      </div>
    </div>
  )
}
