import { createContext, useContext, useState, useEffect, type ReactNode } from 'react'
import type { User, LoginRequest, RegisterRequest } from '@/types'
import { authService } from '@/services/authService'

interface AuthContextType {
  user: User | null
  isAuthenticated: boolean
  isLoading: boolean
  login: (request: LoginRequest) => Promise<void>
  register: (request: RegisterRequest) => Promise<void>
  logout: () => void
}

const AuthContext = createContext<AuthContextType | undefined>(undefined)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null)
  const [isLoading, setIsLoading] = useState(true)

  useEffect(() => {
    // Check if user is already authenticated on mount
    const authData = authService.getAuthData()
    if (authData.token && authData.userId && authData.email) {
      setUser({
        userId: authData.userId,
        email: authData.email,
      })
    }
    setIsLoading(false)
  }, [])

  const login = async (request: LoginRequest) => {
    const response = await authService.login(request)
    authService.saveAuthData(response)
    setUser({
      userId: response.userId,
      email: response.email,
    })
  }

  const register = async (request: RegisterRequest) => {
    const response = await authService.register(request)
    authService.saveAuthData(response)
    setUser({
      userId: response.userId,
      email: response.email,
    })
  }

  const logout = () => {
    authService.logout()
    setUser(null)
  }

  return (
    <AuthContext.Provider
      value={{
        user,
        isAuthenticated: !!user,
        isLoading,
        login,
        register,
        logout,
      }}
    >
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
