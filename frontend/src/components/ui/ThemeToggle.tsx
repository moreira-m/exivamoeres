import { useTranslation } from 'react-i18next'
import { useThemeStore } from '../../store/themeStore'

/** Alterna claro/escuro. Mostra o ícone do tema para o qual vai trocar. */
export function ThemeToggle({ className = '' }: { className?: string }) {
  const { t } = useTranslation()
  const theme = useThemeStore((s) => s.theme)
  const toggle = useThemeStore((s) => s.toggle)

  return (
    <button
      type="button"
      onClick={toggle}
      // O nome acessível diz **a ação**, não o estado: o botão muda de propósito a
      // cada clique, e "Tema" não dizia para onde ele vai. As duas chaves existiam
      // desde sempre e não eram usadas por ninguém (achadas pelo T13).
      aria-label={t(theme === 'dark' ? 'nav.themeLight' : 'nav.themeDark')}
      className={`flex items-center justify-center border-[3px] border-ink bg-surface px-3 py-1 text-base leading-none text-ink ${className}`}
    >
      {theme === 'dark' ? '☀️' : '🌙'}
    </button>
  )
}
