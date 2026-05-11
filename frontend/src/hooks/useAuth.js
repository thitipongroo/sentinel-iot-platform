'use client'

import { useState, useEffect, createContext, useContext } from 'react'
import { authApi } from '@/api/client'
import { setAccessToken, clearAccessToken } from '@/lib/tokenStore'

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    // Attempt silent re-authentication on page load using the HttpOnly refresh
    // token cookie (set by the backend on login). No localStorage involved.
    const tryRefresh = async () => {
      try {
        const { data } = await authApi.refresh()
        setAccessToken(data.accessToken)
        setUser({ username: data.username, role: data.role })
      } catch {
        // Not logged in or refresh token expired — stay logged out
      } finally {
        setLoading(false)
      }
    }
    tryRefresh()
  }, [])

  const login = async (username, password) => {
    const { data } = await authApi.login(username, password)
    // Access token lives only in memory; refresh token arrives as HttpOnly cookie
    setAccessToken(data.accessToken)
    setUser({ username: data.username, role: data.role })
    return data
  }

  const logout = async () => {
    try {
      await authApi.logout()
    } catch { /* best-effort */ } finally {
      clearAccessToken()
      setUser(null)
    }
  }

  return (
    <AuthContext.Provider value={{ user, login, logout, loading }}>
      {children}
    </AuthContext.Provider>
  )
}

export const useAuth = () => useContext(AuthContext)
