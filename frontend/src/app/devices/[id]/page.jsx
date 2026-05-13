'use client'

import Link from 'next/link'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuth } from '@/hooks/useAuth'
import { useWebSocket } from '@/hooks/useWebSocket'
import { qk } from '@/lib/queryClient'
import { devicesApi, alertsApi, telemetryApi } from '@/api/client'
import AppShell from '@/components/AppShell'
import TelemetryChart from '@/components/TelemetryChart'
import DeviceManagement from '@/components/DeviceManagement'
import ErrorBoundary from '@/components/ui/ErrorBoundary'

const STATUS_COLORS = {
  ONLINE:  'text-sentinel-success',
  OFFLINE: 'text-gray-500',
}
const LIFECYCLE_COLORS = {
  ACTIVE:         'text-sentinel-success',
  PROVISIONED:    'text-gray-400',
  INACTIVE:       'text-sentinel-warning',
  DECOMMISSIONED: 'text-sentinel-danger',
}

function InfoRow({ label, value, mono }) {
  return (
    <div className="flex justify-between items-start gap-4 py-2 border-b border-sentinel-700/50 last:border-0">
      <span className="text-xs text-gray-500 flex-shrink-0">{label}</span>
      <span className={`text-sm text-right text-gray-200 ${mono ? 'font-mono text-xs' : ''}`}>
        {value ?? '—'}
      </span>
    </div>
  )
}

export default function DeviceDetailPage({ params }) {
  const { id } = params
  const { user } = useAuth()
  const { lastMessage } = useWebSocket()
  const qc = useQueryClient()

  const { data: device, isLoading } = useQuery({
    queryKey: qk.device(id),
    queryFn:  () => devicesApi.get(id).then(r => r.data),
    enabled:  !!user && !!id,
  })

  const { data: telemetry = [] } = useQuery({
    queryKey: qk.telemetryLatest(id, 50),
    queryFn:  () => telemetryApi.latest(id, 50).then(r => [...r.data].reverse()),
    enabled:  !!user && !!id,
  })

  const { data: deviceAlerts = [] } = useQuery({
    queryKey: ['alerts', 'device', id],
    queryFn:  () => alertsApi.listByDevice(id).then(r => r.data),
    enabled:  !!user && !!id,
  })

  const relativeTime = (ts) => {
    if (!ts) return '—'
    const diff = (Date.now() - new Date(ts)) / 1000
    if (diff < 60)    return `${Math.round(diff)}s ago`
    if (diff < 3600)  return `${Math.round(diff / 60)}m ago`
    if (diff < 86400) return `${Math.round(diff / 3600)}h ago`
    return new Date(ts).toLocaleDateString()
  }

  const isAdmin = user?.role === 'ADMIN'

  if (isLoading) {
    return (
      <AppShell>
        <div className="text-gray-500 text-sm">Loading device…</div>
      </AppShell>
    )
  }

  if (!device) {
    return (
      <AppShell>
        <div className="text-sentinel-danger text-sm">Device not found.</div>
        <Link href="/devices" className="text-xs text-sentinel-accent hover:underline mt-2 block">
          ← Back to Devices
        </Link>
      </AppShell>
    )
  }

  return (
    <AppShell>
      {/* Breadcrumb */}
      <div className="flex items-center gap-2 mb-6 text-sm">
        <Link href="/devices" className="text-gray-500 hover:text-gray-300 transition">Devices</Link>
        <span className="text-gray-700">/</span>
        <span className="text-gray-200">{device.name}</span>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Left — Device info + management */}
        <div className="space-y-6">
          {/* Info card */}
          <div className="card">
            <div className="flex items-start justify-between mb-4">
              <div>
                <h1 className="text-lg font-semibold text-white">{device.name}</h1>
                {device.description && (
                  <p className="text-xs text-gray-500 mt-0.5">{device.description}</p>
                )}
              </div>
              <span className={`text-sm font-medium ${STATUS_COLORS[device.status] ?? 'text-gray-400'}`}>
                ● {device.status}
              </span>
            </div>
            <InfoRow label="Lifecycle"    value={device.lifecycleStatus ?? 'PROVISIONED'} />
            <InfoRow label="Location"     value={device.location} />
            <InfoRow label="Firmware"     value={device.firmwareVersion} mono />
            <InfoRow label="Last Seen"    value={relativeTime(device.lastSeen)} />
            <InfoRow label="Created"      value={device.createdAt ? new Date(device.createdAt).toLocaleDateString() : '—'} />
            <InfoRow label="Device ID"    value={device.id} mono />
          </div>

          {/* Device management (ADMIN) */}
          {isAdmin && (
            <ErrorBoundary label="Device management">
              <DeviceManagement
                device={device}
                onUpdate={() => {
                  qc.invalidateQueries({ queryKey: qk.device(id) })
                  qc.invalidateQueries({ queryKey: qk.devices() })
                }}
              />
            </ErrorBoundary>
          )}

          {/* Recent alerts for this device */}
          {deviceAlerts.length > 0 && (
            <div className="card">
              <h2 className="text-white font-semibold mb-3 text-sm">Recent Alerts</h2>
              <div className="space-y-2">
                {deviceAlerts.slice(0, 5).map(alert => (
                  <div key={alert.id} className="flex items-start gap-2 text-xs">
                    <span className={alert.level === 'CRITICAL' ? 'badge-critical' : 'badge-warning'}>
                      {alert.level}
                    </span>
                    <div className="flex-1 min-w-0">
                      <p className="text-gray-300 leading-tight">{alert.message}</p>
                      <p className="text-gray-600 mt-0.5">{relativeTime(alert.createdAt)}</p>
                    </div>
                    {alert.acknowledged && (
                      <span className="text-sentinel-success flex-shrink-0">✓</span>
                    )}
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>

        {/* Right — Telemetry chart */}
        <div className="lg:col-span-2">
          <ErrorBoundary label="Telemetry chart">
            <TelemetryChart data={telemetry} device={device} />
          </ErrorBoundary>
        </div>
      </div>
    </AppShell>
  )
}
