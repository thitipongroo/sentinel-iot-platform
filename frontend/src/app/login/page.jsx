'use client'

import { useState } from 'react'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { useAuth } from '@/hooks/useAuth'

export default function LoginPage() {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError]       = useState('')
  const [loading, setLoading]   = useState(false)
  const { login } = useAuth()
  const router    = useRouter()

  const handleSubmit = async (e) => {
    e.preventDefault()
    setError('')
    setLoading(true)
    try {
      await login(username, password)
      router.push('/dashboard')
    } catch {
      setError('Invalid username or password. Please try again.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-sentinel-900">
      <div className="card w-full max-w-sm space-y-6">
        <div className="text-center">
          <div className="text-4xl mb-2">⚡</div>
          <h1 className="text-2xl font-bold text-sentinel-accent">Sentinel IoT</h1>
          <p className="text-gray-400 text-sm mt-1">Industrial Monitoring Platform</p>
        </div>

        {error && (
          <div role="alert" className="flex items-start gap-2 px-3 py-2.5 rounded-lg bg-sentinel-danger/15 border border-sentinel-danger/40 text-sentinel-danger text-sm">
            <span className="flex-shrink-0 mt-0.5">✕</span>
            <span>{error}</span>
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="text-sm text-gray-400 mb-1 block">Username</label>
            <input
              type="text"
              value={username}
              onChange={e => { setUsername(e.target.value); setError('') }}
              className={`w-full bg-sentinel-700 border rounded-lg px-3 py-2 text-white focus:outline-none focus:border-sentinel-accent ${error ? 'border-sentinel-danger' : 'border-sentinel-600'}`}
              placeholder="admin"
              autoComplete="username"
              required
            />
          </div>
          <div>
            <div className="flex items-center justify-between mb-1">
              <label className="text-sm text-gray-400">Password</label>
              <Link href="/forgot-password" className="text-xs text-sentinel-accent hover:underline">
                Forgot password?
              </Link>
            </div>
            <input
              type="password"
              value={password}
              onChange={e => { setPassword(e.target.value); setError('') }}
              className={`w-full bg-sentinel-700 border rounded-lg px-3 py-2 text-white focus:outline-none focus:border-sentinel-accent ${error ? 'border-sentinel-danger' : 'border-sentinel-600'}`}
              placeholder="••••••••"
              autoComplete="current-password"
              required
            />
          </div>
          <button
            type="submit"
            disabled={loading}
            className="w-full bg-sentinel-accent text-sentinel-900 font-semibold py-2 rounded-lg hover:bg-cyan-300 transition disabled:opacity-50"
          >
            {loading ? 'Signing in…' : 'Sign In'}
          </button>
        </form>
      </div>
    </div>
  )
}
