'use client'

import {
  ResponsiveContainer,
  ComposedChart,
  Line,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ReferenceLine
} from 'recharts'
import { format } from 'date-fns'
import { useState } from 'react'

const TABS = ['Temperature / Humidity', 'Smoke (ppm)', 'Motion']

const CustomTooltip = ({ active, payload, label }) => {
  if (!active || !payload?.length) return null
  return (
    <div className="bg-sentinel-800 border border-sentinel-700 rounded-lg p-3 text-xs">
      <p className="text-gray-400 mb-1">{label}</p>
      {payload.map(p => (
        <p key={p.name} style={{ color: p.color }}>
          {p.name}: <span className="font-semibold">{typeof p.value === 'number' ? p.value.toFixed(1) : p.value}</span>
        </p>
      ))}
    </div>
  )
}

export default function TelemetryChart({ data, device }) {
  const [activeTab, setActiveTab] = useState(0)

  const chartData = data.map(d => ({
    time: format(new Date(d.timestamp), 'HH:mm:ss'),
    Temperature: d.temperature,
    Humidity: d.humidity,
    Smoke: d.smokePpm ?? 0,
    Motion: d.motion ? 1 : 0
  }))

  return (
    <div className="card">
      <div className="flex items-center justify-between mb-4">
        <h2 className="text-white font-semibold flex items-center gap-2">
          <span className="text-sentinel-accent">📈</span>
          {device ? `${device.name} — Realtime Telemetry` : 'Select a device'}
        </h2>
        <div className="flex gap-1">
          {TABS.map((tab, i) => (
            <button
              key={tab}
              onClick={() => setActiveTab(i)}
              className={`text-xs px-3 py-1 rounded-md transition ${
                activeTab === i
                  ? 'bg-sentinel-accent text-sentinel-900 font-semibold'
                  : 'text-gray-400 hover:text-white'
              }`}
            >
              {tab}
            </button>
          ))}
        </div>
      </div>

      {chartData.length === 0 ? (
        <div className="flex items-center justify-center h-48 text-gray-500 text-sm">
          No telemetry data yet
        </div>
      ) : (
        <>
          {activeTab === 0 && (
            <ResponsiveContainer width="100%" height={260}>
              <ComposedChart data={chartData} margin={{ top: 5, right: 20, left: 0, bottom: 5 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="#1a3a6e" />
                <XAxis dataKey="time" tick={{ fill: '#64748b', fontSize: 11 }} />
                <YAxis yAxisId="temp" domain={[0, 120]} tick={{ fill: '#64748b', fontSize: 11 }} />
                <YAxis yAxisId="hum" orientation="right" domain={[0, 100]} tick={{ fill: '#64748b', fontSize: 11 }} />
                <Tooltip content={<CustomTooltip />} />
                <Legend wrapperStyle={{ color: '#94a3b8', fontSize: 12 }} />
                <ReferenceLine yAxisId="temp" y={80} stroke="#ef4444" strokeDasharray="4 4" label={{ value: 'CRITICAL', fill: '#ef4444', fontSize: 10 }} />
                <Line yAxisId="temp" type="monotone" dataKey="Temperature" stroke="#00d4ff" strokeWidth={2} dot={false} activeDot={{ r: 4 }} unit="°C" />
                <Line yAxisId="hum" type="monotone" dataKey="Humidity" stroke="#a78bfa" strokeWidth={2} dot={false} activeDot={{ r: 4 }} unit="%" />
              </ComposedChart>
            </ResponsiveContainer>
          )}

          {activeTab === 1 && (
            <ResponsiveContainer width="100%" height={260}>
              <ComposedChart data={chartData} margin={{ top: 5, right: 20, left: 0, bottom: 5 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="#1a3a6e" />
                <XAxis dataKey="time" tick={{ fill: '#64748b', fontSize: 11 }} />
                <YAxis tick={{ fill: '#64748b', fontSize: 11 }} label={{ value: 'ppm', angle: -90, position: 'insideLeft', fill: '#64748b', fontSize: 11 }} />
                <Tooltip content={<CustomTooltip />} />
                <ReferenceLine y={200} stroke="#ef4444" strokeDasharray="4 4" label={{ value: 'DANGER', fill: '#ef4444', fontSize: 10 }} />
                <ReferenceLine y={100} stroke="#f59e0b" strokeDasharray="4 4" label={{ value: 'WARNING', fill: '#f59e0b', fontSize: 10 }} />
                <Line type="monotone" dataKey="Smoke" stroke="#fb923c" strokeWidth={2} dot={false} activeDot={{ r: 4 }} unit=" ppm" />
              </ComposedChart>
            </ResponsiveContainer>
          )}

          {activeTab === 2 && (
            <ResponsiveContainer width="100%" height={260}>
              <ComposedChart data={chartData} margin={{ top: 5, right: 20, left: 0, bottom: 5 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="#1a3a6e" />
                <XAxis dataKey="time" tick={{ fill: '#64748b', fontSize: 11 }} />
                <YAxis domain={[0, 1.5]} ticks={[0, 1]} tickFormatter={v => v === 1 ? 'YES' : 'NO'} tick={{ fill: '#64748b', fontSize: 11 }} />
                <Tooltip content={<CustomTooltip />} formatter={(v) => [v === 1 ? 'Detected' : 'None', 'Motion']} />
                <Bar dataKey="Motion" fill="#10b981" opacity={0.8} radius={[2, 2, 0, 0]} />
              </ComposedChart>
            </ResponsiveContainer>
          )}
        </>
      )}
    </div>
  )
}
