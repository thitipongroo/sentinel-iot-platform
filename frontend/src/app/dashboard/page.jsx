'use client'

import { useEffect } from 'react'
import { useRouter } from 'next/navigation'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useAuth } from '@/hooks/useAuth'
import { useWebSocket } from '@/hooks/useWebSocket'
import { useStore } from '@/lib/store'
import { qk } from '@/lib/queryClient'
import { devicesApi, alertsApi, telemetryApi } from '@/api/client'
import DeviceTable from '@/components/DeviceTable'
import TelemetryChart from '@/components/TelemetryChart'
import AlertList from '@/components/AlertList'
import StatsBar from '@/components/StatsBar'
import DeviceManagement from '@/components/DeviceManagement'
import OfflineBanner from '@/components/ui/OfflineBanner'
import ErrorBoundary from '@/components/ui/ErrorBoundary'

export default function DashboardPage() {
  const { user, logout, loading } = useAuth()
  const router = useRouter()
  const { lastMessage, status: wsStatus } = useWebSocket()
  const qc = useQueryClient()

  const { selectedDeviceId, setSelectedDeviceId } = useStore()

  useEffect(() => {
    if (!loading && !user) router.replace('/login')
  }, [user, loading, router])

  // ── Server state via React Query ─────────────────────────────────────────────
  const { data: devices = [] } = useQuery({
    queryKey: qk.devices(),
    queryFn:  () => devicesApi.list().then(r => r.data),
    enabled:  !!user,
  })

  const { data: alerts = [] } = useQuery({
    queryKey: qk.alerts(),
    queryFn:  () => alertsApi.list().then(r => r.data),
    enabled:  !!user,
  })

  const { data: stats = { lastMinute: 0, replayQueueSize: 0 } } = useQuery({
    queryKey: qk.stats(),
    queryFn:  () => telemetryApi.stats().then(r => r.data),
    enabled:  !!user,
  })

  // Derive selected device from normalized id in store
  const selectedDevice = devices.find(d => d.id === selectedDeviceId) ?? devices[0] ?? null

  // Auto-select first device once devices load
  useEffect(() => {
    if (!selectedDeviceId && devices.length > 0) setSelectedDeviceId(devices[0].id)
  }, [devices, selectedDeviceId, setSelectedDeviceId])

  const { data: telemetry = [] } = useQuery({
    queryKey: qk.telemetryLatest(selectedDevice?.id, 50),
    queryFn:  () => telemetryApi.latest(selectedDevice.id, 50).then(r => [...r.data].reverse()),
    enabled:  !!selectedDevice,
  })

  // ── WebSocket: splice live readings into telemetry cache ─────────────────────
  useEffect(() => {
    if (!lastMessage || !selectedDevice) return
    if (lastMessage.deviceId !== selectedDevice.name) return

    qc.setQueryData(qk.telemetryLatest(selectedDevice.id, 50), (prev = []) =>
      [...prev.slice(-49), {
        id:          Date.now(),
        temperature: lastMessage.temperature,
        humidity:    lastMessage.humidity,
        motion:      lastMessage.motion,
        smokePpm:    lastMessage.smokePpm,
        timestamp:   new Date().toISOString(),
      }]
    )

    if (lastMessage.temperature > 80 || lastMessage.smokePpm > 200) {
      qc.invalidateQueries({ queryKey: qk.alerts() })
    }
  }, [lastMessage, selectedDevice, qc])

  // ── Optimistic alert acknowledge ─────────────────────────────────────────────
  const acknowledgeMutation = useMutation({
    mutationFn: (alertId) => alertsApi.acknowledge(alertId),

    onMutate: async (alertId) => {
      await qc.cancelQueries({ queryKey: qk.alerts() })
      const prev = qc.getQueryData(qk.alerts())
      qc.setQueryData(qk.alerts(), (old = []) =>
        old.map(a => a.id === alertId ? { ...a, acknowledged: true } : a)
      )
      return { prev }
    },

    onError: (_err, _alertId, ctx) => {
      qc.setQueryData(qk.alerts(), ctx.prev)
    },

    onSettled: () => {
      qc.invalidateQueries({ queryKey: qk.alerts() })
    },
  })

  if (loading || !user) {
    return (
      <div className="flex items-center justify-center h-screen text-sentinel-accent">
        Loading…
      </div>
    )
  }

  const isAdmin = user?.role === 'ADMIN'

  return (
    <>
      <OfflineBanner />

      <div className="min-h-screen bg-sentinel-900">
        {/* ── Header ─────────────────────────────────────────────────────────── */}
        <header className="bg-sentinel-800 border-b border-sentinel-700 px-6 py-4 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <span className="text-sentinel-accent text-2xl font-bold">⚡ Sentinel</span>
            <span className="text-gray-400 text-sm">IoT Platform</span>
          </div>
          <div className="flex items-center gap-4">
            <span
              className={`flex items-center gap-1.5 text-xs ${
                wsStatus === 'CONNECTED' ? 'text-sentinel-success' : 'text-sentinel-warning'
              }`}
            >
              <span
                className={`w-2 h-2 rounded-full ${
                  wsStatus === 'CONNECTED'
                    ? 'bg-sentinel-success animate-pulse'
                    : 'bg-sentinel-warning'
                }`}
                aria-hidden="true"
              />
              <span aria-label={`WebSocket ${wsStatus}`}>WS {wsStatus}</span>
            </span>
            <span className="text-sm text-gray-400">
              {user?.username} ({user?.role})
            </span>
            <button
              onClick={logout}
              className="text-xs text-gray-500 hover:text-white focus:outline-none focus:underline"
              aria-label="Log out"
            >
              Logout
            </button>
          </div>
        </header>

        {/* ── Main content ───────────────────────────────────────────────────── */}
        <main className="p-6 space-y-6" id="main-content">
          <StatsBar devices={devices} alerts={alerts} stats={stats} />

          <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
            {/* Device list — full virtualized table with filtering */}
            <div className="lg:col-span-1">
              <ErrorBoundary label="Device list">
                <DeviceTable
                  devices={devices}
                  selected={selectedDevice}
                  onSelect={(device) => setSelectedDeviceId(device.id)}
                  lastMessage={lastMessage}
                />
              </ErrorBoundary>
            </div>

            {/* Detail panel */}
            <div className="lg:col-span-2 space-y-6">
              <ErrorBoundary label="Telemetry chart">
                <TelemetryChart data={telemetry} device={selectedDevice} />
              </ErrorBoundary>

              <ErrorBoundary label="Alert list">
                <AlertList
                  alerts={alerts}
                  onAcknowledge={(id) => acknowledgeMutation.mutate(id)}
                  userRole={user?.role}
                />
              </ErrorBoundary>

              {isAdmin && selectedDevice && (
                <ErrorBoundary label="Device management">
                  <DeviceManagement
                    device={selectedDevice}
                    onUpdate={() => qc.invalidateQueries({ queryKey: qk.devices() })}
                  />
                </ErrorBoundary>
              )}
            </div>
          </div>
        </main>
      </div>
    </>
  )
}
