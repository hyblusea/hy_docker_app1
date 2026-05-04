import { createContext, useContext, useState, useCallback, useEffect, type ReactNode } from 'react'
import { getMe, login as apiLogin, logout as apiLogout, type AuthUser } from '../api/auth'
import { UnauthorizedError, setOnUnauthorized } from '../api/client'
import { clearStrategiesCache, clearValidStrategiesCache } from '../hooks/useStrategies'

interface AuthContextType {
  user: AuthUser | null
  loading: boolean
  login: (username: string, password: string) => Promise<void>
  logout: () => Promise<void>
  refreshUser: () => Promise<void>
  isRoot: boolean
}

const AuthContext = createContext<AuthContextType | null>(null)

export const AuthProvider = ({ children }: { children: ReactNode }) => {
  const [user, setUser] = useState<AuthUser | null>(null)
  const [loading, setLoading] = useState(true)

  const refreshUser = useCallback(async (signal?: AbortSignal) => {
    try {
      const u = await getMe(signal)
      setUser(u)
    } catch (e) {
      if (signal?.aborted) return
      if (e instanceof UnauthorizedError) {
        setUser(null)
      }
    } finally {
      if (!signal?.aborted) setLoading(false)
    }
  }, [])

  useEffect(() => {
    setOnUnauthorized(() => {
      clearStrategiesCache()
      clearValidStrategiesCache()
      setUser(null)
    })
  }, [])

  useEffect(() => {
    const ac = new AbortController()
    refreshUser(ac.signal)
    return () => ac.abort()
  }, [refreshUser])

  const login = useCallback(async (username: string, password: string) => {
    const u = await apiLogin(username, password)
    setUser(u)
  }, [])

  const logout = useCallback(async () => {
    await apiLogout()
    setUser(null)
  }, [])

  const isRoot = user?.role === 'root'

  return (
    <AuthContext.Provider value={{ user, loading, login, logout, refreshUser, isRoot }}>
      {children}
    </AuthContext.Provider>
  )
}

export const useAuth = () => {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within AuthProvider')
  return ctx
}
