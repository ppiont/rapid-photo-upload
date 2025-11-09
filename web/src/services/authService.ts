import { apiClient } from './api'
import type { LoginRequest, RegisterRequest, AuthResponse } from '@/types'

export const authService = {
  async login(request: LoginRequest): Promise<AuthResponse> {
    const response = await apiClient.post<AuthResponse>('/auth/login', request)
    return response.data
  },

  async register(request: RegisterRequest): Promise<AuthResponse> {
    const response = await apiClient.post<AuthResponse>('/auth/register', {
      email: request.email,
      password: request.password,
      fullName: request.fullName,
    })
    return response.data
  },

  logout(): void {
    localStorage.removeItem('token')
    localStorage.removeItem('userId')
    localStorage.removeItem('email')
  },

  saveAuthData(authResponse: AuthResponse): void {
    localStorage.setItem('token', authResponse.token)
    localStorage.setItem('userId', authResponse.userId)
    localStorage.setItem('email', authResponse.email)
  },

  getAuthData(): { token: string | null; userId: string | null; email: string | null } {
    return {
      token: localStorage.getItem('token'),
      userId: localStorage.getItem('userId'),
      email: localStorage.getItem('email'),
    }
  },

  isAuthenticated(): boolean {
    return !!localStorage.getItem('token')
  },
}
