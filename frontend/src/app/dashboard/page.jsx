'use client'

import { useEffect } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useAuth } from '@/hooks/useAuth'
import { useWebSocket } from '@/hooks/useWebSocket'
import { useStore } from '@/lib/store'
import { qk } from '@/lib/queryClient'
import { devicesApi, alertsApi, telemetryApi } from '@/api/client'
import AppShell from '@/components/AppShell'
import DeviceTable from '@/components/DeviceTable'
import TelemetryChart from '@/components/TelemetryChart'
import AlertList from '@/components/AlertList'
import StatsBar from '@/components/StatsBar'
import DeviceManagement from '@/components/DeviceManagement'
import ErrorBoundary from '@/components/ui/ErrorBoundary'

export default function DashboardPage() {
  const { user } = useAuth()
  const { lastMessage } = useWebSocket()
  const qc = useQueryClient()

  const { selectedDeviceId, setSelectedDeviceId } = useStore()

  const { data: devices = [] } = useQuery({
    queryKey: qk.devices(),
    queryFn:  () => devicesApi.list().then(r => r.data),
    enabled:  !!user,
  })

  const { data: alertPage } = useQuery({
    queryKey: qk.alerts(),
    queryFn:  () => alertsApi.list(0, 50).then(r => r.data),
    enabled:  !!user,
  })
  const alerts = alertPage?.content ?? []

  const { data: stats = { lastMinute: 0, replayQueueSize: 0 } } = useQuery({
    queryKey: qk.stats(),
    queryFn:  () => telemetryApi.stats().then(r => r.data),
    enabled:  !!user,
  })

  const selectedDevice = devices.find(d => d.id === selectedDeviceId) ?? devices[0] ?? null

  useEffect(() => {
    if (!selectedDeviceId && devices.length > 0) setSelectedDeviceId(devices[0].id)
  }, [devices, selectedDeviceId, setSelectedDeviceId])

  const { data: telemetry = [] } = useQuery({
    queryKey: qk.telemetryLatest(selectedDevice?.id, 50),
    queryFn:  () => telemetryApi.latest(selectedDevice.id, 50).then(r => [...r.data].reverse()),
    enabled:  !!selectedDevice,
  })

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

    const TEMP_ALERT_THRESHOLD  = 80   // matches alert.temperature-threshold in application.yml
    const SMOKE_ALERT_THRESHOLD = 200  // matches alert.smoke-threshold in application.yml
    if (lastMessage.temperature > TEMP_ALERT_THRESHOLD || lastMessage.smokePpm > SMOKE_ALERT_THRESHOLD) {
      qc.invalidateQueries({ queryKey: qk.alerts() })
    }
  }, [lastMessage, selectedDevice, qc])

  const acknowledgeMutation = useMutation({
    mutationFn: (alertId) => alertsApi.acknowledge(alertId),
    onMutate: async (alertId) => {
      await qc.cancelQueries({ queryKey: qk.alerts() })
      const prev = qc.getQueryData(qk.alerts())
      qc.setQueryData(qk.alerts(), (old) => old
        ? { ...old, content: old.content.map(a => a.id === alertId ? { ...a, acknowledged: true } : a) }
        : old
      )
      return { prev }
    },
    onError: (_err, _id, ctx) => qc.setQueryData(qk.alerts(), ctx.prev),
    onSettled: () => {
      qc.invalidateQueries({ queryKey: qk.alerts() })
      qc.invalidateQueries({ queryKey: qk.alertsUnacked() })
    },
  })

  const isAdmin = user?.role === 'ADMIN'

  return (
    <AppShell>
      <StatsBar devices={devices} alerts={alerts} stats={stats} />

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 mt-6" style={{ height: 'calc(100vh - 13rem)' }}>
        <div className="lg:col-span-1 min-h-0">
          <ErrorBoundary label="Device list">
            <DeviceTable
              devices={devices}
              selected={selectedDevice}
              onSelect={(device) => setSelectedDeviceId(device.id)}
              lastMessage={lastMessage}
            />
          </ErrorBoundary>
        </div>

        <div className="lg:col-span-2 flex flex-col gap-6 min-h-0">
          <ErrorBoundary label="Telemetry chart">
            <TelemetryChart data={telemetry} device={selectedDevice} />
          </ErrorBoundary>

          <div className="flex-1 min-h-0">
            <ErrorBoundary label="Alert list">
              <AlertList
                alerts={alerts}
                onAcknowledge={(id) => acknowledgeMutation.mutate(id)}
                userRole={user?.role}
              />
            </ErrorBoundary>
          </div>

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
    </AppShell>
  )
}
