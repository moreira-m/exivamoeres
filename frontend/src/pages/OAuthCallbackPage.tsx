import { useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { authService } from '../services/authService'
import { useAuthStore } from '../store/authStore'

/**
 * Destino do redirect pós-login social. O backend devolve os tokens no
 * FRAGMENTO da URL (#access_token=...&refresh_token=...) — que nunca chega ao
 * servidor. Trocamos o refresh token por uma sessão completa (para obter o
 * usuário) e limpamos a URL.
 */
export function OAuthCallbackPage() {
  const navigate = useNavigate()
  const setSession = useAuthStore((s) => s.setSession)

  useEffect(() => {
    const params = new URLSearchParams(window.location.hash.slice(1))
    const refreshToken = params.get('refresh_token')
    if (!refreshToken) {
      navigate('/login', { replace: true })
      return
    }
    authService
      .refresh(refreshToken)
      .then((auth) => {
        setSession(auth.accessToken, auth.refreshToken, auth.user)
        navigate('/account/teams', { replace: true })
      })
      .catch(() => navigate('/login', { replace: true }))
  }, [navigate, setSession])

  return (
    <div className="flex min-h-screen items-center justify-center font-black uppercase text-white">
      Finalizando login…
    </div>
  )
}
