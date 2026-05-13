'use client'

import { useRef, useMemo, useCallback } from 'react'
import { useVirtualizer } from '@tanstack/react-virtual'
import { formatDistanceToNow } from 'date-fns'
import { clsx } from 'clsx'
import { ArrowUpDown, Search } from 'lucide-react'
import Badge from '@/components/ui/Badge'
import Select from '@/components/ui/Select'
import { useStore } from '@/lib/store'

const ROW_HEIGHT_PX = 72
const OVERSCAN       = 5

const LIFECYCLE_VARIANT = {
  PROVISIONED:    'default',
  ACTIVE:         'success',
  INACTIVE:       'warning',
  DECOMMISSIONED: 'danger',
}

const STATUS_OPTS = [
  { value: 'ALL',     label: 'All statuses' },
  { value: 'ONLINE',  label: 'Online' },
  { value: 'OFFLINE', label: 'Offline' },
]

const LIFECYCLE_OPTS = [
  { value: 'ALL',            label: 'All lifecycles' },
  { value: 'PROVISIONED',    label: 'Provisioned' },
  { value: 'ACTIVE',         label: 'Active' },
  { value: 'INACTIVE',       label: 'Inactive' },
  { value: 'DECOMMISSIONED', label: 'Decommissioned' },
]

const SORT_OPTS = [
  { value: 'name',     label: 'Name' },
  { value: 'lastSeen', label: 'Last seen' },
  { value: 'status',   label: 'Status' },
]

/**
 * Virtualized, filterable device table.
 *
 * • Renders only visible rows regardless of list size — handles 10 000+ devices.
 * • Filtering on search, connection status, and lifecycle state.
 * • Keyboard-navigable: Tab focuses rows, Enter/Space selects.
 * • ARIA grid semantics for screen-reader compatibility.
 */
