import { useTranslation } from 'react-i18next'
import { Layout } from '../../components/Layout'
import { Card } from '../../components/ui/Card'
import { Badge } from '../../components/ui/Badge'
import { useAuth } from '../../hooks/useAuth'

/**
 * Placeholder da assinatura premium. O fluxo de pagamento (Stripe Checkout +
 * Billing Portal) entra na próxima fase; por ora esta página só apresenta o
 * plano atual e os benefícios do premium.
 */
export function BillingPage() {
  const { t } = useTranslation()
  const { user } = useAuth()
  const isPremium = user?.plan === 'PREMIUM'

  return (
    <Layout>
      <h1 className="mb-6 text-3xl text-white drop-shadow-[3px_3px_0_#1a1a1a]">
        {t('billing.title')}
      </h1>

      <Card className="mb-6 flex items-center justify-between p-5">
        <span className="text-lg font-bold text-ink">{t('billing.yourPlan')}</span>
        <Badge tone={isPremium ? 'accent' : 'muted'}>
          {t(`enums.plan.${isPremium ? 'PREMIUM' : 'FREE'}`)}
        </Badge>
      </Card>

      <div className="grid gap-4 md:grid-cols-2">
        <Card className="p-5">
          <h2 className="mb-2 text-xl text-ink">{t('enums.plan.FREE')}</h2>
          <ul className="list-inside list-disc font-bold text-ink/80">
            <li>{t('billing.freeB1')}</li>
            <li>{t('billing.freeB2')}</li>
          </ul>
        </Card>
        <Card className="p-5 ring-4 ring-accent">
          <h2 className="mb-2 text-xl text-ink">{t('enums.plan.PREMIUM')}</h2>
          <ul className="list-inside list-disc font-bold text-ink/80">
            <li>{t('billing.premiumB1')}</li>
            <li>{t('billing.premiumB2')}</li>
            <li>{t('billing.premiumB3')}</li>
          </ul>
          <p className="mt-4 font-extrabold uppercase text-accent">{t('billing.paymentSoon')}</p>
        </Card>
      </div>
    </Layout>
  )
}
