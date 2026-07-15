import React from 'react'
import ReactDOM from 'react-dom/client'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { App } from './App'
import './i18n'
import { useThemeStore } from './store/themeStore'
import './styles/index.css'

// Aplica o tema salvo antes do primeiro render (evita flash de tema errado).
document.documentElement.classList.toggle('dark', useThemeStore.getState().theme === 'dark')

const queryClient = new QueryClient()

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <QueryClientProvider client={queryClient}>
      <App />
    </QueryClientProvider>
  </React.StrictMode>,
)
