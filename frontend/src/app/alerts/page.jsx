'use client'

import { useMemo, useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useAuth } from '@/hooks/useAuth'
import { qk } from '@/lib/queryClient'
import { alertsApi, devicesApi } from '@/api/client'
import AppShell from '@/components/AppShell'
import ErrorBoundary from '@/components/ui/ErrorBoundary'

export default function AlertsPage() {
  const { user } = useAuth()
  const qc = useQueryClient()

  const [levelF,  setLevelF]  = useState('ALL')
  const [statusF, setStatusF] = useState('ALL')
  const [page, setPage]       = useState(0)
  const PAGE_SIZE = 50

  const { data: alertPage, isLoading } = useQuery({
    queryKey: [...qk.alerts(), page],
    queryFn:  () => alertsApi.list(page, PAGE_SIZE).then(r => r.data),
    enabled:  !!user,
  })
  const alerts     = alertPage?.content  ?? []
  const totalPages = alertPage?.totalPages ?? 1
  const totalItems = alertPage?.totalElements ?? 0

  const { data: unackedAlerts = [] } = useQuery({
    queryKey: qk.alertsUnacked(),
    queryFn:  () => alertsApi.unacknowledged().then(r => r.data),
    enabled:  !!user,
  })

  const { data: devices = [] } = useQuery({
    queryKey: qk.devices(),
    queryFn:  () => devicesApi.list().then(r => r.data),
    enabled:  !!user,
  })

  const deviceMap = useMemo(
    () => Object.fromEntries(devices.map(d => [d.id, d])),
    [devices]
  )

  const filtered = useMemo(() => {
    return alerts.filter(a => {
      if (levelF  !== 'ALL' && a.level !== levelF)                         return false
      if (statusF === 'UNACKNOWLEDGED' && a.acknowledged)                  return false
      if (statusF === 'ACKNOWLEDGED'   && !a.acknowledged)                 return false
      return true
    })
  }, [alerts, levelF, statusF])

  const acknowledgeAll = useMutation({
    mutationFn: () => alertsApi.acknowledgeAll(),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: qk.alerts() })
      qc.invalidateQueries({ queryKey: qk.alertsUnacked() })
    },
  })

  const acknowledgeMutation = useMutation({
    mutationFn: (id) => alertsApi.acknowledge(id),
    onMutate: async (id) => {
      await qc.cancelQueries({ queryKey: qk.alerts() })
      const prev = qc.getQueryData(qk.alerts())
      qc.setQueryData(qk.alerts(), (old = []) =>
        old.map(a => a.id === id ? { ...a, acknowledged: true } : a)
      )
      return { prev }
    },
    onError: (_e, _id, ctx) => qc.setQueryData(qk.alerts(), ctx.prev),
    onSettled: () => {
      qc.invalidateQueries({ queryKey: qk.alerts() })
      qc.invalidateQueries({ queryKey: qk.alertsUnacked() })
    },
  })

  const isAdmin = user?.role === 'ADMIN'
  const unacked = unackedAlerts.length
  const hasPrev = page > 0
  const hasNext = page < totalPages - 1

  const relativeTime = (ts) => {
    if (!ts) return '—'
    const diff = (Date.now() - new Date(ts)) / 1000
    if (diff < 60)    return `${Math.round(diff)}s ago`
    if (diff < 3600)  return `${Math.round(diff / 60)}m ago`
    if (diff < 86400) return `${Math.round(diff / 3600)}h ago`
    return new Date(ts).toLocaleString()
  }

  return (
    <AppShell>
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-xl font-semibold text-white">Alerts</h1>
          <p className="text-sm text-gray-500 mt-0.5">
            {totalItems} total · {unacked > 0 ? `${unacked} unacknowledged` : 'all clear'}
          </p>
        </div>
        {isAdmin && unacked > 0 && (
          <button
            onClick={() => acknowledgeAll.mutate()}
            disabled={acknowledgeAll.isPending}
            className="px-4 py-2 text-sm border border-sentinel-700 text-gray-400 rounded-lg hover:bg-sentinel-700/50 transition disabled:opacity-50"
          >
            {acknowledgeAll.isPending ? 'Acknowledging…' : 'Acknowledge All'}
          </button>
        )}
      </div>

      {/* Filters */}
      <div className="flex flex-wrap gap-3 mb-4">
        {[
          { label: 'Level',  value: levelF,  set: setLevelF,  opts: ['ALL', 'CRITICAL', 'WARNING'] },
          { label: 'Status', value: statusF, set: setStatusF, opts: ['ALL', 'UNACKNOWLEDGED', 'ACKNOWLEDGED'] },
        ].map(f => (
          <select
            key={f.label}
            value={f.value}
            onChange={e => { f.set(e.target.value); setPage(0) }}
            className="px-3 py-2 text-sm bg-sentinel-800 border border-sentinel-700 rounded-lg text-gray-300 focus:outline-none focus:border-sentinel-accent"
          >
            {f.opts.map(o => <option key={o}>{o}</option>)}
          </select>
        ))}
        {(levelF !== 'ALL' || statusF !== 'ALL') && (
          <button onClick={() => { setLevelF('ALL'); setStatusF('ALL'); setPage(0) }}
            className="text-xs text-gray-500 hover:text-gray-300 px-2">
            Clear
          </button>
        )}
      </div>

      {/* Alert list */}
      <ErrorBoundary label="Alerts">
        <div className="card p-0 overflow-hidden">
          {isLoading ? (
            <p className="px-4 py-8 text-center text-gray-500 text-sm">Loading…</p>
          ) : filtered.length === 0 ? (
            <p className="px-4 py-8 text-center text-gray-500 text-sm">No alerts match filters</p>
          ) : (
            <ul className="divide-y divide-sentinel-700/50">
              {filtered.map(alert => {
                const device = deviceMap[alert.deviceId]
                return (
                  <li key={alert.id} className={`flex items-start gap-4 px-4 py-3 hover:bg-sentinel-700/20 transition-colors ${alert.acknowledged ? 'opacity-50' : ''}`}>
                    {/* Level badge */}
                    <span className={`flex-shrink-0 mt-0.5 ${alert.level === 'CRITICAL' ? 'badge-critical' : 'badge-warning'}`}>
                      {alert.level}
                    </span>

                    {/* Content */}
                    <div className="flex-1 min-w-0">
                      <p className="text-sm text-gray-200 leading-snug">{alert.message}</p>
                      <div className="flex items-center gap-3 mt-1">
                        <span className="text-xs text-gray-500">
                          {device ? device.name : alert.deviceId}
                        </span>
                        <span className="text-gray-700">·</span>
                        <span className="text-xs text-gray-500">{relativeTime(alert.createdAt)}</span>
                        {alert.acknowledged && (
                          <span className="text-xs text-sentinel-success">✓ acknowledged</span>
                        )}
                      </div>
                    </div>

                    {/* Acknowledge button */}
                    {isAdmin && !alert.acknowledged && (
                      <button
                        onClick={() => acknowledgeMutation.mutate(alert.id)}
                        disabled={acknowledgeMutation.isPending}
                        className="flex-shrink-0 text-xs px-3 py-1 border border-sentinel-700 text-gray-400 rounded hover:bg-sentinel-700/50 transition disabled:opacity-50"
                      >
                        Acknowledge
                      </button>
                    )}
                  </li>
                )
              })}
            </ul>
          )}
          {totalPages > 1 && (
            <div className="px-4 py-3 flex items-center justify-between border-t border-sentinel-700/50">
              <span className="text-xs text-gray-600">
                Page {page + 1} of {totalPages} · {totalItems} alerts total
              </span>
              <div className="flex gap-2">
                <button onClick={() => setPage(p => p - 1)} disabled={!hasPrev}
                  className="text-xs px-3 py-1 border border-sentinel-700 text-gray-400 rounded hover:bg-sentinel-700/50 transition disabled:opacity-30">
                  ← Prev
                </button>
                <button onClick={() => setPage(p => p + 1)} disabled={!hasNext}
                  className="text-xs px-3 py-1 border border-sentinel-700 text-gray-400 rounded hover:bg-sentinel-700/50 transition disabled:opacity-30">
                  Next →
                </button>
              </div>
            </div>
          )}
        </div>
      </ErrorBoundary>
    </AppShell>
  )
}
