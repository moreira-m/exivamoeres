import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { Layout } from '../../components/Layout'
import { TeamCard } from '../../components/TeamCard'
import { Card } from '../../components/ui/Card'
import { Button } from '../../components/ui/Button'
import { Badge } from '../../components/ui/Badge'
import { Spinner } from '../../components/ui/Spinner'
import { QueryError } from '../../components/ui/QueryError'
import { CreatureIcon } from '../../components/CreatureIcon'
import {
  useMyLists,
  useRenewTeam,
  useMyJoinRequests,
  useCancelMyJoinRequest,
} from '../../hooks/useLists'
import { useAuth } from '../../hooks/useAuth'
import { getApiErrorMessage } from '../../lib/apiError'
import type { ListSummaryResponse, MyJoinRequestResponse } from '../../types/api'

type Tab = 'active' | 'inactive' | 'requests'

const FREE_ACTIVE_LIMIT = 3

export function MyTeamsPage() {
  const { t } = useTranslation()
  const myLists = useMyLists()
  // "Meus pedidos": um pedido pendente não aparecia em lugar nenhum, e a pessoa
  // não sabia se tinha sido ignorada ou recusada.
  const myRequests = useMyJoinRequests()
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
        <Button
          variant={tab === 'requests' ? 'primary' : 'neutral'}
          onClick={() => setTab('requests')}
        >
          {t('myTeams.tabRequests', { count: (myRequests.data ?? []).length })}
        </Button>
      </div>

      {tab === 'requests' ? (
        <MyRequestsTab query={myRequests} />
      ) : myLists.isLoading ? (
        <Spinner />
      ) : myLists.isError ? (
        /* "Você não tem times" seria mentira cruel para quem tem: o dono acharia
           que perdeu os times dele. */
        <QueryError
          error={myLists.error}
          onRetry={() => void myLists.refetch()}
          retrying={myLists.isFetching}
        />
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

/** Aba "meus pedidos": estado de cada pedido de entrada e a saída para desistir. */
function MyRequestsTab({ query }: { query: ReturnType<typeof useMyJoinRequests> }) {
  const { t } = useTranslation()

  if (query.isLoading) return <Spinner />
  if (query.isError) {
    return (
      <QueryError
        error={query.error}
        onRetry={() => void query.refetch()}
        retrying={query.isFetching}
      />
    )
  }
  const requests = query.data ?? []
  if (requests.length === 0) {
    return <Card className="p-6 text-center font-bold">{t('myTeams.emptyRequests')}</Card>
  }
  return (
    <div className="grid gap-4 md:grid-cols-2">
      {requests.map((request) => (
        <JoinRequestCard key={request.id} request={request} />
      ))}
    </div>
  )
}

function JoinRequestCard({ request }: { request: MyJoinRequestResponse }) {
  const { t, i18n } = useTranslation()
  const cancel = useCancelMyJoinRequest()
  const [error, setError] = useState('')
  const pending = request.status === 'PENDING'

  const doCancel = async () => {
    if (!window.confirm(t('myRequests.cancelConfirm'))) return
    setError('')
    try {
      await cancel.mutateAsync(request.id)
    } catch (err) {
      setError(getApiErrorMessage(err))
    }
  }

  return (
    <Card className="p-4">
      <div className="flex items-start gap-3">
        <CreatureIcon
          imageUrl={request.targetCreatureImageUrl}
          name={request.targetCreatureName}
          size={48}
        />
        <div className="min-w-0 flex-1">
          <Link to={`/teams/${request.listId}`} className="block">
            <h3 className="truncate text-lg text-ink">{request.targetCreatureName}</h3>
          </Link>
          <p className="text-sm font-bold text-ink/70">
            {request.world} · {t('myRequests.withCharacter', { character: request.characterName })}
          </p>
          <p className="text-xs font-bold text-ink/50">
            {t('myRequests.requestedAt', {
              date: new Date(request.requestedAt).toLocaleDateString(i18n.language),
            })}
          </p>
        </div>
        <Badge tone={pending ? 'muted' : 'neutral'}>
          {t(`enums.membershipStatus.${request.status}`)}
        </Badge>
      </div>

      {/* O aviso é uma dica, não um veredito: só o dono aprova, e há motivos que
          este aviso não enxerga (perda de Premium, por exemplo). */}
      {request.issue && (
        <p className="mt-3 text-sm font-bold text-accent">
          {t('myRequests.mayNotBeApproved')}{' '}
          {request.issue === 'BELOW_MINIMUM_LEVEL'
            ? t('myRequests.issueBelowMinimumLevel', {
                minimum: request.minimumLevel,
                level: request.characterLevel ?? '?',
              })
            : t('myRequests.issueWorldMismatch', { world: request.world })}
        </p>
      )}

      {pending && (
        <div className="mt-3">
          <Button
            variant="neutral"
            className="!px-3 !py-1 !text-xs"
            disabled={cancel.isPending}
            onClick={doCancel}
          >
            {t('myRequests.cancel')}
          </Button>
        </div>
      )}
      {error && <p className="mt-2 font-bold text-accent">{error}</p>}
    </Card>
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
