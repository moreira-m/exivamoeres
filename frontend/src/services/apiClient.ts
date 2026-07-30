import axios, { AxiosError, type InternalAxiosRequestConfig } from 'axios'
import { useAuthStore } from '../store/authStore'
import { lembrarRequestId } from './requestId'

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

// Controla o refresh concorrente: se várias chamadas caem em 401 ao mesmo
// tempo, todas aguardam um único refresh em andamento.
let refreshPromise: Promise<string> | null = null

async function refreshAccessToken(): Promise<string> {
  const { refreshToken, setSession, clearSession } = useAuthStore.getState()
  if (!refreshToken) {
    clearSession()
    throw new Error('Sem refresh token')
  }
  // Chamada crua (sem interceptors) para não recursar no 401.
  const { data } = await axios.post(`${import.meta.env.VITE_API_URL}/api/auth/refresh`, {
    refreshToken,
  })
  setSession(data.accessToken, data.refreshToken, data.user)
  return data.accessToken as string
}

apiClient.interceptors.response.use(
  (response) => {
    // O id de correlação de **toda** resposta, para a tela de erro de render poder
    // mostrar o último que passou (o boundary não tem erro HTTP em mãos).
    lembrarRequestId(response.headers['x-request-id'])
    return response
  },
  async (error: AxiosError) => {
    lembrarRequestId(error.response?.headers?.['x-request-id'])
    const original = error.config as InternalAxiosRequestConfig & { _retried?: boolean }
    const isAuthCall = original?.url?.includes('/api/auth/')

    if (error.response?.status === 401 && original && !original._retried && !isAuthCall) {
      original._retried = true
      try {
        refreshPromise = refreshPromise ?? refreshAccessToken()
        const newToken = await refreshPromise
        refreshPromise = null
        original.headers.Authorization = `Bearer ${newToken}`
        return apiClient(original)
      } catch (refreshError) {
        refreshPromise = null
        useAuthStore.getState().clearSession()
        return Promise.reject(refreshError)
      }
    }
    return Promise.reject(error)
  },
)
