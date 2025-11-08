import AsyncStorage from '@react-native-async-storage/async-storage'
import { apiClient } from './api'
import type { AuthResponse, LoginRequest, RegisterRequest } from '../types'

export const authService = {
  async login(credentials: LoginRequest): Promise<AuthResponse> {
    console.log('[AuthService] Login attempt:', { email: credentials.email, passwordLength: credentials.password.length })
    const response = await apiClient.post<AuthResponse>('/api/auth/login', credentials)
    console.log('[AuthService] Login successful:', { userId: response.data.userId, email: response.data.email })
    const { token, userId, email } = response.data

    // Store auth data in AsyncStorage
    await AsyncStorage.multiSet([
      ['token', token],
      ['userId', userId],
      ['email', email],
    ])

    return response.data
  },

  async register(data: RegisterRequest): Promise<AuthResponse> {
    const response = await apiClient.post<AuthResponse>('/api/auth/register', data)
    const { token, userId, email } = response.data

    // Store auth data in AsyncStorage
    await AsyncStorage.multiSet([
      ['token', token],
      ['userId', userId],
      ['email', email],
    ])

    return response.data
  },

  async logout(): Promise<void> {
    await AsyncStorage.multiRemove(['token', 'userId', 'email'])
  },

  async getStoredAuth(): Promise<{ token: string; userId: string; email: string } | null> {
    const keys = await AsyncStorage.multiGet(['token', 'userId', 'email'])
    const [token, userId, email] = keys.map(([, value]) => value)

    if (token && userId && email) {
      return { token, userId, email }
    }

    return null
  },
}
