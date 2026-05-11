'use client'

import { useEffect, useState } from 'react'

export default function VersionBanner() {
  const [visible, setVisible] = useState(false)
  const [rejected, setRejected] = useState(false)

  useEffect(() => {
    const handleMismatch = () => setVisible(true)
    const handleRejected = () => setRejected(true)

    window.addEventListener('sentinel:api-version-mismatch', handleMismatch)
    window.addEventListener('sentinel:api-version-rejected', handleRejected)
    return () => {
      window.removeEventListener('sentinel:api-version-mismatch', handleMismatch)
      window.removeEventListener('sentinel:api-version-rejected', handleRejected)
    }
  }, [])

  if (!visible && !rejected) return null

  return (
    <div className="fixed top-0 left-0 right-0 z-50 bg-yellow-500 text-black text-sm font-medium px-4 py-2 flex items-center justify-between">
      <span>
        {rejected
          ? 'This client version is no longer supported by the server.'
          : 'A new version is available.'}
        {' '}
        <button
          onClick={() => window.location.reload()}
          className="underline font-bold ml-1"
        >
          Refresh to update
        </button>
      </span>
      {!rejected && (
        <button onClick={() => setVisible(false)} className="ml-4 opacity-70 hover:opacity-100">✕</button>
      )}
    </div>
  )
}