export default function DeviceTable({ devices = [], selected, onSelect, lastMessage }) {
  const { filters, setFilter } = useStore()
  const parentRef = useRef(null)

  // ── Live status override from WebSocket ─────────────────────────────────────
  const getStatus = useCallback(
    (device) =>
      lastMessage?.deviceId === device.name ? 'ONLINE' : device.status,
    [lastMessage]
  )

  // ── Filter + sort (runs in useMemo — re-runs only when deps change) ──────────
  const filtered = useMemo(() => {
    let rows = devices.filter((d) => {
      const status    = getStatus(d)
      const matchSearch =
        !filters.search ||
        d.name.toLowerCase().includes(filters.search.toLowerCase()) ||
        (d.location ?? '').toLowerCase().includes(filters.search.toLowerCase()) ||
        (d.firmwareVersion ?? '').includes(filters.search)
      const matchStatus    = filters.status    === 'ALL' || status === filters.status
      const matchLifecycle = filters.lifecycle === 'ALL' || d.lifecycleStatus === filters.lifecycle
      return matchSearch && matchStatus && matchLifecycle
    })

    rows.sort((a, b) => {
      let va, vb
      if (filters.sortBy === 'lastSeen') {
        va = a.lastSeen ? new Date(a.lastSeen).getTime() : 0
        vb = b.lastSeen ? new Date(b.lastSeen).getTime() : 0
      } else if (filters.sortBy === 'status') {
        va = getStatus(a)
        vb = getStatus(b)
      } else {
        va = a.name?.toLowerCase() ?? ''
        vb = b.name?.toLowerCase() ?? ''
      }
      const cmp = va < vb ? -1 : va > vb ? 1 : 0
      return filters.sortDir === 'asc' ? cmp : -cmp
    })

    return rows
  }, [devices, filters, getStatus])

  // ── Virtualizer ──────────────────────────────────────────────────────────────
  const virtualizer = useVirtualizer({
    count:        filtered.length,
    getScrollElement: () => parentRef.current,
    estimateSize: () => ROW_HEIGHT_PX,
    overscan:     OVERSCAN,
  })

  const toggleSort = (col) => {
    if (filters.sortBy === col) {
      setFilter('sortDir', filters.sortDir === 'asc' ? 'desc' : 'asc')
    } else {
      setFilter('sortBy', col)
      setFilter('sortDir', 'asc')
    }
  }

  return (
    <section className="card h-full flex flex-col" aria-label="Device list">

      {/* ── Filter bar ──────────────────────────────────────────────────────── */}
      <div className="flex items-center gap-2 mb-3" role="search" aria-label="Filter devices">
        {/* Search */}
        <div className="relative flex items-center flex-1 min-w-0">
          <Search
            size={14}
            className="absolute left-2 text-gray-500 pointer-events-none"
            aria-hidden="true"
          />
          <input
            type="search"
            aria-label="Search by name, location, or firmware"
            placeholder="Search…"
            value={filters.search}
            onChange={(e) => setFilter('search', e.target.value)}
            className="w-full pl-7 pr-3 py-1.5 text-sm bg-sentinel-900 border border-sentinel-700
                       rounded-md text-gray-300 placeholder-gray-600 focus:outline-none
                       focus:border-sentinel-accent"
          />
        </div>

        <Select
          id="filter-status"
          label="Status"
          value={filters.status}
          onChange={(v) => setFilter('status', v)}
          options={STATUS_OPTS}
        />
        <Select
          id="filter-lifecycle"
          label="Lifecycle"
          value={filters.lifecycle}
          onChange={(v) => setFilter('lifecycle', v)}
          options={LIFECYCLE_OPTS}
        />
        <Select
          id="filter-sort"
          label="Sort by"
          value={filters.sortBy}
          onChange={(v) => setFilter('sortBy', v)}
          options={SORT_OPTS}
        />

        {(filters.search || filters.status !== 'ALL' || filters.lifecycle !== 'ALL') && (
          <button
            onClick={() => useStore.getState().resetFilters()}
            className="text-xs text-gray-500 hover:text-white underline underline-offset-2 whitespace-nowrap"
            aria-label="Clear all filters"
          >
            Clear
          </button>
        )}
      </div>

      {/* ── Column headers ──────────────────────────────────────────────────── */}
      <div
        role="row"
        className="grid grid-cols-[1fr_auto_auto] gap-2 px-3 pb-1 text-xs text-gray-500 font-medium border-b border-sentinel-700"
        aria-hidden="true"
      >
        <button
          onClick={() => toggleSort('name')}
          className="flex items-center gap-1 hover:text-gray-300 text-left"
        >
          Device <ArrowUpDown size={11} aria-hidden="true" />
        </button>
        <button
          onClick={() => toggleSort('status')}
          className="flex items-center gap-1 hover:text-gray-300"
        >
          Status <ArrowUpDown size={11} aria-hidden="true" />
        </button>
        <button
          onClick={() => toggleSort('lastSeen')}
          className="flex items-center gap-1 hover:text-gray-300"
        >
          Last seen <ArrowUpDown size={11} aria-hidden="true" />
        </button>
      </div>

      {/* ── Result count ────────────────────────────────────────────────────── */}
      <p className="text-xs text-gray-600 px-1 pt-1 pb-0.5" aria-live="polite">
        {filtered.length} of {devices.length} device{devices.length !== 1 ? 's' : ''}
      </p>

      {/* ── Virtualised list ─────────────────────────────────────────────────── */}
      <div
        ref={parentRef}
        className="flex-1 overflow-y-auto"
        style={{ minHeight: 200, maxHeight: 'calc(100vh - 23rem)' }}
      >
        {filtered.length === 0 ? (
          <p className="text-gray-500 text-sm text-center py-12">
            {devices.length === 0 ? 'No devices registered' : 'No devices match the filters'}
          </p>
        ) : (
          <div
            role="grid"
            aria-label="Device rows"
            aria-rowcount={filtered.length}
            style={{ height: virtualizer.getTotalSize(), position: 'relative' }}
          >
            {virtualizer.getVirtualItems().map((vRow) => {
              const device   = filtered[vRow.index]
              const status   = getStatus(device)
              const isSelected = selected?.id === device.id

              return (
                <div
                  key={device.id}
                  role="row"
                  aria-rowindex={vRow.index + 1}
                  aria-selected={isSelected}
                  tabIndex={0}
                  style={{
                    position:  'absolute',
                    top:        vRow.start,
                    left:       0,
                    right:      0,
                    height:     ROW_HEIGHT_PX,
                  }}
                  onClick={() => onSelect(device)}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter' || e.key === ' ') {
                      e.preventDefault()
                      onSelect(device)
                    }
                  }}
                  className={clsx(
                    'grid grid-cols-[1fr_auto_auto] gap-2 items-center px-3 rounded-lg border',
                    'cursor-pointer transition-all focus:outline-none focus:ring-1 focus:ring-sentinel-accent',
                    isSelected
                      ? 'border-sentinel-accent bg-sentinel-accent/10'
                      : 'border-transparent hover:border-sentinel-700 hover:bg-sentinel-900/50'
                  )}
                >
                  {/* Name + lifecycle + location */}
                  <div className="min-w-0">
                    <p className="text-white text-sm font-medium truncate">{device.name}</p>
                    <div className="flex items-center gap-1.5 mt-0.5 flex-wrap">
                      <Badge variant={LIFECYCLE_VARIANT[device.lifecycleStatus] ?? 'default'}>
                        {device.lifecycleStatus ?? 'PROVISIONED'}
                      </Badge>
                      {device.firmwareVersion && (
                        <span className="text-xs text-gray-600">fw {device.firmwareVersion}</span>
                      )}
                      {device.location && (
                        <span className="text-xs text-gray-600 truncate">{device.location}</span>
                      )}
                    </div>
                  </div>

                  {/* Online/offline */}
                  <Badge variant={status === 'ONLINE' ? 'online' : 'offline'}>
                    {status}
                  </Badge>

                  {/* Last seen */}
                  <span className="text-xs text-gray-600 w-20 text-right shrink-0">
                    {device.lastSeen
                      ? formatDistanceToNow(new Date(device.lastSeen), { addSuffix: true })
                      : '—'}
                  </span>
                </div>
              )
            })}
          </div>
        )}
      </div>
    </section>
  )
}
