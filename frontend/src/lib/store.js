import { create } from 'zustand'

/**
 * Normalized client state store.
 *
 * Server state (devices, alerts, telemetry) lives in React Query.
 * This store owns:
 *   - Filter criteria (UI intent, not data)
 *   - Selected device ID (navigation state)
 *   - Network status (offline detection)
 *
 * Normalization: devices and alerts are stored by ID for O(1) updates
 * when WebSocket pushes a partial payload.
 */
export const useStore = create((set) => ({
  // ── UI state ────────────────────────────────────────────────────────────────
  selectedDeviceId: null,
  setSelectedDeviceId: (id) => set({ selectedDeviceId: id }),

  // ── Filters ─────────────────────────────────────────────────────────────────
  filters: {
    search:    '',
    status:    'ALL',       // ALL | ONLINE | OFFLINE
    lifecycle: 'ALL',       // ALL | PROVISIONED | ACTIVE | INACTIVE | DECOMMISSIONED
    sortBy:    'name',      // name | lastSeen | status
    sortDir:   'asc',
  },
  setFilter: (key, value) =>
    set((s) => ({ filters: { ...s.filters, [key]: value } })),
  resetFilters: () =>
    set({ filters: { search: '', status: 'ALL', lifecycle: 'ALL', sortBy: 'name', sortDir: 'asc' } }),

  // ── Network status ───────────────────────────────────────────────────────────
  isOffline: false,
  setOffline: (offline) => set({ isOffline: offline }),
}))
