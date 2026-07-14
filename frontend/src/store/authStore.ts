import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import type { UserResponse } from '../types/api'

interface AuthState {
  accessToken: string | null
  refreshToken: string | null
  user: UserResponse | null
  setSession: (accessToken: string, refreshToken: string, user: UserResponse) => void
  clearSession: () => void
}

// Persistido em localStorage para sobreviver a refresh da página.
export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      accessToken: null,
      refreshToken: null,
      user: null,
      setSession: (accessToken, refreshToken, user) =>
        set({ accessToken, refreshToken, user }),
      clearSession: () =>
        set({ accessToken: null, refreshToken: null, user: null }),
    }),
    { name: 'exivamoeres-auth' },
  ),
)
