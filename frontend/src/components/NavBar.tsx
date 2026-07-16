import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useAuth } from '../hooks/useAuth'
import { Button } from './ui/Button'
import { LanguageToggle } from './ui/LanguageToggle'
import { ThemeToggle } from './ui/ThemeToggle'
import { NotificationBell } from './NotificationBell'

export function NavBar() {
  const { t } = useTranslation()
  const { user, isAuthenticated, logout } = useAuth()
  const navigate = useNavigate()
  const [mobileOpen, setMobileOpen] = useState(false)
  const [accountOpen, setAccountOpen] = useState(false)
  const accountRef = useRef<HTMLDivElement>(null)

  // Fecha o dropdown de conta ao clicar fora.
  useEffect(() => {
    function onClickOutside(e: MouseEvent) {
      if (accountRef.current && !accountRef.current.contains(e.target as Node)) {
        setAccountOpen(false)
      }
    }
    document.addEventListener('mousedown', onClickOutside)
    return () => document.removeEventListener('mousedown', onClickOutside)
  }, [])

  const closeAll = () => {
    setMobileOpen(false)
    setAccountOpen(false)
  }

  const handleLogout = () => {
    logout()
    closeAll()
    navigate('/')
  }

  // Itens de conta (usados no dropdown do desktop e no menu mobile).
  const accountLinks = (
    <>
      <NavItem to="/account/teams" onClick={closeAll}>
        {t('nav.myTeams')}
      </NavItem>
      <NavItem to="/account/characters" onClick={closeAll}>
        {t('nav.characters')}
      </NavItem>
      <NavItem to="/account/notifications" onClick={closeAll}>
        {t('nav.notifications')}
      </NavItem>
      <NavItem to="/account/billing" onClick={closeAll}>
        {t('nav.billing')}
        {user?.plan === 'PREMIUM' && <span className="text-accent"> ★</span>}
      </NavItem>
    </>
  )

  return (
    <header className="border-b-[3px] border-ink bg-surface">
      <nav className="mx-auto flex max-w-6xl items-center gap-3 px-4 py-3">
        <Link to="/" className="text-2xl font-black uppercase tracking-tight text-ink" onClick={closeAll}>
          Tibia<span className="text-accent">Pit</span>
        </Link>
        <span className="hidden text-sm font-bold uppercase text-ink/60 lg:inline">
        </span>

        {/* Desktop */}
        <div className="ml-auto hidden items-center gap-2 md:flex">
          {isAuthenticated ? (
            <>
              <NotificationBell onClick={closeAll} />
              <Link to="/account/teams/new" onClick={closeAll}>
                <Button variant="accent">{t('nav.createTeam')}</Button>
              </Link>
              <div className="relative" ref={accountRef}>
                <button
                  type="button"
                  onClick={() => setAccountOpen((o) => !o)}
                  aria-expanded={accountOpen}
                  className="flex items-center gap-1 border-[3px] border-ink bg-surface px-4 py-2.5 font-extrabold uppercase tracking-wide text-ink shadow-retro-sm"
                >
                  {user?.displayName}
                  {user?.plan === 'PREMIUM' && <span className="text-accent">★</span>}
                  <span aria-hidden className="text-ink/60">
                    ▾
                  </span>
                </button>
                {accountOpen && (
                  <div className="absolute right-0 z-30 mt-1 w-56 border-[3px] border-ink bg-surface shadow-retro">
                    <div className="flex flex-col py-1">{accountLinks}</div>
                    <div className="flex items-center justify-between gap-2 border-t-2 border-dashed border-ink/30 px-3 py-2">
                      <ThemeToggle />
                      <LanguageToggle />
                    </div>
                    <div className="border-t-2 border-dashed border-ink/30">
                      <NavItem as="button" onClick={handleLogout}>
                        {t('nav.logout')}
                      </NavItem>
                    </div>
                  </div>
                )}
              </div>
            </>
          ) : (
            // items-stretch faz os toggles terem a mesma altura do botão Entrar.
            <div className="flex items-stretch gap-2">
              <ThemeToggle />
              <LanguageToggle />
              <Link to="/login" onClick={closeAll} className="flex">
                <Button variant="accent">{t('nav.login')}</Button>
              </Link>
            </div>
          )}
        </div>

        {/* Mobile: só o botão hambúrguer */}
        <button
          type="button"
          className="ml-auto border-[3px] border-ink bg-surface px-3 py-1.5 text-xl leading-none text-ink md:hidden"
          aria-label={t('nav.menu')}
          aria-expanded={mobileOpen}
          onClick={() => setMobileOpen((o) => !o)}
        >
          {mobileOpen ? '✕' : '☰'}
        </button>
      </nav>

      {/* Menu mobile expandido */}
      {mobileOpen && (
        <div className="border-t-[3px] border-ink bg-surface px-4 py-4 md:hidden">
          <div className="flex flex-col gap-2">
            {isAuthenticated ? (
              <>
                <Link to="/account/teams/new" onClick={closeAll}>
                  <Button variant="accent" className="w-full">
                    {t('nav.createTeam')}
                  </Button>
                </Link>
                <Link to="/account/teams" onClick={closeAll}>
                  <Button variant="neutral" className="w-full">
                    {t('nav.myTeams')}
                  </Button>
                </Link>
                <Link to="/account/characters" onClick={closeAll}>
                  <Button variant="neutral" className="w-full">
                    {t('nav.characters')}
                  </Button>
                </Link>
                <Link to="/account/notifications" onClick={closeAll}>
                  <Button variant="neutral" className="w-full">
                    {t('nav.notifications')}
                  </Button>
                </Link>
                <Link to="/account/billing" onClick={closeAll}>
                  <Button variant="neutral" className="w-full">
                    {t('nav.billing')}
                    {user?.plan === 'PREMIUM' && <span className="text-accent"> ★</span>}
                  </Button>
                </Link>
                <Button variant="neutral" className="w-full" onClick={handleLogout}>
                  {t('nav.logout')}
                </Button>
              </>
            ) : (
              <Link to="/login" onClick={closeAll}>
                <Button variant="accent" className="w-full">
                  {t('nav.login')}
                </Button>
              </Link>
            )}
          </div>
          <div className="mt-4 flex items-center justify-between border-t-2 border-dashed border-ink/30 pt-4">
            <div className="flex items-center gap-2">
              <span className="text-sm font-extrabold uppercase text-ink">{t('nav.theme')}</span>
              <ThemeToggle />
            </div>
            <div className="flex items-center gap-2">
              <span className="text-sm font-extrabold uppercase text-ink">{t('nav.language')}</span>
              <LanguageToggle />
            </div>
          </div>
        </div>
      )}
    </header>
  )
}

/** Item de lista do dropdown de conta (link ou botão), com hover destacado. */
function NavItem({
  to,
  as,
  onClick,
  children,
}: {
  to?: string
  as?: 'button'
  onClick?: () => void
  children: React.ReactNode
}) {
  const className =
    'block w-full px-4 py-2 text-left font-extrabold uppercase text-ink hover:bg-primary hover:text-white'
  if (as === 'button' || !to) {
    return (
      <button type="button" onClick={onClick} className={className}>
        {children}
      </button>
    )
  }
  return (
    <Link to={to} onClick={onClick} className={className}>
      {children}
    </Link>
  )
}
