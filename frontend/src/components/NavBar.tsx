import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../hooks/useAuth'
import { Button } from './ui/Button'

export function NavBar() {
  const { user, isAuthenticated, logout } = useAuth()
  const navigate = useNavigate()

  const handleLogout = () => {
    logout()
    navigate('/')
  }

  return (
    <header className="border-b-[3px] border-ink bg-white">
      <nav className="mx-auto flex max-w-6xl flex-wrap items-center gap-3 px-4 py-3">
        <Link to="/" className="text-2xl font-black uppercase tracking-tight text-ink">
          Exiva<span className="text-accent">moeres</span>
        </Link>
        <span className="hidden text-sm font-bold uppercase text-ink/60 sm:inline">
          times de soul core
        </span>
        <div className="ml-auto flex items-center gap-2">
          {isAuthenticated ? (
            <>
              <Link to="/account/teams">
                <Button variant="neutral">Meus times</Button>
              </Link>
              <Link to="/account/characters">
                <Button variant="neutral">Personagens</Button>
              </Link>
              <Link to="/account/teams/new">
                <Button variant="accent">Criar time</Button>
              </Link>
              <span className="hidden font-bold text-ink md:inline">{user?.displayName}</span>
              <Button variant="neutral" onClick={handleLogout}>
                Sair
              </Button>
            </>
          ) : (
            <Link to="/login">
              <Button variant="accent">Entrar</Button>
            </Link>
          )}
        </div>
      </nav>
    </header>
  )
}
