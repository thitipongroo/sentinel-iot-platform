import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../hooks/useAuth'

export default function LoginPage() {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  const { login } = useAuth()
  const navigate = useNavigate()

  const handleSubmit = async (e) => {
    e.preventDefault()
    setError('')
    setLoading(true)
    try {
      await login(username, password)
      navigate('/')
    } catch {
      setError('Invalid username or password')
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

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="text-sm text-gray-400 mb-1 block">Username</label>
            <input
              type="text"
              value={username}
              onChange={e => setUsername(e.target.value)}
              className="w-full bg-sentinel-700 border border-sentinel-600 rounded-lg px-3 py-2 text-white focus:outline-none focus:border-sentinel-accent"
              placeholder="admin"
              required
            />
          </div>
          <div>
            <label className="text-sm text-gray-400 mb-1 block">Password</label>
            <input
              type="password"
              value={password}
              onChange={e => setPassword(e.target.value)}
              className="w-full bg-sentinel-700 border border-sentinel-600 rounded-lg px-3 py-2 text-white focus:outline-none focus:border-sentinel-accent"
              placeholder="••••••••"
              required
            />
          </div>
          {error && <p className="text-sentinel-danger text-sm">{error}</p>}
          <button
            type="submit"
            disabled={loading}
            className="w-full bg-sentinel-accent text-sentinel-900 font-semibold py-2 rounded-lg hover:bg-cyan-300 transition disabled:opacity-50"
          >
            {loading ? 'Signing in…' : 'Sign In'}
          </button>
        </form>
        <p className="text-xs text-gray-500 text-center">Default: admin / admin123</p>
      </div>
    </div>
  )
}
