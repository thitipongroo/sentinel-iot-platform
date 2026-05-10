'use client'

import {
  ResponsiveContainer,
  ComposedChart,
  Line,
  Area,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ReferenceLine
} from 'recharts'
import { format, subHours, subDays } from 'date-fns'
import { useState, useEffect } from 'react'
import { telemetryApi } from '@/api/client'

const SENSOR_TABS = ['Temperature / Humidity', 'Smoke (ppm)', 'Motion']
const TIME_WINDOWS = [
  { label: 'Live', value: 'live' },
  { label: '1h',   value: '1h'  },
  { label: '6h',   value: '6h'  },
  { label: '24h',  value: '24h' },
  { label: '7d',   value: '7d'  }
]

const BAND_KEYS = new Set(['tempBandBase', 'tempBandHeight', 'humBandBase', 'humBandHeight'])

const CustomTooltip = ({ active, payload, label }) => {
  if (!active || !payload?.length) return null
  return (
    <div className="bg-sentinel-800 border border-sentinel-700 rounded-lg p-3 text-xs">
      <p className="text-gray-400 mb-1">{label}</p>
      {payload.filter(p => p.value != null && !BAND_KEYS.has(p.dataKey)).map(p => (
        <p key={p.name} style={{ color: p.color }}>
          {p.name}: <span className="font-semibold">{typeof p.value === 'number' ? p.value.toFixed(1) : p.value}</span>
        </p>
      ))}
    </div>
  )
}

function windowRange(tw) {
  const now = new Date()
  switch (tw) {
    case '1h':  return { from: subHours(now, 1),  to: now }
    case '6h':  return { from: subHours(now, 6),  to: now }
    case '24h': return { from: subHours(now, 24), to: now }
    case '7d':  return { from: subDays(now, 7),   to: now }
    default:    return null
  }
}

function toTimeFmt(ts, tw) {
  return format(new Date(ts), tw === '7d' ? 'MM/dd HH:mm' : 'HH:mm')
}

