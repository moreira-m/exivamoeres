import { useTranslation } from 'react-i18next'
import { changeLanguage, type Language } from '../../i18n'

const OPTIONS: Language[] = ['pt', 'en']

/** Alterna entre PT e EN. Segmentado, estilo "retro". */
export function LanguageToggle({ className = '' }: { className?: string }) {
  const { i18n } = useTranslation()
  const current = i18n.language.startsWith('pt') ? 'pt' : 'en'

  return (
    <div className={`inline-flex border-[3px] border-ink ${className}`}>
      {OPTIONS.map((lang) => (
        <button
          key={lang}
          type="button"
          onClick={() => changeLanguage(lang)}
          className={`flex items-center justify-center px-2.5 py-1 text-xs font-extrabold uppercase
            ${current === lang ? 'bg-primary text-white' : 'bg-surface text-ink'}`}
        >
          {lang}
        </button>
      ))}
    </div>
  )
}
