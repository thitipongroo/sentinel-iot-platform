import { formatDistanceToNow } from 'date-fns'
import { alertsApi } from '../api/client'

export default function AlertList({ alerts, onAcknowledge, userRole }) {
  const handleAck = async (id) => {
    try {
      await alertsApi.acknowledge(id)
      onAcknowledge()
    } catch { /* */ }
  }

  const unacked = alerts.filter(a => !a.acknowledged)

  return (
    <div className="card">
      <h2 className="text-white font-semibold mb-4 flex items-center gap-2">
        <span className="text-sentinel-danger">⚠</span> Alerts
        {unacked.length > 0 && (
          <span className="ml-1 bg-sentinel-danger text-white text-xs px-1.5 py-0.5 rounded-full">
            {unacked.length}
          </span>
        )}
      </h2>
      <div className="space-y-2 max-h-64 overflow-y-auto pr-1">
        {alerts.length === 0 && (
          <p className="text-gray-500 text-sm text-center py-6">No alerts</p>
        )}
        {alerts.slice(0, 20).map(alert => (
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
