import { QueryClient } from '@tanstack/react-query'

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      // Data is considered fresh for 10 s — matches previous polling interval.
      // React Query will serve cached data instantly and revalidate in background.
      staleTime:      10_000,
      // Silently refetch every 30 s while the tab is visible.
      refetchInterval: 30_000,
      refetchIntervalInBackground: false,
      // Retry failed requests twice with exponential backoff before surfacing error.
      retry:          2,
      retryDelay:     (attempt) => Math.min(1000 * 2 ** attempt, 10_000),
    },
    mutations: {
      // Retry mutations once; most mutations are not idempotent so keep it low.
      retry: 1,
    },
  },
})

// ── Query key factory ────────────────────────────────────────────────────────
// Centralised key definitions prevent cache key drift across features.
export const qk = {
  devices:          ()            => ['devices'],
  device:           (id)          => ['devices', id],
  deviceCaps:       (id)          => ['devices', id, 'capabilities'],
  alerts:           ()            => ['alerts'],
  alertsUnacked:    ()            => ['alerts', 'unacknowledged'],
  telemetryLatest:  (id, limit)   => ['telemetry', id, 'latest', limit],
  telemetryRange:   (id, from, to)=> ['telemetry', id, 'range', from, to],
  telemetryHourly:  (id, from, to)=> ['telemetry', id, 'hourly', from, to],
  telemetryCached:  (id)          => ['telemetry', id, 'cache'],
  stats:            ()            => ['stats'],
}
