import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { Layout } from '../../components/Layout'
import { TeamCard } from '../../components/TeamCard'
import { Card } from '../../components/ui/Card'
import { Button } from '../../components/ui/Button'
import { Badge } from '../../components/ui/Badge'
import { Spinner } from '../../components/ui/Spinner'
import { useMyLists, useRenewTeam } from '../../hooks/useLists'
import { useAuth } from '../../hooks/useAuth'
import { getApiErrorMessage } from '../../lib/apiError'
import type { ListSummaryResponse } from '../../types/api'

type Tab = 'active' | 'inactive'

const FREE_ACTIVE_LIMIT = 3

export function MyTeamsPage() {
  const { t } = useTranslation()
  const myLists = useMyLists()
  const { user } = useAuth()
  const [tab, setTab] = useState<Tab>('active')

  const { active, inactive } = useMemo(() => {
    const all = myLists.data ?? []
    return {
      active: all.filter((t) => t.status === 'ACTIVE'),
      inactive: all.filter((t) => t.status !== 'ACTIVE'),
    }
  }, [myLists.data])

  const isFree = user?.plan === 'FREE'
  const shown = tab === 'active' ? active : inactive

  return (
    <Layout>
      <div className="mb-6 flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-3xl text-white drop-shadow-[3px_3px_0_#1a1a1a]">{t('myTeams.title')}</h1>
        <Link to="/account/teams/new">
          <Button variant="accent">{t('nav.createTeam')}</Button>
        </Link>
      </div>

      {isFree && (
        <Card className="mb-6 flex items-center justify-between p-4">
          <span className="font-bold text-ink">
            {t('myTeams.freePlan', { count: active.length, limit: FREE_ACTIVE_LIMIT })}
          </span>
          <Link to="/account/billing">
            <Button variant="primary">{t('myTeams.subscribePremium')}</Button>
          </Link>
        </Card>
      )}

      <div className="mb-5 flex gap-2">
        <Button variant={tab === 'active' ? 'primary' : 'neutral'} onClick={() => setTab('active')}>
          {t('myTeams.tabActive', { count: active.length })}
        </Button>
        <Button variant={tab === 'inactive' ? 'primary' : 'neutral'} onClick={() => setTab('inactive')}>
          {t('myTeams.tabInactive', { count: inactive.length })}
        </Button>
      </div>

      {myLists.isLoading ? (
        <Spinner />
      ) : shown.length > 0 ? (
        <div className="grid gap-4 md:grid-cols-2">
          {shown.map((team) =>
            tab === 'inactive' ? (
              <InactiveTeamCard key={team.id} team={team} ownerId={user?.id} />
            ) : (
              <TeamCard key={team.id} team={team} />
            ),
          )}
        </div>
      ) : (
        <Card className="p-6 text-center font-bold">
          {tab === 'active' ? t('myTeams.emptyActive') : t('myTeams.emptyInactive')}
        </Card>
      )}
    </Layout>
  )
}

/** Cartão dos times inativos, com tag de status e ação de renovar (só arquivados, só dono). */
function InactiveTeamCard({ team, ownerId }: { team: ListSummaryResponse; ownerId?: number }) {
  const { t } = useTranslation()
  const renew = useRenewTeam()
  const [error, setError] = useState('')
  // ownerId aqui é o id do usuário logado; o backend valida a posse de verdade.
  const canRenew = team.status === 'ARCHIVED' && ownerId != null

  return (
    <Card className="p-4">
      <div className="flex items-start gap-3">
        <div className="min-w-0 flex-1">
          <Link to={`/teams/${team.id}`} className="block">
            <h3 className="truncate text-lg text-ink">{team.name}</h3>
          </Link>
          <p className="text-sm font-bold text-ink/70">
            {team.targetCreatureName} · {team.world}
          </p>
        </div>
        <Badge tone={team.status === 'COMPLETED' ? 'primary' : 'muted'}>
          {t(`enums.teamStatus.${team.status}`)}
        </Badge>
      </div>
      {canRenew && (
        <div className="mt-3">
          <Button
            variant="accent"
            onClick={async () => {
              setError('')
              try {
                await renew.mutateAsync(team.id)
              } catch (err) {
                setError(getApiErrorMessage(err))
              }
            }}
            disabled={renew.isPending}
          >
            {t('myTeams.renew')}
          </Button>
          {error && <p className="mt-2 font-bold text-accent">{error}</p>}
        </div>
      )}
    </Card>
  )
}
