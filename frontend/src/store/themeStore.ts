import { create } from 'zustand'
import { persist } from 'zustand/middleware'

export type Theme = 'light' | 'dark'

interface ThemeState {
  theme: Theme
  toggle: () => void
  setTheme: (theme: Theme) => void
}

/** Aplica/remove a classe `dark` no <html> (Tailwind darkMode: 'class'). */
function applyTheme(theme: Theme) {
  document.documentElement.classList.toggle('dark', theme === 'dark')
}

export const useThemeStore = create<ThemeState>()(
  persist(
    (set) => ({
      // Default claro; a preferência salva sobrescreve na hidratação.
      theme: 'light',
      toggle: () =>
        set((s) => {
          const next: Theme = s.theme === 'dark' ? 'light' : 'dark'
          applyTheme(next)
          return { theme: next }
        }),
      setTheme: (theme) => {
        applyTheme(theme)
        set({ theme })
      },
    }),
    {
      name: 'exivamoeres-theme',
      onRehydrateStorage: () => (state) => {
        // Reaplica a classe assim que a preferência é lida do localStorage.
        if (state) applyTheme(state.theme)
      },
    },
  ),
)
