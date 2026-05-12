'use client'

import { useState, useEffect } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useAuth } from '@/hooks/useAuth'
import { qk } from '@/lib/queryClient'
import { settingsApi } from '@/api/client'
import AppShell from '@/components/AppShell'
import ErrorBoundary from '@/components/ui/ErrorBoundary'

function Toggle({ checked, onChange, disabled }) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      disabled={disabled}
      onClick={() => onChange(!checked)}
      className={`relative inline-flex h-5 w-9 flex-shrink-0 rounded-full border-2 border-transparent transition-colors
        ${checked ? 'bg-sentinel-success' : 'bg-sentinel-700'}
        ${disabled ? 'opacity-50 cursor-not-allowed' : 'cursor-pointer'}`}
    >
      <span className={`pointer-events-none inline-block h-4 w-4 rounded-full bg-white shadow transition-transform
        ${checked ? 'translate-x-4' : 'translate-x-0'}`} />
    </button>
  )
}

function NumericField({ label, value, unit, min, max, step = 0.1, onChange, disabled }) {
  return (
    <div className="flex justify-between items-center py-2.5 border-b border-sentinel-700/50 last:border-0">
      <span className="text-xs text-gray-500">{label}</span>
      <div className="flex items-center gap-1.5">
        <input
          type="number"
          value={value ?? ''}
          min={min}
          max={max}
          step={step}
          disabled={disabled}
          onChange={e => onChange(parseFloat(e.target.value))}
          className="w-24 text-right text-sm bg-sentinel-900 border border-sentinel-700 rounded px-2 py-1 text-gray-200 focus:outline-none focus:border-sentinel-accent disabled:opacity-50 disabled:cursor-not-allowed"
        />
        {unit && <span className="text-xs text-gray-500 w-8">{unit}</span>}
      </div>
    </div>
  )
}

function ReadRow({ label, value, mono }) {
  return (
    <div className="flex justify-between items-center py-2.5 border-b border-sentinel-700/50 last:border-0">
      <span className="text-xs text-gray-500">{label}</span>
      <span className={`text-sm text-gray-200 ${mono ? 'font-mono text-xs' : ''}`}>{value ?? '—'}</span>
    </div>
  )
}

