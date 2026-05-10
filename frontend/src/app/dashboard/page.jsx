'use client'

import { useState, useEffect, useCallback } from 'react'
import { useRouter } from 'next/navigation'
import { useAuth } from '@/hooks/useAuth'
import { useWebSocket } from '@/hooks/useWebSocket'
import { devicesApi, alertsApi, telemetryApi } from '@/api/client'
import DeviceList from '@/components/DeviceList'
import TelemetryChart from '@/components/TelemetryChart'
import AlertList from '@/components/AlertList'
import StatsBar from '@/components/StatsBar'

export default function DashboardPage() {
  const { user, logout, loading } = useAuth()
  const router = useRouter()
  const { lastMessage, status: wsStatus } = useWebSocket()
  const [devices, setDevices] = useState([])
  const [alerts, setAlerts] = useState([])
  const [selectedDevice, setSelectedDevice] = useState(null)
  const [telemetry, setTelemetry] = useState([])
  const [stats, setStats] = useState({ lastMinute: 0 })

  useEffect(() => {
    if (!loading && !user) router.replace('/login')
  }, [user, loading, router])

  const loadDevices = useCallback(async () => {
    try {
      const { data } = await devicesApi.list()
      setDevices(data)
      if (!selectedDevice && data.length > 0) setSelectedDevice(data[0])
    } catch { /* handled by axios interceptor */ }
  }, [selectedDevice])

  const loadAlerts = useCallback(async () => {
    try {
      const { data } = await alertsApi.list()
      setAlerts(data)
    } catch { /* */ }
  }, [])

  const loadStats = useCallback(async () => {
    try {
      const { data } = await telemetryApi.stats()
      setStats(data)
    } catch { /* */ }
  }, [])

  useEffect(() => {
    if (!user) return
    loadDevices()
    loadAlerts()
    loadStats()
    const interval = setInterval(() => {
      loadDevices()
      loadAlerts()
      loadStats()
    }, 10000)
    return () => clearInterval(interval)
  }, [user, loadDevices, loadAlerts, loadStats])

  useEffect(() => {
    if (!selectedDevice) return
    telemetryApi.latest(selectedDevice.id, 50).then(({ data }) => setTelemetry(data.reverse()))
  }, [selectedDevice])

  useEffect(() => {
    if (!lastMessage || !selectedDevice) return
    if (lastMessage.deviceId === selectedDevice.name) {
      setTelemetry(prev => [...prev.slice(-49), {
        id: Date.now(),
        temperature: lastMessage.temperature,
        humidity: lastMessage.humidity,
        motion: lastMessage.motion,
        smokePpm: lastMessage.smokePpm,
        timestamp: new Date().toISOString()
      }])
    }
    if (lastMessage.temperature > 80 || lastMessage.smokePpm > 200) {
      loadAlerts()
    }
  }, [lastMessage, selectedDevice, loadAlerts])

  if (loading || !user) {
    return <div className="flex items-center justify-center h-screen text-sentinel-accent">Loading...</div>
  }

  return (
    <div className="min-h-screen bg-sentinel-900">
      <header className="bg-sentinel-800 border-b border-sentinel-700 px-6 py-4 flex items-center justify-between">
        <div className="flex items-center gap-3">
          <span className="text-sentinel-accent text-2xl font-bold">⚡ Sentinel</span>
          <span className="text-gray-400 text-sm">IoT Platform</span>
        </div>
        <div className="flex items-center gap-4">
          <span className={`flex items-center gap-1.5 text-xs ${wsStatus === 'CONNECTED' ? 'text-sentinel-success' : 'text-sentinel-warning'}`}>
            <span className={`w-2 h-2 rounded-full ${wsStatus === 'CONNECTED' ? 'bg-sentinel-success animate-pulse' : 'bg-sentinel-warning'}`} />
            WS {wsStatus}
          </span>
          <span className="text-sm text-gray-400">{user?.username} ({user?.role})</span>
          <button onClick={logout} className="text-xs text-gray-500 hover:text-white">Logout</button>
        </div>
      </header>

      <main className="p-6 space-y-6">
        <StatsBar devices={devices} alerts={alerts} stats={stats} />

        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          <div className="lg:col-span-1">
            <DeviceList
              devices={devices}
              selected={selectedDevice}
              onSelect={setSelectedDevice}
              lastMessage={lastMessage}
            />
          </div>
          <div className="lg:col-span-2 space-y-6">
            <TelemetryChart data={telemetry} device={selectedDevice} />
            <AlertList alerts={alerts} onAcknowledge={loadAlerts} userRole={user?.role} />
          </div>
        </div>
      </main>
    </div>
  )
}
