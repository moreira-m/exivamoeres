import { apiClient } from './apiClient'
import type { AuthResponse } from '../types/api'

export const authService = {
  register: (email: string, password: string, displayName: string) =>
    apiClient
      .post<AuthResponse>('/api/auth/register', { email, password, displayName })
      .then((r) => r.data),

  login: (email: string, password: string) =>
    apiClient.post<AuthResponse>('/api/auth/login', { email, password }).then((r) => r.data),

  loginAnonymous: (displayName?: string) =>
    apiClient.post<AuthResponse>('/api/auth/anonymous', { displayName }).then((r) => r.data),

  refresh: (refreshToken: string) =>
    apiClient.post<AuthResponse>('/api/auth/refresh', { refreshToken }).then((r) => r.data),

  logout: (refreshToken: string) =>
    apiClient.post<void>('/api/auth/logout', { refreshToken }).then(() => undefined),
}
