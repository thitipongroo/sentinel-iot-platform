'use client'

import { useState } from 'react'
import { formatDistanceToNow } from 'date-fns'
import { alertsApi } from '@/api/client'

const FILTER_TABS = ['All', 'Unacknowledged']

export default function AlertList({ alerts, onAcknowledge, userRole }) {
  const [filter, setFilter] = useState('All')

  const handleAck = async (id) => {
    try {
      await alertsApi.acknowledge(id)
      onAcknowledge()
    } catch { /* */ }
  }

  const unacked    = alerts.filter(a => !a.acknowledged)
  const displayed  = filter === 'Unacknowledged' ? unacked : alerts

  return (
    <div className="card">
      <div className="flex items-center justify-between mb-3 flex-wrap gap-2">
        <h2 className="text-white font-semibold flex items-center gap-2">
          <span className="text-sentinel-danger">⚠</span> Alerts
          {unacked.length > 0 && (
            <span className="bg-sentinel-danger text-white text-xs px-1.5 py-0.5 rounded-full">
              {unacked.length}
            </span>
          )}
        </h2>
        <div className="flex gap-0.5 border border-sentinel-700 rounded-md overflow-hidden">
          {FILTER_TABS.map(tab => (
            <button
              key={tab}
              onClick={() => setFilter(tab)}
              className={`text-xs px-3 py-1 transition flex items-center gap-1 ${
                filter === tab
                  ? 'bg-sentinel-accent text-sentinel-900 font-semibold'
                  : 'text-gray-400 hover:text-white'
              }`}
            >
              {tab}
              {tab === 'Unacknowledged' && unacked.length > 0 && (
                <span className="bg-sentinel-danger/80 text-white text-xs px-1 rounded-full leading-tight">
                  {unacked.length}
                </span>
              )}
            </button>
          ))}
        </div>
      </div>

      <div className="space-y-2 max-h-64 overflow-y-auto pr-1">
        {displayed.length === 0 && (
          <p className="text-gray-500 text-sm text-center py-6">
            {filter === 'Unacknowledged' ? 'No active alerts' : 'No alerts'}
          </p>
        )}
        {displayed.slice(0, 20).map(alert => (
          <div
            key={alert.id}
            className={`flex items-start justify-between p-3 rounded-lg border text-sm ${
              alert.acknowledged
                ? 'border-sentinel-700 bg-sentinel-900/30 opacity-50'
                : alert.level === 'CRITICAL'
                ? 'border-sentinel-danger/40 bg-sentinel-danger/10'
                : 'border-sentinel-warning/40 bg-sentinel-warning/10'
            }`}
          >
            <div className="flex-1 min-w-0">
              <div className="flex items-center gap-2 mb-0.5">
                <span className={alert.level === 'CRITICAL' ? 'badge-critical' : 'badge-warning'}>
                  {alert.level}
                </span>
                <span className="text-gray-500 text-xs">
                  {formatDistanceToNow(new Date(alert.createdAt), { addSuffix: true })}
                </span>
              </div>
              <p className="text-gray-300 text-xs truncate">{alert.message}</p>
            </div>
            {!alert.acknowledged && userRole === 'ADMIN' && (
              <button
                onClick={() => handleAck(alert.id)}
                className="ml-3 text-xs text-gray-500 hover:text-white shrink-0"
              >
                Ack
              </button>
            )}
          </div>
        ))}
      </div>
    </div>
  )
}
