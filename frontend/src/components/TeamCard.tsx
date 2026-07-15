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
        className={`flex items-center gap-4 p-4 transition-transform hover:-translate-x-0.5 hover:-translate-y-0.5
          ${team.featured ? 'ring-4 ring-accent' : ''}`}
      >
        <CreatureIcon imageUrl={team.targetCreatureImageUrl} name={team.targetCreatureName} size={52} />
        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-2">
            <h3 className="truncate text-lg text-ink">{team.name}</h3>
            {team.featured && <Badge tone="accent">{t('teamCard.featured')}</Badge>}
          </div>
          <p className="text-sm font-bold text-ink/70">
            {team.targetCreatureName} · {team.world}
          </p>
          <p className="text-xs font-bold text-ink/50">
            {isActive ? formatExpiry(team.expiresAt) : t(`enums.teamStatus.${team.status}`)}
          </p>
        </div>
        <div className="flex flex-col items-end gap-1">
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
