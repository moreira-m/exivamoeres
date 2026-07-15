import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import pt from './locales/pt.json'
import en from './locales/en.json'

export const SUPPORTED_LANGUAGES = ['pt', 'en'] as const
export type Language = (typeof SUPPORTED_LANGUAGES)[number]

const STORAGE_KEY = 'exivamoeres-lang'

function initialLanguage(): Language {
  const saved = localStorage.getItem(STORAGE_KEY)
  if (saved === 'pt' || saved === 'en') return saved
  // Cai no idioma do navegador quando começa com "pt"; senão, inglês.
  return navigator.language.toLowerCase().startsWith('pt') ? 'pt' : 'en'
}

i18n.use(initReactI18next).init({
  resources: {
    pt: { translation: pt },
    en: { translation: en },
  },
  lng: initialLanguage(),
  fallbackLng: 'pt',
  interpolation: { escapeValue: false },
})

export function changeLanguage(lang: Language) {
  localStorage.setItem(STORAGE_KEY, lang)
  void i18n.changeLanguage(lang)
}

export default i18n