export default function TelemetryChart({ data: liveData, device }) {
  const [activeTab, setActiveTab]     = useState(0)
  const [timeWindow, setTimeWindow]   = useState('live')
  const [histData, setHistData]       = useState([])
  const [loading, setLoading]         = useState(false)

  useEffect(() => {
    if (timeWindow === 'live' || !device) { setHistData([]); return }
    const range = windowRange(timeWindow)
    if (!range) return

    setLoading(true)
    const useHourly = timeWindow === '24h' || timeWindow === '7d'
    const req = useHourly
      ? telemetryApi.hourly(device.id, range.from, range.to)
      : telemetryApi.range(device.id, range.from, range.to)

    req
      .then(({ data }) => {
        if (useHourly) {
          setHistData(data.map(d => ({
            time:           toTimeFmt(d.hourBucket, timeWindow),
            Temperature:    d.tempAvg,
            tempBandBase:   d.tempMin,
            tempBandHeight: d.tempMax - d.tempMin,
            Humidity:       d.humAvg,
            humBandBase:    d.humMin,
            humBandHeight:  d.humMax - d.humMin,
            Smoke:          d.smokeAvg,
            Motion:         d.motionCount
          })))
        } else {
          setHistData(data.map(d => ({
            time:        toTimeFmt(d.timestamp, timeWindow),
            Temperature: d.temperature,
            Humidity:    d.humidity,
            Smoke:       d.smokePpm ?? 0,
            Motion:      d.motion ? 1 : 0
          })))
        }
      })
      .catch(() => {})
      .finally(() => setLoading(false))
  }, [timeWindow, device])

  const isHourly  = timeWindow === '24h' || timeWindow === '7d'
  const isRawHist = timeWindow === '1h'  || timeWindow === '6h'

  const chartData = timeWindow === 'live'
    ? liveData.map(d => ({
        time:        format(new Date(d.timestamp), 'HH:mm:ss'),
        Temperature: d.temperature,
        Humidity:    d.humidity,
        Smoke:       d.smokePpm ?? 0,
        Motion:      d.motion ? 1 : 0
      }))
    : histData

  return (
    <div className="card">
      <div className="flex items-center justify-between mb-4 flex-wrap gap-2">
        <h2 className="text-white font-semibold flex items-center gap-2">
          <span className="text-sentinel-accent">📈</span>
          {device ? device.name : 'Select a device'}
        </h2>
        <div className="flex gap-1 items-center flex-wrap">
          <div className="flex gap-0.5 mr-2 border border-sentinel-700 rounded-md overflow-hidden">
            {TIME_WINDOWS.map(w => (
              <button
                key={w.value}
                onClick={() => setTimeWindow(w.value)}
                className={`text-xs px-2.5 py-1 transition ${
                  timeWindow === w.value
                    ? 'bg-sentinel-accent text-sentinel-900 font-semibold'
                    : 'text-gray-400 hover:text-white'
                }`}
              >
                {w.label}
              </button>
            ))}
          </div>
          {SENSOR_TABS.map((tab, i) => (
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

      {loading ? (
        <div className="flex items-center justify-center h-48 text-gray-500 text-sm">Loading…</div>
      ) : chartData.length === 0 ? (
        <div className="flex items-center justify-center h-48 text-gray-500 text-sm">No telemetry data</div>
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
                <ReferenceLine yAxisId="temp" y={80} stroke="#ef4444" strokeDasharray="4 4"
                  label={{ value: 'CRITICAL', fill: '#ef4444', fontSize: 10 }} />
                {isHourly && (
                  <>
                    <Area yAxisId="temp" type="monotone" dataKey="tempBandBase"   fill="transparent" stroke="none" stackId="tband" legendType="none" />
                    <Area yAxisId="temp" type="monotone" dataKey="tempBandHeight" fill="#00d4ff" fillOpacity={0.15} stroke="none" stackId="tband" legendType="none" name="Temp range" />
                    <Area yAxisId="hum"  type="monotone" dataKey="humBandBase"    fill="transparent" stroke="none" stackId="hband" legendType="none" />
                    <Area yAxisId="hum"  type="monotone" dataKey="humBandHeight"  fill="#a78bfa" fillOpacity={0.15} stroke="none" stackId="hband" legendType="none" name="Hum range" />
                  </>
                )}
                <Line yAxisId="temp" type="monotone" dataKey="Temperature" stroke="#00d4ff" strokeWidth={2} dot={false} activeDot={{ r: 4 }} unit="°C" />
                <Line yAxisId="hum"  type="monotone" dataKey="Humidity"    stroke="#a78bfa" strokeWidth={2} dot={false} activeDot={{ r: 4 }} unit="%" />
              </ComposedChart>
            </ResponsiveContainer>
          )}

          {activeTab === 1 && (
            <ResponsiveContainer width="100%" height={260}>
              <ComposedChart data={chartData} margin={{ top: 5, right: 20, left: 0, bottom: 5 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="#1a3a6e" />
                <XAxis dataKey="time" tick={{ fill: '#64748b', fontSize: 11 }} />
                <YAxis tick={{ fill: '#64748b', fontSize: 11 }}
                  label={{ value: 'ppm', angle: -90, position: 'insideLeft', fill: '#64748b', fontSize: 11 }} />
                <Tooltip content={<CustomTooltip />} />
                <ReferenceLine y={200} stroke="#ef4444" strokeDasharray="4 4" label={{ value: 'DANGER',  fill: '#ef4444', fontSize: 10 }} />
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
                {isHourly ? (
                  <>
                    <YAxis tick={{ fill: '#64748b', fontSize: 11 }} />
                    <Tooltip content={<CustomTooltip />} />
                    <Bar dataKey="Motion" fill="#10b981" opacity={0.8} radius={[2, 2, 0, 0]} name="Motion events" />
                  </>
                ) : (
                  <>
                    <YAxis domain={[0, 1.5]} ticks={[0, 1]} tickFormatter={v => v === 1 ? 'YES' : 'NO'} tick={{ fill: '#64748b', fontSize: 11 }} />
                    <Tooltip content={<CustomTooltip />} formatter={v => [v === 1 ? 'Detected' : 'None', 'Motion']} />
                    <Bar dataKey="Motion" fill="#10b981" opacity={0.8} radius={[2, 2, 0, 0]} />
                  </>
                )}
              </ComposedChart>
            </ResponsiveContainer>
          )}
        </>
      )}
    </div>
  )
}
