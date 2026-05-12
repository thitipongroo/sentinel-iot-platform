'use client'

'use client'

import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useAuth } from '@/hooks/useAuth'
import { qk } from '@/lib/queryClient'
import { usersApi } from '@/api/client'
import AppShell from '@/components/AppShell'
import ErrorBoundary from '@/components/ui/ErrorBoundary'

const ROLE_COLORS = {
  ADMIN:    'text-sentinel-accent',
  OPERATOR: 'text-gray-400',
}

function AddUserModal({ onClose, onCreate }) {
  const [form, setForm]   = useState({ username: '', password: '', role: 'OPERATOR' })
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  const handleSubmit = async (e) => {
    e.preventDefault()
    if (!form.username.trim()) { setError('Username is required'); return }
    if (form.password.length < 8) { setError('Password must be at least 8 characters'); return }
    setError('')
    setLoading(true)
    try {
      await onCreate(form)
      onClose()
    } catch (err) {
      setError(err.response?.data?.message ?? 'Failed to create user')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="fixed inset-0 bg-black/60 flex items-center justify-center z-50" onClick={onClose}>
      <div className="card w-full max-w-md mx-4" onClick={e => e.stopPropagation()}>
        <h2 className="text-white font-semibold mb-4">Add User</h2>
        <form onSubmit={handleSubmit} className="space-y-3">
          <div>
            <label className="block text-xs text-gray-400 mb-1">Username *</label>
            <input
              type="text"
              placeholder="john.doe"
              value={form.username}
              onChange={e => setForm(p => ({ ...p, username: e.target.value }))}
              className="w-full px-3 py-2 text-sm bg-sentinel-900 border border-sentinel-700 rounded-lg text-gray-300 placeholder-gray-600 focus:outline-none focus:border-sentinel-accent"
            />
          </div>
          <div>
            <label className="block text-xs text-gray-400 mb-1">Password *</label>
            <input
              type="password"
              placeholder="Min 8 characters"
              value={form.password}
              onChange={e => setForm(p => ({ ...p, password: e.target.value }))}
              className="w-full px-3 py-2 text-sm bg-sentinel-900 border border-sentinel-700 rounded-lg text-gray-300 placeholder-gray-600 focus:outline-none focus:border-sentinel-accent"
            />
          </div>
          <div>
            <label className="block text-xs text-gray-400 mb-1">Role *</label>
            <select
              value={form.role}
              onChange={e => setForm(p => ({ ...p, role: e.target.value }))}
              className="w-full px-3 py-2 text-sm bg-sentinel-900 border border-sentinel-700 rounded-lg text-gray-300 focus:outline-none focus:border-sentinel-accent"
            >
              <option value="OPERATOR">OPERATOR</option>
              <option value="ADMIN">ADMIN</option>
            </select>
          </div>
          {error && <p className="text-xs text-sentinel-danger">{error}</p>}
          <div className="flex gap-3 pt-2">
            <button type="button" onClick={onClose}
              className="flex-1 px-4 py-2 text-sm border border-sentinel-700 text-gray-400 rounded-lg hover:bg-sentinel-700/50 transition">
              Cancel
            </button>
            <button type="submit" disabled={loading}
              className="flex-1 px-4 py-2 text-sm bg-sentinel-accent/20 border border-sentinel-accent/50 text-sentinel-accent rounded-lg hover:bg-sentinel-accent/30 transition disabled:opacity-50">
              {loading ? 'Creating…' : 'Create User'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

function ResetPasswordModal({ username, onClose, onReset }) {
  const [password, setPassword] = useState('')
  const [error, setError]       = useState('')
  const [loading, setLoading]   = useState(false)

  const handleSubmit = async (e) => {
    e.preventDefault()
    if (password.length < 8) { setError('Password must be at least 8 characters'); return }
    setError('')
    setLoading(true)
    try {
      await onReset(password)
      onClose()
    } catch (err) {
      setError(err.response?.data?.message ?? 'Failed to reset password')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="fixed inset-0 bg-black/60 flex items-center justify-center z-50" onClick={onClose}>
      <div className="card w-full max-w-sm mx-4" onClick={e => e.stopPropagation()}>
        <h2 className="text-white font-semibold mb-1">Reset Password</h2>
        <p className="text-xs text-gray-500 mb-4">Set a new password for <span className="text-gray-300">{username}</span></p>
        <form onSubmit={handleSubmit} className="space-y-3">
          <div>
            <label className="block text-xs text-gray-400 mb-1">New Password *</label>
            <input
              type="password"
              placeholder="Min 8 characters"
              value={password}
              onChange={e => setPassword(e.target.value)}
              className="w-full px-3 py-2 text-sm bg-sentinel-900 border border-sentinel-700 rounded-lg text-gray-300 placeholder-gray-600 focus:outline-none focus:border-sentinel-accent"
              autoFocus
            />
          </div>
          {error && <p className="text-xs text-sentinel-danger">{error}</p>}
          <div className="flex gap-3 pt-1">
            <button type="button" onClick={onClose}
              className="flex-1 px-4 py-2 text-sm border border-sentinel-700 text-gray-400 rounded-lg hover:bg-sentinel-700/50 transition">
              Cancel
            </button>
            <button type="submit" disabled={loading}
              className="flex-1 px-4 py-2 text-sm bg-sentinel-accent/20 border border-sentinel-accent/50 text-sentinel-accent rounded-lg hover:bg-sentinel-accent/30 transition disabled:opacity-50">
              {loading ? 'Resetting…' : 'Reset Password'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

export default function UsersPage() {
  const { user } = useAuth()
  const qc = useQueryClient()
  const [showModal, setShowModal]           = useState(false)
  const [confirmDelete, setConfirmDelete]   = useState(null)
  const [resetTarget, setResetTarget]       = useState(null)

  const { data: users = [], isLoading } = useQuery({
    queryKey: qk.users(),
    queryFn:  () => usersApi.list().then(r => r.data),
    enabled:  !!user,
  })

  const createMutation = useMutation({
    mutationFn: (data) => usersApi.create(data),
    onSuccess:  () => qc.invalidateQueries({ queryKey: qk.users() }),
  })

  const deleteMutation = useMutation({
    mutationFn: (username) => usersApi.delete(username),
    onSuccess:  () => {
      qc.invalidateQueries({ queryKey: qk.users() })
      setConfirmDelete(null)
    },
  })

  const resetPasswordMutation = useMutation({
    mutationFn: ({ username, newPassword }) => usersApi.resetPassword(username, newPassword),
  })

  const roleMutation = useMutation({
    mutationFn: ({ username, role }) => usersApi.changeRole(username, role),
    onSuccess:  () => qc.invalidateQueries({ queryKey: qk.users() }),
  })

  const isSelf = (u) => u.username === user?.username

  return (
    <AppShell>
      {resetTarget && (
        <ResetPasswordModal
          username={resetTarget}
          onClose={() => setResetTarget(null)}
          onReset={(newPassword) => resetPasswordMutation.mutateAsync({ username: resetTarget, newPassword })}
        />
      )}

      {showModal && (
        <AddUserModal
          onClose={() => setShowModal(false)}
          onCreate={(data) => createMutation.mutateAsync(data)}
        />
      )}

      {confirmDelete && (
        <div className="fixed inset-0 bg-black/60 flex items-center justify-center z-50"
          onClick={() => setConfirmDelete(null)}>
          <div className="card w-full max-w-sm mx-4" onClick={e => e.stopPropagation()}>
            <h2 className="text-white font-semibold mb-2">Delete User</h2>
            <p className="text-sm text-gray-400 mb-4">
              Remove <span className="text-gray-200 font-medium">{confirmDelete}</span>? This cannot be undone.
            </p>
            <div className="flex gap-3">
              <button onClick={() => setConfirmDelete(null)}
                className="flex-1 px-4 py-2 text-sm border border-sentinel-700 text-gray-400 rounded-lg hover:bg-sentinel-700/50 transition">
                Cancel
              </button>
              <button
                onClick={() => deleteMutation.mutate(confirmDelete)}
                disabled={deleteMutation.isPending}
                className="flex-1 px-4 py-2 text-sm bg-sentinel-danger/20 border border-sentinel-danger/50 text-sentinel-danger rounded-lg hover:bg-sentinel-danger/30 transition disabled:opacity-50">
                {deleteMutation.isPending ? 'Deleting…' : 'Delete'}
              </button>
            </div>
          </div>
        </div>
      )}

      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-xl font-semibold text-white">Users</h1>
          <p className="text-sm text-gray-500 mt-0.5">{users.length} member{users.length !== 1 ? 's' : ''}</p>
        </div>
        <button
          onClick={() => setShowModal(true)}
          className="px-4 py-2 text-sm bg-sentinel-accent/20 border border-sentinel-accent/50 text-sentinel-accent rounded-lg hover:bg-sentinel-accent/30 transition"
        >
          + Add User
        </button>
      </div>

      <ErrorBoundary label="Users table">
        <div className="card p-0 overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-sentinel-700 text-xs text-gray-500 uppercase tracking-wide">
                  {['Username', 'Role', 'Actions', ''].map(h => (
                    <th key={h} className="px-4 py-3 text-left font-medium">{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {isLoading ? (
                  <tr><td colSpan={4} className="px-4 py-8 text-center text-gray-500">Loading…</td></tr>
                ) : users.length === 0 ? (
                  <tr><td colSpan={4} className="px-4 py-8 text-center text-gray-500">No users found</td></tr>
                ) : users.map(u => (
                  <tr key={u.id} className="border-b border-sentinel-700/50 hover:bg-sentinel-700/20 transition-colors">
                    <td className="px-4 py-3 font-medium text-gray-200">
                      {u.username}
                      {isSelf(u) && <span className="ml-2 text-xs text-gray-500">(you)</span>}
                    </td>
                    <td className="px-4 py-3">
                      <select
                        value={u.role}
                        disabled={isSelf(u) || roleMutation.isPending}
                        onChange={e => roleMutation.mutate({ username: u.username, role: e.target.value })}
                        className={`text-xs px-2 py-1 bg-sentinel-800 border border-sentinel-700 rounded text-gray-300 focus:outline-none focus:border-sentinel-accent disabled:opacity-50 disabled:cursor-not-allowed ${ROLE_COLORS[u.role] ?? ''}`}
                      >
                        <option value="OPERATOR">OPERATOR</option>
                        <option value="ADMIN">ADMIN</option>
                      </select>
                    </td>
                    <td className="px-4 py-3">
                      <button
                        onClick={() => setConfirmDelete(u.username)}
                        disabled={isSelf(u)}
                        className="text-xs px-3 py-1 border border-sentinel-700 text-sentinel-danger rounded hover:bg-sentinel-danger/10 transition disabled:opacity-30 disabled:cursor-not-allowed"
                      >
                        Delete
                      </button>
                    </td>
                    <td className="px-4 py-3">
                      <button
                        onClick={() => setResetTarget(u.username)}
                        disabled={isSelf(u)}
                        className="text-xs px-3 py-1 border border-sentinel-700 text-gray-400 rounded hover:bg-sentinel-700/50 transition disabled:opacity-30 disabled:cursor-not-allowed"
                      >
                        Reset Password
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      </ErrorBoundary>
    </AppShell>
  )
}
