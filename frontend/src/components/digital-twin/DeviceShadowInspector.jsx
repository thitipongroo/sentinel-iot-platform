'use client'

import { useState, useEffect } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { shadowsApi } from '@/api/client'
import { qk } from '@/lib/queryClient'

export default function DeviceShadowInspector({ device, userRole }) {
  const qc = useQueryClient()
  const deviceId = device?.id
  const isAdmin = userRole === 'ADMIN'

  const { data: shadow, isLoading, error } = useQuery({
    queryKey: qk.deviceShadow(deviceId),
    queryFn: () => shadowsApi.get(deviceId).then(r => r.data),
    enabled: !!deviceId,
    refetchInterval: 5_000,
  })

  const [desiredJson, setDesiredJson] = useState('{}')
  const [jsonError, setJsonError] = useState(null)
  const [saveSuccess, setSaveSuccess] = useState(false)

  // Populate editor when shadow loads
  useEffect(() => {
    if (shadow?.desired) {
      setDesiredJson(JSON.stringify(shadow.desired, null, 2))
    } else {
      setDesiredJson('{\n  "targetTemperature": 22.5,\n  "operatingMode": "OPTIMAL",\n  "telemetryIntervalSec": 5\n}')
    }
  }, [shadow])

  const updateMutation = useMutation({
    mutationFn: (newDesired) => shadowsApi.update(deviceId, {
      desiredState: newDesired,
      version: shadow?.version,
      clientToken: `web-${Date.now()}`
    }),
    onSuccess: (data) => {
      qc.setQueryData(qk.deviceShadow(deviceId), data.data)
      qc.invalidateQueries({ queryKey: qk.deviceShadow(deviceId) })
      setSaveSuccess(true)
      setTimeout(() => setSaveSuccess(false), 3000)
    }
  })

  const handleSaveDesired = () => {
    setJsonError(null)
    try {
      const parsed = JSON.parse(desiredJson)
      updateMutation.mutate(parsed)
    } catch (err) {
      setJsonError('Invalid JSON format: ' + err.message)
    }
  }

  const desired = shadow?.desired || {}
  const reported = shadow?.reported || {}
  const delta = shadow?.delta || {}
  const deltaKeys = Object.keys(delta)
  const hasDelta = deltaKeys.length > 0

  return (
    <div className="card space-y-6">
      {/* Header bar */}
      <div className="flex items-center justify-between flex-wrap gap-3 pb-4 border-b border-sentinel-700/60">
        <div>
          <div className="flex items-center gap-2">
            <h2 className="text-white font-semibold text-base flex items-center gap-2">
              <span>Device Twin / Shadow State</span>
              <span className="text-xs px-2 py-0.5 rounded-full bg-sentinel-accent/20 border border-sentinel-accent/40 text-sentinel-accent font-mono font-bold">
                OCC v{shadow?.version ?? 1}
              </span>
            </h2>
            {hasDelta ? (
              <span className="badge-warning font-mono font-semibold animate-pulse">
                {deltaKeys.length} Delta Unsynced
              </span>
            ) : (
              <span className="badge-online font-mono font-semibold">
                ✓ Fully Synchronized
              </span>
            )}
          </div>
          <p className="text-xs text-gray-400 mt-0.5">
            Decoupled desired vs reported digital twin with optimistic concurrency control & sub-millisecond Redis cache
          </p>
        </div>

        <div className="flex items-center gap-2 text-xs font-mono">
          <span className="text-gray-500">Last Synced:</span>
          <span className="text-gray-300">
            {shadow?.timestamp ? new Date(shadow.timestamp).toLocaleTimeString() : 'Just now'}
          </span>
        </div>
      </div>

      {/* Delta Callout (if divergence exists) */}
      {hasDelta && (
        <div className="p-3.5 rounded-lg bg-sentinel-warning/10 border border-sentinel-warning/40">
          <div className="flex items-center gap-2 text-sentinel-warning font-semibold text-xs mb-1.5">
            <span>⚠ State Divergence Detected (Delta Queue)</span>
          </div>
          <p className="text-xs text-gray-300 mb-2">
            The physical device has pending state modifications that have not yet been reported:
          </p>
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-2">
            {deltaKeys.map((k) => (
              <div key={k} className="p-2 rounded bg-sentinel-900/80 border border-sentinel-700/60 font-mono text-xs">
                <span className="text-sentinel-accent font-semibold block">{k}</span>
                <div className="flex justify-between text-[11px] mt-1 text-gray-400">
                  <span>Desired: <strong className="text-white">{String(desired[k])}</strong></span>
                  <span>Reported: <strong className="text-sentinel-warning">{String(reported[k] ?? '—')}</strong></span>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Side-by-Side Dual View: Desired vs Reported */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        {/* Desired State Panel */}
        <div className="flex flex-col rounded-lg bg-sentinel-900/70 border border-sentinel-700/70 p-4">
          <div className="flex items-center justify-between mb-3">
            <div>
              <span className="text-xs font-bold text-sentinel-accent uppercase tracking-wider block">
                Desired State (Target)
              </span>
              <span className="text-[11px] text-gray-500">Configuration targets requested by cloud</span>
            </div>
            {isAdmin && (
              <button
                onClick={handleSaveDesired}
                disabled={updateMutation.isPending}
                className="px-3 py-1 text-xs font-semibold bg-sentinel-accent text-sentinel-900 rounded-md hover:bg-sentinel-accent/90 transition-all disabled:opacity-50"
              >
                {updateMutation.isPending ? 'Syncing…' : 'Push Desired'}
              </button>
            )}
          </div>

          {isAdmin ? (
            <div className="flex-1 flex flex-col">
              <textarea
                value={desiredJson}
                onChange={(e) => setDesiredJson(e.target.value)}
                rows={9}
                className="w-full flex-1 bg-sentinel-950 font-mono text-xs text-sentinel-accent p-3 rounded-lg border border-sentinel-700/80 focus:outline-none focus:border-sentinel-accent resize-none"
                placeholder='{\n  "targetTemp": 22.0\n}'
              />
              {jsonError && (
                <p className="text-xs text-sentinel-danger mt-1.5 font-mono">{jsonError}</p>
              )}
              {saveSuccess && (
                <p className="text-xs text-sentinel-success mt-1.5 font-mono">✓ Desired state persisted and delta published</p>
              )}
            </div>
          ) : (
            <pre className="p-3 bg-sentinel-950 rounded-lg text-xs font-mono text-sentinel-accent overflow-auto max-h-56">
              {JSON.stringify(desired, null, 2)}
            </pre>
          )}
        </div>

        {/* Reported State Panel */}
        <div className="flex flex-col rounded-lg bg-sentinel-900/70 border border-sentinel-700/70 p-4">
          <div className="flex items-center justify-between mb-3">
            <div>
              <span className="text-xs font-bold text-sentinel-success uppercase tracking-wider block">
                Reported State (Actual)
              </span>
              <span className="text-[11px] text-gray-500">Confirmed state broadcasted by edge gateway</span>
            </div>
            <span className="text-[10px] text-gray-500 font-mono">Read-Only</span>
          </div>

          <pre className="flex-1 p-3 bg-sentinel-950 rounded-lg text-xs font-mono text-gray-300 overflow-auto max-h-56">
            {Object.keys(reported).length > 0
              ? JSON.stringify(reported, null, 2)
              : '{\n  // Awaiting device first reported packet\n}'}
          </pre>
        </div>
      </div>
    </div>
  )
}
