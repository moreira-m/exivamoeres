import { useTranslation } from 'react-i18next'

/**
 * O código da requisição que falhou, em letra pequena e **selecionável**.
 *
 * É a ponta que faltava no correlation ID (item T6): o servidor marca toda linha de log
 * com este id, mas ele vivia só no cabeçalho da resposta — visível para quem abre o
 * devtools. Quem reporta um problema não tinha o que mandar, e quem investiga voltava a
 * procurar por horário.
 *
 * `select-all` no CSS e `<code>`: o gesto esperado é copiar e colar num relato.
 *
 * Não renderiza nada quando não há id — ver `services/requestId.ts` sobre por que **não**
 * inventamos um.
 */
export function RequestIdNote({ id }: { id: string | null }) {
  const { t } = useTranslation()
  if (!id) {
    return null
  }
  return (
    <p className="mt-3 text-xs font-bold text-ink/50">
      {t('errors.requestId')}{' '}
      <code className="select-all font-mono text-ink/70">{id}</code>
    </p>
  )
}
