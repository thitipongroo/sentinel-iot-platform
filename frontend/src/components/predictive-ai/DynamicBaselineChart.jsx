'use client'

import { useState } from 'react'
import {
  ResponsiveContainer,
  ComposedChart,
  Area,
  Line,
  XAxis,
  YAxis,
  Tooltip,
  Legend,
  ReferenceArea
} from 'recharts'

export default function DynamicBaselineChart({
  telemetry = [],
  baselines = {},
  selectedSensor = 'TEMPERATURE',
  onSelectSensor
}) {
  const sensorKeys = ['TEMPERATURE', 'HUMIDITY', 'SMOKE_PPM']
  const baseline = baselines[selectedSensor]

  // Synthesize chart data combining historical points with the dynamic baseline bounds
  const chartData = telemetry.map((t, idx) => {
    let val = 0
    if (selectedSensor === 'TEMPERATURE') val = t.temperature ?? 0
    else if (selectedSensor === 'HUMIDITY') val = t.humidity ?? 0
    else if (selectedSensor === 'SMOKE_PPM') val = t.smokePpm ?? 0

    const mean = baseline ? baseline.mean : val
    const lowerBand = baseline ? baseline.lowerBand : val - 3
    const upperBand = baseline ? baseline.upperBand : val + 3
    const isOutlier = baseline && baseline.sampleCount > 5 && (val > upperBand || val < lowerBand)

    return {
      index: idx + 1,
      time: t.timestamp ? new Date(t.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' }) : `#${idx + 1}`,
      value: val,
      mean: Number(mean.toFixed(2)),
      lowerBand: Number(lowerBand.toFixed(2)),
      upperBand: Number(upperBand.toFixed(2)),
      isOutlier
    }
  })

  const unit = selectedSensor === 'TEMPERATURE' ? '°C' : selectedSensor === 'HUMIDITY' ? '%' : 'ppm'

  return (
    <div className="card flex flex-col h-full">
      <div className="flex items-center justify-between mb-4 flex-wrap gap-2">
        <div>
          <h2 className="text-white font-semibold text-base flex items-center gap-2">
            <span>Dynamic Baseline Envelope</span>
            <span className="text-xs px-2 py-0.5 rounded-full bg-sentinel-accent/20 text-sentinel-accent border border-sentinel-accent/30 font-mono">
              μ ± 3σ
            </span>
          </h2>
          <p className="text-xs text-gray-500 mt-0.5">
            Statistical learning envelope continuously adapting to nominal operational conditions
          </p>
        </div>

        {/* Sensor selector tabs */}
        <div className="flex bg-sentinel-900 border border-sentinel-700 rounded-lg p-0.5">
          {sensorKeys.map(k => (
            <button
              key={k}
              onClick={() => onSelectSensor(k)}
              className={`px-3 py-1 text-xs font-medium rounded-md transition-all ${
                selectedSensor === k
                  ? 'bg-sentinel-accent text-sentinel-900 shadow-sm'
                  : 'text-gray-400 hover:text-gray-200'
              }`}
            >
              {k.replace('_', ' ')}
            </button>
          ))}
        </div>
      </div>

      {/* Baseline Metric Pills */}
      {baseline && (
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 mb-4">
          <div className="bg-sentinel-900/60 border border-sentinel-700/60 rounded-lg p-2.5">
            <span className="text-[11px] text-gray-500 block">Baseline Mean (μ)</span>
            <span className="text-sm font-semibold text-gray-200 font-mono">{baseline.mean.toFixed(2)} {unit}</span>
          </div>
          <div className="bg-sentinel-900/60 border border-sentinel-700/60 rounded-lg p-2.5">
            <span className="text-[11px] text-gray-500 block">Dispersion (σ)</span>
            <span className="text-sm font-semibold text-gray-200 font-mono">±{baseline.stdDev.toFixed(2)} {unit}</span>
          </div>
          <div className="bg-sentinel-900/60 border border-sentinel-700/60 rounded-lg p-2.5">
            <span className="text-[11px] text-gray-500 block">Normal Band Range</span>
            <span className="text-sm font-semibold text-sentinel-accent font-mono">
              {baseline.lowerBand.toFixed(1)} – {baseline.upperBand.toFixed(1)} {unit}
            </span>
          </div>
          <div className="bg-sentinel-900/60 border border-sentinel-700/60 rounded-lg p-2.5">
            <span className="text-[11px] text-gray-500 block">Current Z-Score / Status</span>
            <span className={`text-sm font-semibold font-mono ${baseline.breached ? 'text-sentinel-danger animate-pulse' : 'text-sentinel-success'}`}>
              z = {baseline.zScore > 0 ? '+' : ''}{baseline.zScore.toFixed(2)} {baseline.breached ? '(BREACH)' : '(Nominal)'}
            </span>
          </div>
        </div>
      )}

      {/* Recharts chart */}
      <div className="flex-1 min-h-[260px] w-full">
        {chartData.length === 0 ? (
          <div className="flex items-center justify-center h-full text-xs text-gray-500">
            Awaiting telemetry data stream…
          </div>
        ) : (
          <ResponsiveContainer width="100%" height="100%">
            <ComposedChart data={chartData} margin={{ top: 10, right: 10, left: -20, bottom: 0 }}>
              <XAxis dataKey="time" stroke="#4b5563" tick={{ fontSize: 10, fill: '#9ca3af' }} />
              <YAxis stroke="#4b5563" tick={{ fontSize: 10, fill: '#9ca3af' }} unit={` ${unit}`} />
              <Tooltip
                contentStyle={{
                  backgroundColor: '#0d1530',
                  borderColor: '#1e293b',
                  borderRadius: '0.5rem',
                  fontSize: '0.75rem',
                  color: '#e2e8f0'
                }}
              />
              <Legend
                verticalAlign="top"
                height={32}
                wrapperStyle={{ fontSize: '11px', color: '#9ca3af' }}
              />

              {/* Shaded Normal Band Range */}
              <Area
                type="monotone"
                dataKey="upperBand"
                stroke="none"
                fill="#00d4ff"
                fillOpacity={0.08}
                name="Confidence Envelope (μ ± 3σ)"
              />
              <Line
                type="monotone"
                dataKey="mean"
                stroke="#64748b"
                strokeDasharray="4 4"
                strokeWidth={1.5}
                dot={false}
                name="Rolling Mean (μ)"
              />
              <Line
                type="monotone"
                dataKey="value"
                stroke="#00d4ff"
                strokeWidth={2}
                dot={(props) => {
                  const { cx, cy, payload } = props
                  if (payload.isOutlier) {
                    return (
                      <circle
                        key={`dot-${payload.index}`}
                        cx={cx}
                        cy={cy}
                        r={5}
                        fill="#ef4444"
                        stroke="#ffffff"
                        strokeWidth={1.5}
                      />
                    )
                  }
                  return (
                    <circle
                      key={`dot-${payload.index}`}
                      cx={cx}
                      cy={cy}
                      r={2}
                      fill="#00d4ff"
                    />
                  )
                }}
                name={`Observed ${selectedSensor}`}
              />
            </ComposedChart>
          </ResponsiveContainer>
        )}
      </div>
    </div>
  )
}
