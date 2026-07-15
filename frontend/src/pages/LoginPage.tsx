import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Layout } from '../components/Layout'
import { Card } from '../components/ui/Card'
import { Button } from '../components/ui/Button'
import { Input } from '../components/ui/Input'
import { useAuth } from '../hooks/useAuth'
import { getApiErrorMessage } from '../lib/apiError'

type Mode = 'login' | 'register'

const API_URL = import.meta.env.VITE_API_URL as string

export function LoginPage() {
  const [mode, setMode] = useState<Mode>('login')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [error, setError] = useState('')
  const { login, register, loginAnonymous } = useAuth()
  const navigate = useNavigate()

  const busy = login.isPending || register.isPending || loginAnonymous.isPending

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError('')
    try {
      if (mode === 'login') {
        await login.mutateAsync({ email, password })
      } else {
        await register.mutateAsync({ email, password, displayName })
      }
      navigate('/account/teams')
    } catch (err) {
      setError(getApiErrorMessage(err))
    }
  }

  const enterAnonymous = async () => {
    setError('')
    try {
      await loginAnonymous.mutateAsync(undefined)
      navigate('/account/teams')
    } catch (err) {
      setError(getApiErrorMessage(err))
    }
  }

  return (
    <Layout>
      <div className="mx-auto max-w-md">
        <Card className="p-6">
          <div className="mb-5 flex gap-2">
            <Button
              variant={mode === 'login' ? 'primary' : 'neutral'}
              onClick={() => setMode('login')}
              type="button"
            >
              Entrar
            </Button>
            <Button
              variant={mode === 'register' ? 'primary' : 'neutral'}
              onClick={() => setMode('register')}
              type="button"
            >
              Criar conta
            </Button>
          </div>

          <form onSubmit={submit} className="space-y-4">
            {mode === 'register' && (
              <div className="[&_span]:text-ink">
                <Input
                  label="Nome de exibição"
                  value={displayName}
                  onChange={(e) => setDisplayName(e.target.value)}
                  required
                />
              </div>
            )}
            <div className="[&_span]:text-ink">
              <Input
                label="Email"
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                required
              />
            </div>
            <div className="[&_span]:text-ink">
              <Input
                label="Senha"
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
              />
            </div>

            {error && <p className="font-bold text-accent">{error}</p>}

            <Button type="submit" disabled={busy} className="w-full">
              {mode === 'login' ? 'Entrar' : 'Criar conta'}
            </Button>
          </form>

          <div className="my-5 border-t-2 border-dashed border-ink/30" />

          <div className="space-y-2">
            <a href={`${API_URL}/oauth2/authorization/google`} className="block">
              <Button variant="neutral" className="w-full" type="button">
                Continuar com Google
              </Button>
            </a>
            <a href={`${API_URL}/oauth2/authorization/discord`} className="block">
              <Button variant="neutral" className="w-full" type="button">
                Continuar com Discord
              </Button>
            </a>
            <Button
              variant="neutral"
              className="w-full"
              type="button"
              onClick={enterAnonymous}
              disabled={busy}
            >
              Entrar como anônimo
            </Button>
          </div>
        </Card>
      </div>
    </Layout>
  )
}
