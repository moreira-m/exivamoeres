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
      aria-label={t('nav.theme')}
      className={`flex items-center justify-center border-[3px] border-ink bg-surface px-3 py-1 text-base leading-none text-ink ${className}`}
    >
      {theme === 'dark' ? '☀️' : '🌙'}
    </button>
  )
}
