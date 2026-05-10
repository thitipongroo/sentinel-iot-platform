import { useState, useEffect, createContext, useContext } from 'react'
import { authApi } from '../api/client'

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    const token = localStorage.getItem('sentinel_token')
    const stored = localStorage.getItem('sentinel_user')
    if (token && stored) {
      setUser(JSON.parse(stored))
    }
    setLoading(false)
  }, [])

  const login = async (username, password) => {
    const { data } = await authApi.login(username, password)
    localStorage.setItem('sentinel_token', data.token)
    localStorage.setItem('sentinel_user', JSON.stringify({ username: data.username, role: data.role }))
    setUser({ username: data.username, role: data.role })
    return data
  }

  const logout = () => {
    localStorage.removeItem('sentinel_token')
    localStorage.removeItem('sentinel_user')
    setUser(null)
  }

  return (
    <AuthContext.Provider value={{ user, login, logout, loading }}>
      {children}
    </AuthContext.Provider>
  )
}

export const useAuth = () => useContext(AuthContext)
