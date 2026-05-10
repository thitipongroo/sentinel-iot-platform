import {
  ResponsiveContainer,
  ComposedChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ReferenceLine
} from 'recharts'
import { format } from 'date-fns'

const CustomTooltip = ({ active, payload, label }) => {
  if (!active || !payload?.length) return null
  return (
    <div className="bg-sentinel-800 border border-sentinel-700 rounded-lg p-3 text-xs">
      <p className="text-gray-400 mb-1">{label}</p>
      {payload.map(p => (
        <p key={p.name} style={{ color: p.color }}>
          {p.name}: <span className="font-semibold">{p.value?.toFixed(1)}{p.name === 'Temperature' ? '°C' : '%'}</span>
        </p>
      ))}
    </div>
  )
}

export default function TelemetryChart({ data, device }) {
  const chartData = data.map(d => ({
    time: format(new Date(d.timestamp), 'HH:mm:ss'),
    Temperature: d.temperature,
    Humidity: d.humidity
  }))

  return (
    <div className="card">
      <h2 className="text-white font-semibold mb-4 flex items-center gap-2">
        <span className="text-sentinel-accent">📈</span>
        {device ? `${device.name} — Realtime Telemetry` : 'Select a device'}
      </h2>

      {chartData.length === 0 ? (
        <div className="flex items-center justify-center h-48 text-gray-500 text-sm">
          No telemetry data yet
        </div>
      ) : (
        <ResponsiveContainer width="100%" height={280}>
          <ComposedChart data={chartData} margin={{ top: 5, right: 20, left: 0, bottom: 5 }}>
            <CartesianGrid strokeDasharray="3 3" stroke="#1a3a6e" />
            <XAxis dataKey="time" tick={{ fill: '#64748b', fontSize: 11 }} />
            <YAxis yAxisId="temp" domain={[0, 120]} tick={{ fill: '#64748b', fontSize: 11 }} label={{ value: '°C', position: 'insideLeft', fill: '#64748b', fontSize: 11 }} />
            <YAxis yAxisId="hum" orientation="right" domain={[0, 100]} tick={{ fill: '#64748b', fontSize: 11 }} label={{ value: '%', position: 'insideRight', fill: '#64748b', fontSize: 11 }} />
            <Tooltip content={<CustomTooltip />} />
            <Legend wrapperStyle={{ color: '#94a3b8', fontSize: 12 }} />
            <ReferenceLine yAxisId="temp" y={80} stroke="#ef4444" strokeDasharray="4 4" label={{ value: 'CRITICAL', fill: '#ef4444', fontSize: 10 }} />
            <Line yAxisId="temp" type="monotone" dataKey="Temperature" stroke="#00d4ff" strokeWidth={2} dot={false} activeDot={{ r: 4 }} />
            <Line yAxisId="hum" type="monotone" dataKey="Humidity" stroke="#a78bfa" strokeWidth={2} dot={false} activeDot={{ r: 4 }} />
          </ComposedChart>
        </ResponsiveContainer>
      )}
    </div>
  )
}
