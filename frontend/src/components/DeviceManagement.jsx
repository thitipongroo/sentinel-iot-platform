'use client'

import { useState } from 'react'
import { devicesApi } from '@/api/client'

const LIFECYCLE_STATES = ['PROVISIONED', 'ACTIVE', 'INACTIVE', 'DECOMMISSIONED']

const LIFECYCLE_COLORS = {
  PROVISIONED:    'text-gray-300',
  ACTIVE:         'text-sentinel-success',
  INACTIVE:       'text-sentinel-warning',
  DECOMMISSIONED: 'text-sentinel-danger'
}

const TRANSITION_STYLE = {
  ACTIVE:         'border-sentinel-success/50 text-sentinel-success hover:bg-sentinel-success/10',
  INACTIVE:       'border-sentinel-warning/50 text-sentinel-warning hover:bg-sentinel-warning/10',
  DECOMMISSIONED: 'border-sentinel-danger/50 text-sentinel-danger hover:bg-sentinel-danger/10',
  PROVISIONED:    'border-sentinel-700 text-gray-300 hover:bg-sentinel-700/50'
}

// semver-ish: major.minor.patch with optional pre-release
const SEMVER_RE = /^\d+\.\d+\.\d+(-[\w.]+)?$/

export default function DeviceManagement({ device, onUpdate }) {
  const [firmwareInput,    setFirmwareInput]    = useState('')
  const [loadingLifecycle, setLoadingLifecycle] = useState(null)
  const [loadingFirmware,  setLoadingFirmware]  = useState(false)
  const [error,            setError]            = useState('')

  if (!device) return null

  const isDecommissioned = device.lifecycleStatus === 'DECOMMISSIONED'

  const handleLifecycle = async (status) => {
    setError('')
    setLoadingLifecycle(status)
    try {
      await devicesApi.updateLifecycle(device.id, status)
      onUpdate()
    } catch (e) {
      setError(e.response?.data?.message ?? 'Lifecycle update failed')
    } finally {
      setLoadingLifecycle(null)
    }
  }

  const handleFirmware = async (e) => {
    e.preventDefault()
    const ver = firmwareInput.trim()
    if (!ver) return
    if (!SEMVER_RE.test(ver)) {
      setError('Version must follow semver (e.g. 1.2.3 or 1.2.3-beta.1)')
      return
    }
    setError('')
    setLoadingFirmware(true)
    try {
      await devicesApi.updateFirmware(device.id, ver)
      setFirmwareInput('')
      onUpdate()
    } catch (e) {
      setError(e.response?.data?.message ?? 'Firmware update failed')
    } finally {
      setLoadingFirmware(false)
    }
  }

  const currentLifecycle = device.lifecycleStatus ?? 'PROVISIONED'

  return (
    <div className="card">
      <h2 className="text-white font-semibold mb-4 flex items-center gap-2">
        <span className="text-sentinel-accent">⚙</span> Device Management
        <span className="text-gray-500 text-sm font-normal">— {device.name}</span>
      </h2>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        <div>
          <p className="text-gray-400 text-xs uppercase tracking-wide mb-2">Lifecycle Status</p>
          <p className="text-sm mb-3">
            Current:{' '}
            <span className={`font-semibold ${LIFECYCLE_COLORS[currentLifecycle] ?? 'text-gray-300'}`}>
              {currentLifecycle}
            </span>
          </p>
          <div className="flex flex-wrap gap-2">
            {LIFECYCLE_STATES
              .filter(s => s !== currentLifecycle)
              .map(status => (
                <button
                  key={status}
                  disabled={isDecommissioned || loadingLifecycle === status}
                  onClick={() => handleLifecycle(status)}
                  className={`text-xs px-3 py-1.5 rounded-md border transition disabled:opacity-40 disabled:cursor-not-allowed ${TRANSITION_STYLE[status]}`}
                >
                  {loadingLifecycle === status ? '…' : `→ ${status}`}
                </button>
              ))}
          </div>
          {isDecommissioned && (
            <p className="text-gray-500 text-xs mt-2">
              Device is decommissioned — no further transitions allowed.
            </p>
          )}
        </div>

        <div>
          <p className="text-gray-400 text-xs uppercase tracking-wide mb-2">Firmware Version</p>
          <p className="text-sm mb-3">
            Current:{' '}
            <span className="font-semibold text-gray-300">
              {device.firmwareVersion ?? 'unknown'}
            </span>
          </p>
          <form onSubmit={handleFirmware} className="flex gap-2">
            <input
              type="text"
              placeholder="e.g. 1.2.3"
              value={firmwareInput}
              onChange={e => setFirmwareInput(e.target.value)}
              disabled={isDecommissioned || loadingFirmware}
              className="flex-1 px-3 py-1.5 text-sm bg-sentinel-900 border border-sentinel-700 rounded-md text-gray-300 placeholder-gray-600 focus:outline-none focus:border-sentinel-accent disabled:opacity-40"
            />
            <button
              type="submit"
              disabled={isDecommissioned || loadingFirmware || !firmwareInput.trim()}
              className="text-xs px-4 py-1.5 bg-sentinel-accent/20 border border-sentinel-accent/50 text-sentinel-accent rounded-md hover:bg-sentinel-accent/30 transition disabled:opacity-40 disabled:cursor-not-allowed"
            >
              {loadingFirmware ? '…' : 'Update'}
            </button>
          </form>
        </div>
      </div>

      {error && (
        <p className="mt-3 text-xs text-sentinel-danger">{error}</p>
      )}
    </div>
  )
}
