'use client'

import { useState, useEffect } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useAuth } from '@/hooks/useAuth'
import { qk } from '@/lib/queryClient'
import { devicesApi } from '@/api/client'
import AppShell from '@/components/AppShell'
import ErrorBoundary from '@/components/ui/ErrorBoundary'
import DeviceShadowInspector from '@/components/digital-twin/DeviceShadowInspector'

export default function DigitalTwinPage() {
  const { user } = useAuth()
  const [selectedDeviceId, setSelectedDeviceId] = useState(null)

  const { data: devices = [], isLoading } = useQuery({
    queryKey: qk.devices(),
    queryFn: () => devicesApi.list().then(r => r.data),
    enabled: !!user,
  })

  useEffect(() => {
    if (!selectedDeviceId && devices.length > 0) {
      setSelectedDeviceId(devices[0].id)
    }
  }, [devices, selectedDeviceId])

  const selectedDevice = devices.find(d => d.id === selectedDeviceId) ?? devices[0] ?? null

  return (
    <AppShell>
      {/* Top Header */}
      <div className="flex items-center justify-between mb-6 flex-wrap gap-4">
        <div>
          <div className="flex items-center gap-2">
            <h1 className="text-xl font-bold text-white tracking-tight">Digital Twin & Device Shadow Hub</h1>
            <span className="text-xs px-2.5 py-0.5 rounded-full bg-sentinel-accent/20 border border-sentinel-accent/40 text-sentinel-accent font-semibold">
              RFC-001 Phase 2
            </span>
          </div>
          <p className="text-sm text-gray-400 mt-0.5">
            Cloud-edge state synchronization, desired/reported decoupling, and delta resolution with optimistic concurrency control
          </p>
        </div>

        {/* Feature Badges */}
        <div className="flex items-center gap-2 text-xs">
          <div className="px-3 py-1.5 rounded-lg bg-sentinel-800 border border-sentinel-700/80 flex items-center gap-2">
            <span className="w-2 h-2 rounded-full bg-sentinel-success" />
            <span className="text-gray-300 font-medium">Redis L1 Cache: &lt; 1ms</span>
          </div>
          <div className="px-3 py-1.5 rounded-lg bg-sentinel-800 border border-sentinel-700/80 flex items-center gap-2">
            <span className="w-2 h-2 rounded-full bg-sentinel-accent" />
            <span className="text-gray-300 font-medium">OCC Versioning: PostgreSQL JSONB</span>
          </div>
        </div>
      </div>

      {/* Device Selector Bar */}
      <div className="bg-sentinel-800/80 border border-sentinel-700 rounded-xl p-3 mb-6 flex items-center justify-between flex-wrap gap-3">
        <div className="flex items-center gap-3">
          <span className="text-xs text-gray-400 font-medium uppercase tracking-wider">Inspect Twin:</span>
          <select
            value={selectedDeviceId || ''}
            onChange={(e) => setSelectedDeviceId(e.target.value)}
            className="bg-sentinel-900 border border-sentinel-700 rounded-lg px-3 py-1.5 text-xs text-gray-200 font-medium focus:outline-none focus:border-sentinel-accent"
          >
            {devices.map((d) => (
              <option key={d.id} value={d.id}>
                {d.name} ({d.status})
              </option>
            ))}
          </select>
        </div>

        {selectedDevice && (
          <div className="flex items-center gap-4 text-xs font-mono">
            <span className="text-gray-400">
              ID: <span className="text-gray-200">{selectedDevice.id.slice(0, 8)}…</span>
            </span>
            <span className="text-gray-400">
              Location: <span className="text-gray-200">{selectedDevice.location || 'Factory Floor'}</span>
            </span>
            <span className="text-gray-400">
              Status: <span className={selectedDevice.status === 'ONLINE' ? 'text-sentinel-success font-bold' : 'text-gray-500'}>{selectedDevice.status}</span>
            </span>
          </div>
        )}
      </div>

      {/* Twin Inspector */}
      {selectedDevice ? (
        <ErrorBoundary label="Digital Twin Inspector">
          <DeviceShadowInspector
            device={selectedDevice}
            userRole={user?.role}
          />
        </ErrorBoundary>
      ) : (
        <div className="card text-center py-12 text-gray-500 text-sm">
          {isLoading ? 'Loading registered devices…' : 'No devices found.'}
        </div>
      )}
    </AppShell>
  )
}
