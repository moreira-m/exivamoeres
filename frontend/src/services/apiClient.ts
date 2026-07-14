import axios from 'axios'
import { useAuthStore } from '../store/authStore'

// Client HTTP centralizado: toda chamada à API passa por aqui.
export const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_URL,
})

apiClient.interceptors.request.use((config) => {
  const token = useAuthStore.getState().accessToken
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    // TODO(sessão 2): em 401, tentar POST /api/auth/refresh com o
    // refreshToken do store antes de derrubar a sessão.
    if (error.response?.status === 401) {
      useAuthStore.getState().clearSession()
    }
    return Promise.reject(error)
  },
)
