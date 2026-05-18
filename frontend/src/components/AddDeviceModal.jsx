'use client'

import { useState } from 'react'

export default function AddDeviceModal({ onClose, onCreate }) {
  const [form, setForm] = useState({ name: '', description: '', location: '' })
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  const handleSubmit = async (e) => {
    e.preventDefault()
    if (!form.name.trim()) { setError('Name is required'); return }
    setError('')
    setLoading(true)
    try {
      await onCreate(form)
      onClose()
    } catch (err) {
      setError(err.response?.data?.message ?? 'Failed to create device')
    } finally {
      setLoading(false)
    }
  }

  const fields = [
    { id: 'name',        label: 'Name *',      placeholder: 'sensor-101' },
    { id: 'description', label: 'Description',  placeholder: 'Assembly line sensor' },
    { id: 'location',    label: 'Location',     placeholder: 'Building 3 — Zone A' },
  ]

  return (
    <div className="fixed inset-0 bg-black/60 flex items-center justify-center z-50" onClick={onClose}>
      <div className="card w-full max-w-md mx-4" onClick={e => e.stopPropagation()}>
        <h2 className="text-white font-semibold mb-4">Register New Device</h2>
        <form onSubmit={handleSubmit} className="space-y-3">
          {fields.map(f => (
            <div key={f.id}>
              <label className="block text-xs text-gray-400 mb-1">{f.label}</label>
              <input
                type="text"
                placeholder={f.placeholder}
                value={form[f.id]}
                onChange={e => setForm(p => ({ ...p, [f.id]: e.target.value }))}
                className="w-full px-3 py-2 text-sm bg-sentinel-900 border border-sentinel-700 rounded-lg text-gray-300 placeholder-gray-600 focus:outline-none focus:border-sentinel-accent"
              />
            </div>
          ))}
          {error && <p className="text-xs text-sentinel-danger">{error}</p>}
          <div className="flex gap-3 pt-2">
            <button
              type="button"
              onClick={onClose}
              className="flex-1 px-4 py-2 text-sm border border-sentinel-700 text-gray-400 rounded-lg hover:bg-sentinel-700/50 transition"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={loading}
              className="flex-1 px-4 py-2 text-sm bg-sentinel-accent/20 border border-sentinel-accent/50 text-sentinel-accent rounded-lg hover:bg-sentinel-accent/30 transition disabled:opacity-50"
            >
              {loading ? 'Registering…' : 'Register'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
