import { clsx } from 'clsx'
import { formatDistanceToNow } from 'date-fns'

export default function DeviceList({ devices, selected, onSelect, lastMessage }) {
  const getStatus = (device) => {
    if (lastMessage?.deviceId === device.name) return 'ONLINE'
    return device.status
  }

  return (
    <div className="card h-full">
      <h2 className="text-white font-semibold mb-4 flex items-center gap-2">
        <span className="text-sentinel-accent">▣</span> Devices
        <span className="ml-auto text-xs text-gray-500">{devices.length} total</span>
      </h2>
      <div className="space-y-2 overflow-y-auto max-h-[520px] pr-1">
        {devices.length === 0 && (
          <p className="text-gray-500 text-sm text-center py-8">No devices registered</p>
        )}
        {devices.map(device => {
          const status = getStatus(device)
          const isSelected = selected?.id === device.id
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
              <div className="flex items-center justify-between">
                <span className="text-white text-sm font-medium">{device.name}</span>
                <span className={status === 'ONLINE' ? 'badge-online' : 'badge-offline'}>
                  {status}
                </span>
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
