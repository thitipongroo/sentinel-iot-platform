'use client'

import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { anomaliesApi } from '@/api/client'

const PRESETS = [
  { label: 'Nominal Baseline', temp: 24.5, hum: 52.0, smoke: 1.2 },
  { label: 'Moderate Heat Drift', temp: 36.0, hum: 42.0, smoke: 3.5 },
  { label: 'Thermal Runaway Crisis', temp: 88.0, hum: 11.5, smoke: 85.0 },
  { label: 'Smoldering Smoke Spike', temp: 48.0, hum: 30.0, smoke: 165.0 },
]

export default function InferenceSimulator({ deviceId, deviceName }) {
  const [temperature, setTemperature] = useState(24.5)
  const [humidity, setHumidity] = useState(52.0)
  const [smokePpm, setSmokePpm] = useState(1.2)
  const [result, setResult] = useState(null)

  const evalMutation = useMutation({
    mutationFn: (payload) => anomaliesApi.evaluate(payload).then(r => r.data),
    onSuccess: (data) => setResult(data)
  })

  const handleSimulate = (e) => {
    e?.preventDefault()
    evalMutation.mutate({
      deviceId,
      deviceName,
      temperature: Number(temperature),
      humidity: Number(humidity),
      smokePpm: Number(smokePpm)
    })
  }

  const applyPreset = (p) => {
    setTemperature(p.temp)
    setHumidity(p.hum)
    setSmokePpm(p.smoke)
    evalMutation.mutate({
      deviceId,
      deviceName,
      temperature: p.temp,
      humidity: p.hum,
      smokePpm: p.smoke
    })
  }

  return (
    <div className="card">
      <div className="flex items-center justify-between mb-3 flex-wrap gap-2">
        <div>
          <h3 className="text-white font-semibold text-sm flex items-center gap-2">
            <span>ML Anomaly Dry-Run Simulator</span>
            <span className="text-[10px] px-1.5 py-0.5 rounded bg-sentinel-accent/20 text-sentinel-accent font-mono uppercase">
              Zero-DB Side Effects
            </span>
          </h3>
          <p className="text-xs text-gray-500">
            Inject synthetic multi-sensor anomalies to test real-time inference scoring and thermal runaway gates
          </p>
        </div>

        {/* Quick Presets */}
        <div className="flex flex-wrap gap-1.5">
          {PRESETS.map((p) => (
            <button
              key={p.label}
              type="button"
              onClick={() => applyPreset(p)}
              className="px-2.5 py-1 text-[11px] bg-sentinel-900 border border-sentinel-700/80 rounded-md text-gray-300 hover:text-white hover:border-sentinel-accent/50 transition-all"
            >
              {p.label}
            </button>
          ))}
        </div>
      </div>

      <form onSubmit={handleSimulate} className="grid grid-cols-1 sm:grid-cols-4 gap-3 items-end">
        <div>
          <label className="block text-[11px] text-gray-400 mb-1">Temperature (°C)</label>
          <input
            type="number"
            step="0.1"
            value={temperature}
            onChange={(e) => setTemperature(e.target.value)}
            className="w-full bg-sentinel-900 border border-sentinel-700 rounded-lg px-3 py-1.5 text-xs text-gray-200 font-mono focus:outline-none focus:border-sentinel-accent"
          />
        </div>

        <div>
          <label className="block text-[11px] text-gray-400 mb-1">Humidity (%)</label>
          <input
            type="number"
            step="0.1"
            value={humidity}
            onChange={(e) => setHumidity(e.target.value)}
            className="w-full bg-sentinel-900 border border-sentinel-700 rounded-lg px-3 py-1.5 text-xs text-gray-200 font-mono focus:outline-none focus:border-sentinel-accent"
          />
        </div>

        <div>
          <label className="block text-[11px] text-gray-400 mb-1">Smoke (PPM)</label>
          <input
            type="number"
            step="0.1"
            value={smokePpm}
            onChange={(e) => setSmokePpm(e.target.value)}
            className="w-full bg-sentinel-900 border border-sentinel-700 rounded-lg px-3 py-1.5 text-xs text-gray-200 font-mono focus:outline-none focus:border-sentinel-accent"
          />
        </div>

        <div>
          <button
            type="submit"
            disabled={evalMutation.isPending}
            className="w-full py-1.5 px-4 text-xs font-semibold bg-sentinel-accent text-sentinel-900 rounded-lg hover:bg-sentinel-accent/90 transition-all disabled:opacity-50 shadow-sm"
          >
            {evalMutation.isPending ? 'Computing…' : '⚡ Run AI Inference'}
          </button>
        </div>
      </form>

      {/* Simulator Inference Output */}
      {result && (
        <div className="mt-4 p-4 rounded-lg bg-sentinel-900/80 border border-sentinel-700">
          <div className="flex items-center justify-between mb-2 flex-wrap gap-2">
            <div className="flex items-center gap-2">
              <span className={`px-2 py-0.5 rounded text-xs font-bold font-mono ${
                result.severity === 'CRITICAL'
                  ? 'bg-sentinel-danger text-white animate-pulse'
                  : result.severity === 'WARNING'
                    ? 'bg-sentinel-warning text-sentinel-900'
                    : 'bg-sentinel-success/20 text-sentinel-success'
              }`}>
                {result.severity}
              </span>
              <span className="text-sm font-semibold text-gray-200 font-mono">
                {result.anomalyType}
              </span>
            </div>

            <div className="flex items-center gap-4 text-xs font-mono">
              <div>
                <span className="text-gray-500 mr-1.5">Anomaly Score:</span>
                <span className={`font-bold ${
                  result.anomalyScore >= 0.8 ? 'text-sentinel-danger' : result.anomalyScore >= 0.65 ? 'text-sentinel-warning' : 'text-sentinel-success'
                }`}>
                  {(result.anomalyScore * 100).toFixed(1)}%
                </span>
              </div>
              <div>
                <span className="text-gray-500 mr-1.5">Confidence:</span>
                <span className="text-gray-300 font-bold">{(result.confidence * 100).toFixed(1)}%</span>
              </div>
            </div>
          </div>

          <p className="text-xs text-gray-400 mt-1 border-t border-sentinel-700/50 pt-2">
            <span className="text-sentinel-accent font-semibold mr-1">Diagnostic:</span>
            {result.summary}
          </p>

          {/* Feature contribution pills */}
          {result.featureContributions && Object.keys(result.featureContributions).length > 0 && (
            <div className="flex flex-wrap gap-2 mt-3 pt-2 border-t border-sentinel-700/40">
              <span className="text-[11px] text-gray-500 self-center">Feature Impact:</span>
              {Object.entries(result.featureContributions)
                .filter(([_, v]) => v > 0)
                .map(([k, v]) => (
                  <span
                    key={k}
                    className="text-[11px] px-2 py-0.5 rounded bg-sentinel-800 border border-sentinel-700 text-gray-300 font-mono"
                  >
                    {k}: <span className="text-sentinel-accent font-bold">{v}%</span>
                  </span>
                ))}
            </div>
          )}
        </div>
      )}
    </div>
  )
}
