'use client'

import { useState } from 'react'
import Link from 'next/link'

export default function ForgotPasswordPage() {
  const [username, setUsername] = useState('')
  const [submitted, setSubmitted] = useState(false)

  const handleSubmit = (e) => {
    e.preventDefault()
    setSubmitted(true)
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-sentinel-900">
      <div className="card w-full max-w-sm space-y-6">
        <div className="text-center">
          <div className="text-4xl mb-2">⚡</div>
          <h1 className="text-2xl font-bold text-sentinel-accent">Sentinel IoT</h1>
          <p className="text-gray-400 text-sm mt-1">Reset Password</p>
        </div>

        {!submitted ? (
          <form onSubmit={handleSubmit} className="space-y-4">
            <p className="text-sm text-gray-400">
              Enter your username and your administrator will be notified to reset your password.
            </p>
            <div>
              <label className="text-sm text-gray-400 mb-1 block">Username</label>
              <input
                type="text"
                value={username}
                onChange={e => setUsername(e.target.value)}
                className="w-full bg-sentinel-700 border border-sentinel-600 rounded-lg px-3 py-2 text-white focus:outline-none focus:border-sentinel-accent"
                placeholder="your-username"
                autoComplete="username"
                required
              />
            </div>
            <button
              type="submit"
              className="w-full bg-sentinel-accent text-sentinel-900 font-semibold py-2 rounded-lg hover:bg-cyan-300 transition"
            >
              Request Reset
            </button>
          </form>
        ) : (
          <div className="space-y-4">
            <div role="status" className="flex items-start gap-2 px-3 py-3 rounded-lg bg-sentinel-success/15 border border-sentinel-success/40 text-sentinel-success text-sm">
              <span className="flex-shrink-0 mt-0.5">✓</span>
              <span>
                Contact your administrator and provide your username{' '}
                <span className="font-semibold text-white">"{username}"</span>{' '}
                to have your password reset.
              </span>
            </div>
            <p className="text-xs text-gray-500 text-center">
              This platform does not support self-service password reset.
            </p>
          </div>
        )}

        <Link href="/login" className="block text-center text-xs text-sentinel-accent hover:underline">
          ← Back to Sign In
        </Link>
      </div>
    </div>
  )
}
