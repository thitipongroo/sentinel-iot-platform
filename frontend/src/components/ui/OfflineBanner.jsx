'use client'

import { useEffect } from 'react'
import { WifiOff } from 'lucide-react'
import { useStore } from '@/lib/store'

/**
 * Detects network connectivity loss and shows a dismissible banner.
 * Uses the browser Navigation API; falls back gracefully in SSR.
 */
export default function OfflineBanner() {
  const { isOffline, setOffline } = useStore()

  useEffect(() => {
    if (typeof window === 'undefined') return

    const onOnline  = () => setOffline(false)
    const onOffline = () => setOffline(true)

    // Sync initial state — window.navigator.onLine may already be false
    setOffline(!window.navigator.onLine)

    window.addEventListener('online',  onOnline)
    window.addEventListener('offline', onOffline)
    return () => {
      window.removeEventListener('online',  onOnline)
      window.removeEventListener('offline', onOffline)
    }
  }, [setOffline])

  if (!isOffline) return null

  return (
    <div
      role="status"
      aria-live="polite"
      className="fixed top-0 inset-x-0 z-50 flex items-center justify-center gap-2
                 bg-sentinel-warning text-sentinel-900 text-sm font-semibold py-2 px-4
                 shadow-lg"
    >
      <WifiOff size={16} aria-hidden="true" />
      You are offline — live data is paused. Reconnecting…
    </div>
  )
}
