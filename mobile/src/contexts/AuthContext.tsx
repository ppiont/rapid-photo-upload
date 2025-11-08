import React, { createContext, useState, useContext, useEffect, ReactNode } from 'react'
import { authService } from '../services/authService'
import type { User, LoginRequest, RegisterRequest } from '../types'

interface AuthContextType {
  user: User | null
  loading: boolean
  login: (credentials: LoginRequest) => Promise<void>
  register: (data: RegisterRequest) => Promise<void>
  logout: () => Promise<void>
}

const AuthContext = createContext<AuthContextType | undefined>(undefined)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    // Check for stored auth on mount
    checkAuth()
  }, [])

  const checkAuth = async () => {
    try {
      const storedAuth = await authService.getStoredAuth()
      if (storedAuth) {
        setUser({
          userId: storedAuth.userId,
          email: storedAuth.email,
        })
      }
    } catch (error) {
      console.error('Failed to check auth:', error)
    } finally {
      setLoading(false)
    }
  }

  const login = async (credentials: LoginRequest) => {
    const response = await authService.login(credentials)
    setUser({
      userId: response.userId,
      email: response.email,
    })
  }

  const register = async (data: RegisterRequest) => {
    const response = await authService.register(data)
    setUser({
      userId: response.userId,
      email: response.email,
    })
  }

  const logout = async () => {
    await authService.logout()
    setUser(null)
  }

  return (
    <AuthContext.Provider value={{ user, loading, login, register, logout }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (context === undefined) {
    throw new Error('useAuth must be used within an AuthProvider')
  }
  return context
}
