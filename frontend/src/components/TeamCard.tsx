import { Link } from 'react-router-dom'
import type { ListSummaryResponse } from '../types/api'
import { Card } from './ui/Card'
import { Badge } from './ui/Badge'
import { CreatureIcon } from './CreatureIcon'

/** Cartão de time exibido na busca pública e em "meus times". */
export function TeamCard({ team }: { team: ListSummaryResponse }) {
  return (
    <Link to={`/teams/${team.id}`} className="block">
      <Card className="flex items-center gap-4 p-4 transition-transform hover:-translate-x-0.5 hover:-translate-y-0.5">
        <CreatureIcon imageUrl={team.targetCreatureImageUrl} name={team.targetCreatureName} size={52} />
        <div className="min-w-0 flex-1">
          <h3 className="truncate text-lg text-ink">{team.name}</h3>
          <p className="text-sm font-bold text-ink/70">
            {team.targetCreatureName} · {team.world}
          </p>
        </div>
        <div className="flex flex-col items-end gap-1">
          <Badge tone={team.hasOpenSlots ? 'primary' : 'neutral'}>
            {team.memberCount}/{team.maxMembers}
          </Badge>
          <Badge tone={team.joinPolicy === 'AUTO_ACCEPT' ? 'accent' : 'muted'}>
            {team.joinPolicy === 'AUTO_ACCEPT' ? 'auto' : 'manual'}
          </Badge>
        </div>
      </Card>
    </Link>
  )
}
