'use client'

export default function StatsBar({ devices, alerts, stats }) {
  const online = devices.filter(d => d.status === 'ONLINE').length
  const critical = alerts.filter(a => a.level === 'CRITICAL' && !a.acknowledged).length

  const items = [
    { label: 'Total Devices', value: devices.length, color: 'text-sentinel-accent' },
    { label: 'Online', value: online, color: 'text-sentinel-success' },
    { label: 'Offline', value: devices.length - online, color: 'text-gray-400' },
    { label: 'Critical Alerts', value: critical, color: 'text-sentinel-danger' },
    { label: 'Events / min', value: stats.lastMinute ?? 0, color: 'text-sentinel-accent' }
  ]

  return (
    <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-4">
      {items.map(item => (
        <div key={item.label} className="card py-4">
          <p className="text-gray-500 text-xs uppercase tracking-wide">{item.label}</p>
          <p className={`text-3xl font-bold mt-1 ${item.color}`}>{item.value}</p>
        </div>
      ))}
    </div>
  )
}
