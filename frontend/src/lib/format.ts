import i18n from '../i18n'

/**
 * Texto curto do prazo de um time, traduzido. Usa o i18n singleton — os
 * componentes que chamam isto re-renderizam ao trocar de idioma (usam
 * useTranslation), então o texto acompanha o idioma atual.
 */
export function formatExpiry(expiresAt: string): string {
  const diffMs = new Date(expiresAt).getTime() - Date.now()
  if (diffMs <= 0) return i18n.t('expiry.expired')
  const days = Math.floor(diffMs / (1000 * 60 * 60 * 24))
  if (days >= 2) return i18n.t('expiry.days', { count: days })
  const hours = Math.floor(diffMs / (1000 * 60 * 60))
  if (hours >= 24) return i18n.t('expiry.tomorrow')
  if (hours >= 1) return i18n.t('expiry.hours', { count: hours })
  return i18n.t('expiry.soon')
}
