import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import type { ListSummaryResponse } from '../types/api'
import { Card } from './ui/Card'
import { Badge } from './ui/Badge'
import { CreatureIcon } from './CreatureIcon'
import { formatExpiry } from '../lib/format'

/** Cartão de time exibido na busca pública e em "meus times". */
export function TeamCard({ team }: { team: ListSummaryResponse }) {
  const { t } = useTranslation()
  const isActive = team.status === 'ACTIVE'
  return (
    <Link to={`/teams/${team.id}`} className="block">
      <Card
        className={`flex items-center gap-4 p-4 transition-transform hover:-translate-x-0.5 hover:-translate-y-0.5 min-h-[134px]
          ${team.featured ? 'ring-4 ring-accent' : ''}`}
      >
        <CreatureIcon imageUrl={team.targetCreatureImageUrl} name={team.targetCreatureName} size={56} />
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-left gap-1 flex-col">
            {/* A criatura-alvo é o destaque visual; o nome do time é secundário. */}
            <h3 className="truncate text-lg text-ink">{team.targetCreatureName}</h3>
            <div className="mb-1 gap-1 flex">
              {/* {team.featured && <Badge tone="accent">{t('teamCard.featured')}</Badge>} */}
              {team.minimumLevel != null && (
                <Badge tone="muted">{t('teamCard.minLevel', { level: team.minimumLevel })}</Badge>
              )}
            </div>
          </div>
          <p className="truncate text-sm font-bold text-ink/60">{team.world}</p>
          <div className="flex flex-wrap items-center gap-x-3 text-xs font-bold text-ink/50">
            <span>{isActive ? formatExpiry(team.expiresAt) : t(`enums.teamStatus.${team.status}`)}</span>
            {team.pricePerSlot != null && (
              <span className="text-accent">
                {t('teamCard.price', { price: team.pricePerSlot.toLocaleString() })}
              </span>
            )}
          </div>
        </div>
        <div className="flex flex-col items-end gap-1">
          {team.featured && <Badge tone="accent">{t('teamCard.featured')}</Badge>}
          <Badge tone={team.hasOpenSlots && isActive ? 'primary' : 'neutral'}>
            {team.memberCount}/{team.maxMembers}
          </Badge>
          <Badge tone={team.joinPolicy === 'AUTO_ACCEPT' ? 'accent' : 'muted'}>
            {team.joinPolicy === 'AUTO_ACCEPT' ? t('teamCard.auto') : t('teamCard.manual')}
          </Badge>
        </div>
      </Card>
    </Link>
  )
}
