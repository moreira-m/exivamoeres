import { useTranslation } from 'react-i18next'
import { Card } from './Card'
import { Button } from './Button'
import { getApiErrorMessage } from '../../lib/apiError'
import { requestIdDoErro } from '../../services/requestId'
import { RequestIdNote } from './RequestIdNote'

interface Props {
  /** O erro da query (React Query `query.error`), usado só para detalhar a causa. */
  error: unknown
  /** Normalmente `query.refetch`. Sem isto, o card não oferece "tentar de novo". */
  onRetry?: () => void
  retrying?: boolean
}

/**
 * Estado de **falha** de carregamento — o terceiro estado que as telas ignoravam.
 *
 * Existe porque "carregando" e "carregou vazio" não cobrem "não deu para
 * carregar": sem este card, uma requisição que falha aparece como lista vazia, e
 * a tela afirma que não existe nada quando a verdade é que ninguém conseguiu
 * perguntar. É o pior tipo de erro de interface — o que não parece erro.
 *
 * A mensagem técnica (quando o backend respondeu com envelope) vem abaixo da
 * frase principal; quando não houve resposta nenhuma, o texto de rede assume.
 *
 * Quando o backend **respondeu**, o card mostra também o `X-Request-Id` daquela
 * resposta: é o que permite pedir "me manda o código" e achar a requisição no log de uma
 * vez (item T6/P26). Falha sem resposta não tem id — e não inventamos um.
 */
export function QueryError({ error, onRetry, retrying = false }: Props) {
  const { t } = useTranslation()
  return (
    <Card className="p-6 text-center">
      <p className="font-bold text-accent">{t('errors.loadFailed')}</p>
      <p className="mt-1 text-sm font-bold text-ink/70">
        {getApiErrorMessage(error, t('errors.network'))}
      </p>
      {/* O id **deste** erro, não o último visto: um id de outra requisição parece
          confiável e manda quem investiga para a linha errada. */}
      <RequestIdNote id={requestIdDoErro(error)} />
      {onRetry && (
        <Button variant="neutral" className="mt-4" disabled={retrying} onClick={onRetry}>
          {retrying ? t('common.loading') : t('errors.retry')}
        </Button>
      )}
    </Card>
  )
}
