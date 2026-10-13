'use client'

import { useQuery } from '@tanstack/react-query'
import { useAuth } from '@/hooks/useAuth'
import { qk } from '@/lib/queryClient'
import { devicesApi } from '@/api/client'
import AppShell from '@/components/AppShell'
import ErrorBoundary from '@/components/ui/ErrorBoundary'
import LakehouseExplorer from '@/components/lakehouse/LakehouseExplorer'

export default function LakehousePage() {
  const { user } = useAuth()

  const { data: devices = [] } = useQuery({
    queryKey: qk.devices(),
    queryFn: () => devicesApi.list().then(r => r.data),
    enabled: !!user,
  })

  return (
    <AppShell>
      {/* Top Header */}
      <div className="flex items-center justify-between mb-6 flex-wrap gap-4">
        <div>
          <div className="flex items-center gap-2">
            <h1 className="text-xl font-bold text-white tracking-tight">DuckDB Lakehouse Historical Analytics</h1>
            <span className="text-xs px-2.5 py-0.5 rounded-full bg-sentinel-accent/20 border border-sentinel-accent/40 text-sentinel-accent font-semibold">
              RFC-001 Phase 3
            </span>
          </div>
          <p className="text-sm text-gray-400 mt-0.5">
            Embedded DuckDB OLAP engine querying compressed columnar Apache Parquet files across multi-year historical horizons in sub-second latency
          </p>
        </div>

        {/* Status Pills */}
        <div className="flex items-center gap-2 text-xs">
          <div className="px-3 py-1.5 rounded-lg bg-sentinel-800 border border-sentinel-700/80 flex items-center gap-2">
            <span className="w-2 h-2 rounded-full bg-sentinel-success" />
            <span className="text-gray-300 font-medium">Parquet Engine: DuckDB 1.1</span>
          </div>
          <div className="px-3 py-1.5 rounded-lg bg-sentinel-800 border border-sentinel-700/80 flex items-center gap-2">
            <span className="w-2 h-2 rounded-full bg-sentinel-accent" />
            <span className="text-gray-300 font-medium">Partition Layout: org_id/year/month</span>
          </div>
        </div>
      </div>

      <ErrorBoundary label="Lakehouse Explorer">
        <LakehouseExplorer devices={devices} userRole={user?.role} />
      </ErrorBoundary>
    </AppShell>
  )
}
