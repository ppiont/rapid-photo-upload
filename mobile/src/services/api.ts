import axios, { type AxiosInstance } from 'axios'
import AsyncStorage from '@react-native-async-storage/async-storage'

// Update this with your backend URL
const API_BASE_URL = 'http://rpu-pp-demo-alb-1241907848.us-west-1.elb.amazonaws.com'

export const apiClient: AxiosInstance = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
  timeout: 30000,
})

// Request interceptor to add JWT token
apiClient.interceptors.request.use(
  async (config) => {
    const token = await AsyncStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

// Response interceptor to handle errors
apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    if (error.response?.status === 401) {
      // Unauthorized - clear token
      await AsyncStorage.multiRemove(['token', 'userId', 'email'])
    }
    return Promise.reject(error)
  }
)
