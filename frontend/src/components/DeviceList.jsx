'use client'

import { useState, useMemo } from 'react'
import { clsx } from 'clsx'
import { formatDistanceToNow } from 'date-fns'

const LIFECYCLE_BADGE = {
  PROVISIONED:    { cls: 'bg-gray-700 text-gray-300' },
  ACTIVE:         { cls: 'bg-sentinel-success/20 text-sentinel-success' },
  INACTIVE:       { cls: 'bg-sentinel-warning/20 text-sentinel-warning' },
  DECOMMISSIONED: { cls: 'bg-sentinel-danger/20 text-sentinel-danger' }
}

export default function DeviceList({ devices, selected, onSelect, lastMessage }) {
  const [search, setSearch] = useState('')

  const filtered = useMemo(() =>
    devices.filter(d =>
      d.name.toLowerCase().includes(search.toLowerCase()) ||
      (d.location ?? '').toLowerCase().includes(search.toLowerCase())
    ), [devices, search])

  const getOnlineStatus = (device) => {
    if (lastMessage?.deviceId === device.name) return 'ONLINE'
    return device.status
  }

  return (
    <div className="card h-full">
      <h2 className="text-white font-semibold mb-3 flex items-center gap-2">
        <span className="text-sentinel-accent">▣</span> Devices
        <span className="ml-auto text-xs text-gray-500">{devices.length} total</span>
      </h2>

      <input
        type="text"
        placeholder="Search by name or location…"
        value={search}
        onChange={e => setSearch(e.target.value)}
        className="w-full mb-3 px-3 py-1.5 text-sm bg-sentinel-900 border border-sentinel-700 rounded-md text-gray-300 placeholder-gray-600 focus:outline-none focus:border-sentinel-accent"
      />

      <div className="space-y-2 overflow-y-auto max-h-[480px] pr-1">
        {filtered.length === 0 && (
          <p className="text-gray-500 text-sm text-center py-8">
            {devices.length === 0 ? 'No devices registered' : 'No matches'}
          </p>
        )}
        {filtered.map(device => {
          const status = getOnlineStatus(device)
          const isSelected = selected?.id === device.id
          const lifecycle = LIFECYCLE_BADGE[device.lifecycleStatus] ?? LIFECYCLE_BADGE.PROVISIONED
          return (
            <button
              key={device.id}
              onClick={() => onSelect(device)}
              className={clsx(
                'w-full text-left p-3 rounded-lg border transition-all',
                isSelected
                  ? 'border-sentinel-accent bg-sentinel-accent/10'
                  : 'border-sentinel-700 hover:border-sentinel-600 bg-sentinel-900/50'
              )}
            >
              <div className="flex items-center justify-between gap-2">
                <span className="text-white text-sm font-medium truncate">{device.name}</span>
                <span className={status === 'ONLINE' ? 'badge-online' : 'badge-offline'}>
                  {status}
                </span>
              </div>
              <div className="flex items-center gap-2 mt-1.5 flex-wrap">
                <span className={`text-xs px-1.5 py-0.5 rounded font-medium ${lifecycle.cls}`}>
                  {device.lifecycleStatus ?? 'PROVISIONED'}
                </span>
                {device.firmwareVersion && (
                  <span className="text-xs text-gray-500">fw {device.firmwareVersion}</span>
                )}
              </div>
              {device.location && (
                <p className="text-gray-500 text-xs mt-1">{device.location}</p>
              )}
              {device.lastSeen && (
                <p className="text-gray-600 text-xs mt-0.5">
                  Last seen {formatDistanceToNow(new Date(device.lastSeen), { addSuffix: true })}
                </p>
              )}
            </button>
          )
        })}
      </div>
    </div>
  )
}
