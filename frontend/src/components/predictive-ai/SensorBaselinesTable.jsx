'use client'

import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { anomaliesApi } from '@/api/client'
import { qk } from '@/lib/queryClient'

export default function SensorBaselinesTable({ deviceId, baselines = {}, userRole }) {
  const qc = useQueryClient()
  const [resettingSensor, setResettingSensor] = useState(null)
  const isAdmin = userRole === 'ADMIN'

  const resetAllMutation = useMutation({
    mutationFn: () => anomaliesApi.resetDeviceBaselines(deviceId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: qk.anomaliesBaselines(deviceId) })
    }
  })

  const resetSensorMutation = useMutation({
    mutationFn: (sensorKey) => anomaliesApi.resetSensorBaseline(deviceId, sensorKey),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: qk.anomaliesBaselines(deviceId) })
      setResettingSensor(null)
    }
  })

  const entries = Object.entries(baselines || {})

  return (
    <div className="card p-0 overflow-hidden">
      <div className="p-4 border-b border-sentinel-700/60 flex items-center justify-between flex-wrap gap-2">
        <div>
          <h3 className="text-white font-semibold text-sm">Sensor Adaptive Baselines</h3>
          <p className="text-xs text-gray-500">Live statistical parameters tracking ambient variance</p>
        </div>

        {isAdmin && entries.length > 0 && (
          <button
            onClick={() => {
              if (window.confirm('Reset all dynamic baselines for this device? This will initiate a fresh 10-sample learning cycle.')) {
                resetAllMutation.mutate()
              }
            }}
            disabled={resetAllMutation.isPending}
            className="px-3 py-1 text-xs font-medium text-sentinel-danger bg-sentinel-danger/10 border border-sentinel-danger/30 rounded-lg hover:bg-sentinel-danger/20 transition-all disabled:opacity-50"
          >
            {resetAllMutation.isPending ? 'Resetting…' : '↺ Reset All Baselines'}
          </button>
        )}
      </div>

      <div className="overflow-x-auto">
        <table className="w-full text-xs text-left">
          <thead>
            <tr className="border-b border-sentinel-700 text-[11px] text-gray-400 uppercase tracking-wider bg-sentinel-900/40">
              <th className="px-4 py-2.5">Sensor</th>
              <th className="px-4 py-2.5">Mean (μ)</th>
              <th className="px-4 py-2.5">Std Dev (σ)</th>
              <th className="px-4 py-2.5">Normal Band [μ ± 3σ]</th>
              <th className="px-4 py-2.5">Samples</th>
              <th className="px-4 py-2.5">Last Observed</th>
              <th className="px-4 py-2.5">Z-Score</th>
              <th className="px-4 py-2.5">Status</th>
              {isAdmin && <th className="px-4 py-2.5 text-right">Actions</th>}
            </tr>
          </thead>
          <tbody className="divide-y divide-sentinel-700/40 font-mono">
            {entries.length === 0 ? (
              <tr>
                <td colSpan={isAdmin ? 9 : 8} className="px-4 py-8 text-center text-gray-500 font-sans">
                  No active baselines stored. Baselines materialize after telemetry ingest.
                </td>
              </tr>
            ) : (
              entries.map(([sensorKey, b]) => {
                const isBreached = b.breached
                const unit = sensorKey === 'TEMPERATURE' ? '°C' : sensorKey === 'HUMIDITY' ? '%' : 'ppm'

                return (
                  <tr key={sensorKey} className="hover:bg-sentinel-700/20 transition-colors">
                    <td className="px-4 py-3 font-semibold text-gray-200 font-sans flex items-center gap-1.5">
                      <span className="w-1.5 h-1.5 rounded-full bg-sentinel-accent" />
                      {sensorKey.replace('_', ' ')}
                    </td>
                    <td className="px-4 py-3 text-gray-300">{b.mean?.toFixed(2)} {unit}</td>
                    <td className="px-4 py-3 text-gray-400">±{b.stdDev?.toFixed(2)}</td>
                    <td className="px-4 py-3 text-sentinel-accent font-medium">
                      {b.lowerBand?.toFixed(1)} – {b.upperBand?.toFixed(1)}
                    </td>
                    <td className="px-4 py-3 text-gray-400">{b.sampleCount ?? 0}</td>
                    <td className="px-4 py-3 text-gray-200">{b.lastObservedValue?.toFixed(2)} {unit}</td>
                    <td className={`px-4 py-3 font-bold ${
                      Math.abs(b.zScore) > 3 ? 'text-sentinel-danger' : Math.abs(b.zScore) > 2 ? 'text-sentinel-warning' : 'text-gray-300'
                    }`}>
                      {b.zScore > 0 ? '+' : ''}{b.zScore?.toFixed(2)}
                    </td>
                    <td className="px-4 py-3 font-sans">
                      {isBreached ? (
                        <span className="badge-critical font-bold">BREACHED</span>
                      ) : (
                        <span className="badge-online">NOMINAL</span>
                      )}
                    </td>
                    {isAdmin && (
                      <td className="px-4 py-3 text-right font-sans">
                        <button
                          onClick={() => {
                            if (window.confirm(`Reset baseline for ${sensorKey}?`)) {
                              setResettingSensor(sensorKey)
                              resetSensorMutation.mutate(sensorKey)
                            }
                          }}
                          disabled={resettingSensor === sensorKey}
                          className="text-xs text-gray-400 hover:text-sentinel-danger transition-colors underline"
                        >
                          {resettingSensor === sensorKey ? 'Resetting…' : 'Reset'}
                        </button>
                      </td>
                    )}
                  </tr>
                )
              })
            )}
          </tbody>
        </table>
      </div>
    </div>
  )
}
