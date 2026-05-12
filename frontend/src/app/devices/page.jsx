'use client'

import { useState, useMemo } from 'react'
import Link from 'next/link'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useAuth } from '@/hooks/useAuth'
import { qk } from '@/lib/queryClient'
import { devicesApi } from '@/api/client'
import AppShell from '@/components/AppShell'
import ErrorBoundary from '@/components/ui/ErrorBoundary'

const STATUS_COLORS = {
  ONLINE:  'badge-online',
  OFFLINE: 'badge-offline',
}
const LIFECYCLE_COLORS = {
  ACTIVE:         'text-sentinel-success',
  PROVISIONED:    'text-gray-400',
  INACTIVE:       'text-sentinel-warning',
  DECOMMISSIONED: 'text-sentinel-danger',
}

function AddDeviceModal({ onClose, onCreate }) {
  const [form, setForm] = useState({ name: '', description: '', location: '' })
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  const handleSubmit = async (e) => {
    e.preventDefault()
    if (!form.name.trim()) { setError('Name is required'); return }
    setError('')
    setLoading(true)
    try {
      await onCreate(form)
      onClose()
    } catch (err) {
      setError(err.response?.data?.message ?? 'Failed to create device')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="fixed inset-0 bg-black/60 flex items-center justify-center z-50" onClick={onClose}>
      <div className="card w-full max-w-md mx-4" onClick={e => e.stopPropagation()}>
        <h2 className="text-white font-semibold mb-4">Register New Device</h2>
        <form onSubmit={handleSubmit} className="space-y-3">
          {[
            { id: 'name',        label: 'Name *',     placeholder: 'sensor-101' },
            { id: 'description', label: 'Description', placeholder: 'Assembly line sensor' },
            { id: 'location',    label: 'Location',    placeholder: 'Building 3 — Zone A' },
          ].map(f => (
            <div key={f.id}>
              <label className="block text-xs text-gray-400 mb-1">{f.label}</label>
              <input
                type="text"
                placeholder={f.placeholder}
                value={form[f.id]}
                onChange={e => setForm(p => ({ ...p, [f.id]: e.target.value }))}
                className="w-full px-3 py-2 text-sm bg-sentinel-900 border border-sentinel-700 rounded-lg text-gray-300 placeholder-gray-600 focus:outline-none focus:border-sentinel-accent"
              />
            </div>
          ))}
          {error && <p className="text-xs text-sentinel-danger">{error}</p>}
          <div className="flex gap-3 pt-2">
            <button type="button" onClick={onClose}
              className="flex-1 px-4 py-2 text-sm border border-sentinel-700 text-gray-400 rounded-lg hover:bg-sentinel-700/50 transition">
              Cancel
            </button>
            <button type="submit" disabled={loading}
              className="flex-1 px-4 py-2 text-sm bg-sentinel-accent/20 border border-sentinel-accent/50 text-sentinel-accent rounded-lg hover:bg-sentinel-accent/30 transition disabled:opacity-50">
              {loading ? 'Registering…' : 'Register'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

export default function DevicesPage() {
  const { user } = useAuth()
  const qc = useQueryClient()

  const [search,    setSearch]    = useState('')
  const [statusF,   setStatusF]   = useState('ALL')
  const [lifecycleF, setLifecycleF] = useState('ALL')
  const [showModal, setShowModal] = useState(false)

  const { data: devices = [], isLoading } = useQuery({
    queryKey: qk.devices(),
    queryFn:  () => devicesApi.list().then(r => r.data),
    enabled:  !!user,
  })

  const createMutation = useMutation({
    mutationFn: (data) => devicesApi.create(data),
    onSuccess:  () => qc.invalidateQueries({ queryKey: qk.devices() }),
  })

  const filtered = useMemo(() => {
    const q = search.toLowerCase()
    return devices.filter(d => {
      if (statusF   !== 'ALL' && d.status        !== statusF)   return false
      if (lifecycleF !== 'ALL' && d.lifecycleStatus !== lifecycleF) return false
      if (q && !d.name.toLowerCase().includes(q) &&
          !(d.location ?? '').toLowerCase().includes(q))        return false
      return true
    })
  }, [devices, search, statusF, lifecycleF])

  const isAdmin = user?.role === 'ADMIN'

  const relativeTime = (ts) => {
    if (!ts) return '—'
    const diff = (Date.now() - new Date(ts)) / 1000
    if (diff < 60)   return `${Math.round(diff)}s ago`
    if (diff < 3600) return `${Math.round(diff / 60)}m ago`
    if (diff < 86400) return `${Math.round(diff / 3600)}h ago`
    return `${Math.round(diff / 86400)}d ago`
  }

  return (
    <AppShell>
      {showModal && (
        <AddDeviceModal
          onClose={() => setShowModal(false)}
          onCreate={(data) => createMutation.mutateAsync(data)}
        />
      )}

      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-xl font-semibold text-white">Devices</h1>
          <p className="text-sm text-gray-500 mt-0.5">{devices.length} registered</p>
        </div>
        {isAdmin && (
          <button
            onClick={() => setShowModal(true)}
            className="px-4 py-2 text-sm bg-sentinel-accent/20 border border-sentinel-accent/50 text-sentinel-accent rounded-lg hover:bg-sentinel-accent/30 transition"
          >
            + Register Device
          </button>
        )}
      </div>

      {/* Filters */}
      <div className="flex flex-wrap gap-3 mb-4">
        <input
          type="search"
          placeholder="Search name or location…"
          value={search}
          onChange={e => setSearch(e.target.value)}
          className="px-3 py-2 text-sm bg-sentinel-800 border border-sentinel-700 rounded-lg text-gray-300 placeholder-gray-600 focus:outline-none focus:border-sentinel-accent w-64"
        />
        <select
          value={statusF}
          onChange={e => setStatusF(e.target.value)}
          className="px-3 py-2 text-sm bg-sentinel-800 border border-sentinel-700 rounded-lg text-gray-300 focus:outline-none focus:border-sentinel-accent"
        >
          {['ALL', 'ONLINE', 'OFFLINE'].map(v => <option key={v}>{v}</option>)}
        </select>
        <select
          value={lifecycleF}
          onChange={e => setLifecycleF(e.target.value)}
          className="px-3 py-2 text-sm bg-sentinel-800 border border-sentinel-700 rounded-lg text-gray-300 focus:outline-none focus:border-sentinel-accent"
        >
          {['ALL', 'PROVISIONED', 'ACTIVE', 'INACTIVE', 'DECOMMISSIONED'].map(v => <option key={v}>{v}</option>)}
        </select>
        {(search || statusF !== 'ALL' || lifecycleF !== 'ALL') && (
          <button onClick={() => { setSearch(''); setStatusF('ALL'); setLifecycleF('ALL') }}
            className="text-xs text-gray-500 hover:text-gray-300 px-2">
            Clear
          </button>
        )}
      </div>

      {/* Table */}
      <ErrorBoundary label="Devices table">
        <div className="card p-0 overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-sentinel-700 text-xs text-gray-500 uppercase tracking-wide">
                  {['Name', 'Status', 'Lifecycle', 'Location', 'Firmware', 'Last Seen', ''].map(h => (
                    <th key={h} className="px-4 py-3 text-left font-medium">{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {isLoading ? (
                  <tr><td colSpan={7} className="px-4 py-8 text-center text-gray-500">Loading…</td></tr>
                ) : filtered.length === 0 ? (
                  <tr><td colSpan={7} className="px-4 py-8 text-center text-gray-500">No devices match filters</td></tr>
                ) : filtered.map(d => (
                  <tr key={d.id} className="border-b border-sentinel-700/50 hover:bg-sentinel-700/20 transition-colors">
                    <td className="px-4 py-3 font-medium text-gray-200">{d.name}</td>
                    <td className="px-4 py-3">
                      <span className={STATUS_COLORS[d.status] ?? 'badge-offline'}>{d.status}</span>
                    </td>
                    <td className="px-4 py-3">
                      <span className={`text-xs font-medium ${LIFECYCLE_COLORS[d.lifecycleStatus] ?? 'text-gray-400'}`}>
                        {d.lifecycleStatus ?? 'PROVISIONED'}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-gray-400 max-w-[180px] truncate">{d.location ?? '—'}</td>
                    <td className="px-4 py-3 text-gray-400 font-mono text-xs">{d.firmwareVersion ?? '—'}</td>
                    <td className="px-4 py-3 text-gray-500 text-xs">{relativeTime(d.lastSeen)}</td>
                    <td className="px-4 py-3">
                      <Link href={`/devices/${d.id}`}
                        className="text-xs text-sentinel-accent hover:underline">
                        View →
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {filtered.length > 0 && (
            <p className="px-4 py-2 text-xs text-gray-600 border-t border-sentinel-700/50">
              Showing {filtered.length} of {devices.length} devices
            </p>
          )}
        </div>
      </ErrorBoundary>
    </AppShell>
  )
}
