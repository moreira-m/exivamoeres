import { useMutation } from '@tanstack/react-query'
import { authService } from '../services/authService'
import { useAuthStore } from '../store/authStore'
import type { AuthResponse } from '../types/api'

/** Ações de autenticação que já persistem a sessão no store. */
export function useAuth() {
  const setSession = useAuthStore((s) => s.setSession)
  const clearSession = useAuthStore((s) => s.clearSession)
  const user = useAuthStore((s) => s.user)

  const persist = (auth: AuthResponse) => {
    setSession(auth.accessToken, auth.refreshToken, auth.user)
    return auth
  }

  const login = useMutation({
    mutationFn: (v: { email: string; password: string }) =>
      authService.login(v.email, v.password).then(persist),
  })

  const register = useMutation({
    mutationFn: (v: { email: string; password: string; displayName: string }) =>
      authService.register(v.email, v.password, v.displayName).then(persist),
  })

  const loginAnonymous = useMutation({
    mutationFn: (displayName?: string) => authService.loginAnonymous(displayName).then(persist),
  })

  const logout = () => {
    const refreshToken = useAuthStore.getState().refreshToken
    if (refreshToken) {
      void authService.logout(refreshToken).catch(() => undefined)
    }
    clearSession()
  }

  return { user, login, register, loginAnonymous, logout, isAuthenticated: !!user }
}
