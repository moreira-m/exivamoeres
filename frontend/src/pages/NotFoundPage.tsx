import { useTranslation } from 'react-i18next'
import { Link, useLocation } from 'react-router-dom'
import { Layout } from '../components/Layout'
import { Button } from '../components/ui/Button'
import { Card } from '../components/ui/Card'

/**
 * Endereço que não existe. Sem esta página, o `<Routes>` não casava nenhuma rota
 * e renderizava **nada** — a tela ficava totalmente em branco, sem cabeçalho e
 * sem explicação, para qualquer URL errada (link antigo, digitação, página que
 * já existiu e foi removida).
 *
 * Mostra o caminho pedido de propósito: "/account/soulcores não existe" é
 * informação; "algo deu errado" não é.
 */
export function NotFoundPage() {
  const { t } = useTranslation()
  const { pathname } = useLocation()

  return (
    <Layout>
      <Card className="p-6 text-center">
        <h1 className="mb-2 text-2xl text-ink">{t('notFound.title')}</h1>
        <p className="mb-1 font-bold text-ink/70">{t('notFound.explanation')}</p>
        <p className="mb-4 break-all font-mono text-sm text-ink/60">{pathname}</p>
        <Link to="/" className="inline-flex">
          <Button>{t('notFound.backHome')}</Button>
        </Link>
      </Card>
    </Layout>
  )
}