export default function SettingsPage() {
  const { user } = useAuth()
  const qc       = useQueryClient()
  const isAdmin  = user?.role === 'ADMIN'

  const { data: settings, isLoading, isError } = useQuery({
    queryKey: qk.settings(),
    queryFn:  () => settingsApi.get().then(r => r.data),
    enabled:  !!user,
  })

  const [form, setForm] = useState(null)

  useEffect(() => {
    if (settings && !form) {
      setForm({
        temperatureCelsius: settings.thresholds.temperatureCelsius,
        humidityPercent:    settings.thresholds.humidityPercent,
        smokePpm:           settings.thresholds.smokePpm,
        telemetryDays:      settings.retention.telemetryDays,
        auditDays:          settings.retention.auditDays,
        slack:              settings.notifications.slack,
        line:               settings.notifications.line,
        webhook:            settings.notifications.webhook,
      })
    }
  }, [settings, form])

  const saveMutation = useMutation({
    mutationFn: (data) => settingsApi.update(data),
    onSuccess: () => qc.invalidateQueries({ queryKey: qk.settings() }),
  })

  const set = (key, val) => setForm(f => ({ ...f, [key]: val }))

  const hasChanges = form && settings && (
    form.temperatureCelsius !== settings.thresholds.temperatureCelsius ||
    form.humidityPercent    !== settings.thresholds.humidityPercent    ||
    form.smokePpm           !== settings.thresholds.smokePpm           ||
    form.telemetryDays      !== settings.retention.telemetryDays       ||
    form.auditDays          !== settings.retention.auditDays           ||
    form.slack              !== settings.notifications.slack           ||
    form.line               !== settings.notifications.line            ||
    form.webhook            !== settings.notifications.webhook
  )

  const handleSave = () => saveMutation.mutate(form)
  const handleReset = () => setForm(null)

  return (
    <AppShell>
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-xl font-semibold text-white">Settings</h1>
          <p className="text-sm text-gray-500 mt-0.5">
            {isAdmin ? 'Platform configuration' : 'Platform configuration (read-only)'}
          </p>
        </div>
        {isAdmin && hasChanges && (
          <div className="flex gap-2">
            <button
              onClick={handleReset}
              className="px-3 py-2 text-sm border border-sentinel-700 text-gray-400 rounded-lg hover:bg-sentinel-700/50 transition"
            >
              Reset
            </button>
            <button
              onClick={handleSave}
              disabled={saveMutation.isPending}
              className="px-4 py-2 text-sm bg-sentinel-accent/20 border border-sentinel-accent/50 text-sentinel-accent rounded-lg hover:bg-sentinel-accent/30 transition disabled:opacity-50"
            >
              {saveMutation.isPending ? 'Saving…' : 'Save Changes'}
            </button>
          </div>
        )}
      </div>

      {saveMutation.isSuccess && (
        <div role="status" className="mb-4 px-3 py-2 rounded-lg bg-sentinel-success/15 border border-sentinel-success/40 text-sentinel-success text-sm">
          Settings saved successfully.
        </div>
      )}
      {saveMutation.isError && (
        <div role="alert" className="mb-4 px-3 py-2 rounded-lg bg-sentinel-danger/15 border border-sentinel-danger/40 text-sentinel-danger text-sm">
          Failed to save settings. Please try again.
        </div>
      )}

      <ErrorBoundary label="Settings">
        {isLoading ? (
          <p className="text-sm text-gray-500">Loading…</p>
        ) : isError ? (
          <p className="text-sm text-sentinel-danger">Failed to load settings.</p>
        ) : form ? (
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">

            {/* Alert Thresholds */}
            <div className="card">
              <h2 className="text-sm font-semibold text-white mb-4">Alert Thresholds</h2>
              <NumericField label="Temperature (critical above)" value={form.temperatureCelsius} unit="°C"  min={0}   max={200} step={0.5} onChange={v => set('temperatureCelsius', v)} disabled={!isAdmin} />
              <NumericField label="Humidity (critical above)"    value={form.humidityPercent}    unit="%"   min={0}   max={100} step={0.5} onChange={v => set('humidityPercent', v)}    disabled={!isAdmin} />
              <NumericField label="Smoke PPM (critical above)"   value={form.smokePpm}           unit="ppm" min={0}   max={9999} step={1}  onChange={v => set('smokePpm', v)}           disabled={!isAdmin} />
            </div>

            {/* Data Retention */}
            <div className="card">
              <h2 className="text-sm font-semibold text-white mb-4">Data Retention</h2>
              <NumericField label="Telemetry retention" value={form.telemetryDays} unit="days" min={1} max={3650} step={1} onChange={v => set('telemetryDays', v)} disabled={!isAdmin} />
              <NumericField label="Audit log retention" value={form.auditDays}     unit="days" min={1} max={3650} step={1} onChange={v => set('auditDays', v)}     disabled={!isAdmin} />
            </div>

            {/* Notification Channels */}
            <div className="card">
              <h2 className="text-sm font-semibold text-white mb-4">Notification Channels</h2>
              {[
                { key: 'slack',   label: 'Slack' },
                { key: 'line',    label: 'LINE Notify' },
                { key: 'webhook', label: 'Webhook' },
              ].map(({ key, label }) => (
                <div key={key} className="flex justify-between items-center py-2.5 border-b border-sentinel-700/50 last:border-0">
                  <span className="text-xs text-gray-500">{label}</span>
                  <Toggle checked={!!form[key]} onChange={v => set(key, v)} disabled={!isAdmin} />
                </div>
              ))}
            </div>

            {/* Platform (read-only) */}
            <div className="card">
              <h2 className="text-sm font-semibold text-white mb-4">Platform</h2>
              <ReadRow label="API version" value="1"                      mono />
              <ReadRow label="Environment" value={process.env.NODE_ENV} />
              <ReadRow label="Backend"     value="/api/v1"                mono />
            </div>

          </div>
        ) : null}
      </ErrorBoundary>
    </AppShell>
  )
}
