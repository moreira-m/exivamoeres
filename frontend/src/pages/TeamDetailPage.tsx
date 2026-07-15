import { useMemo, useState } from 'react'
import { useParams } from 'react-router-dom'
import { Layout } from '../components/Layout'
import { Card } from '../components/ui/Card'
import { Button } from '../components/ui/Button'
import { Badge } from '../components/ui/Badge'
import { Select } from '../components/ui/Input'
import { Spinner } from '../components/ui/Spinner'
import { CreatureIcon } from '../components/CreatureIcon'
import { ChatPanel } from '../components/ChatPanel'
import { SoulcoreBoard } from '../components/SoulcoreBoard'
import {
  useListDetail,
  useJoinList,
  useLeaveList,
  usePendingRequests,
  useRequestDecision,
  useSuggestions,
  useDismissSuggestion,
  useRenewTeam,
} from '../hooks/useLists'
import { useMyCharacters } from '../hooks/useCharacters'
import { useAuthStore } from '../store/authStore'
import { getApiErrorMessage } from '../lib/apiError'
import { formatExpiry } from '../lib/format'
import { useTranslation } from 'react-i18next'

export function TeamDetailPage() {
  const { t } = useTranslation()
  const { id } = useParams()
  const listId = Number(id)
  const detail = useListDetail(listId)
  const user = useAuthStore((s) => s.user)
  const myChars = useMyCharacters()

  if (detail.isLoading) {
    return (
      <Layout>
        <Spinner />
      </Layout>
    )
  }
  if (!detail.data) {
    return (
      <Layout>
        <Card className="p-6 text-center font-bold">{t('teamDetail.notFound')}</Card>
      </Layout>
    )
  }

  const team = detail.data.summary
  const isOwner = !!user && detail.data.ownerId === user.id
  const isActive = team.status === 'ACTIVE'

  // Personagem meu que é membro ativo/aprovado deste time (para agir no time).
  const myCharacterIds = new Set((myChars.data ?? []).map((c) => c.id))
  const myActiveMembership = detail.data.members.find(
    (m) => m.active && m.status === 'APPROVED' && myCharacterIds.has(m.characterId),
  )
  const actingCharacterId = myActiveMembership?.characterId
  const isMember = !!actingCharacterId
  // Escrita (soulcore, chat) só em time ativo — reflete a regra do backend.
  const canWrite = isActive && isMember ? actingCharacterId : undefined

  return (
    <Layout>
      <Card className="mb-6 flex flex-wrap items-center gap-4 p-5">
        <CreatureIcon imageUrl={team.targetCreatureImageUrl} name={team.targetCreatureName} size={64} />
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2">
            <h1 className="text-3xl text-ink">{team.name}</h1>
            {team.featured && <Badge tone="accent">{t('teamCard.featured')}</Badge>}
            {!isActive && <Badge tone="neutral">{t(`enums.teamStatus.${team.status}`)}</Badge>}
          </div>
          <p className="font-bold text-ink/70">
            {t('teamDetail.target')}: {team.targetCreatureName} · {t('teamDetail.world')}: {team.world}
          </p>
          <p className="text-sm font-bold text-ink/50">
            {isActive
              ? formatExpiry(team.expiresAt)
              : t('teamDetail.teamStatusInfo', { status: t(`enums.teamStatus.${team.status}`) })}
          </p>
        </div>
        <div className="flex flex-col items-end gap-1">
          <Badge tone={team.hasOpenSlots && isActive ? 'primary' : 'neutral'}>
            {t('teamDetail.membersCount', { count: team.memberCount, max: team.maxMembers })}
          </Badge>
          <Badge tone={team.joinPolicy === 'AUTO_ACCEPT' ? 'accent' : 'muted'}>
            {team.joinPolicy === 'AUTO_ACCEPT'
              ? t('createTeam.joinPolicyAuto')
              : t('createTeam.joinPolicyManual')}
          </Badge>
        </div>
      </Card>

      {isOwner && team.status === 'ARCHIVED' && <RenewCard listId={listId} />}

      <div className="grid gap-6 lg:grid-cols-[1fr_1fr]">
        <div className="space-y-6">
          <MembersCard members={detail.data.members} />
          {isActive && !isMember && (
            <JoinCard listId={listId} teamWorld={team.world} full={!team.hasOpenSlots} />
          )}
          {isMember && isActive && <LeaveCard listId={listId} />}
          {isOwner && isActive && <RequestsCard listId={listId} />}
          {isMember && isActive && <SuggestionsCard listId={listId} />}
        </div>
        <div className="space-y-6">
          {/* Board sempre visível (leitura); ações só passam actingCharacterId em time ativo. */}
          <SoulcoreBoard listId={listId} actingCharacterId={canWrite} />
          {canWrite && <ChatPanel listId={listId} actingCharacterId={canWrite} />}
        </div>
      </div>
    </Layout>
  )
}

function RenewCard({ listId }: { listId: number }) {
  const { t } = useTranslation()
  const renew = useRenewTeam()
  const [error, setError] = useState('')
  return (
    <Card className="mb-6 flex flex-wrap items-center justify-between gap-3 p-4">
      <span className="font-bold text-ink">{t('teamDetail.renewInfo')}</span>
      <Button
        variant="accent"
        disabled={renew.isPending}
        onClick={async () => {
          setError('')
          try {
            await renew.mutateAsync(listId)
          } catch (err) {
            setError(getApiErrorMessage(err))
          }
        }}
      >
        {t('teamDetail.renewButton')}
      </Button>
      {error && <p className="w-full font-bold text-accent">{error}</p>}
    </Card>
  )
}

function MembersCard({ members }: { members: { id: number; characterName: string; vocation: string | null; status: string; active: boolean }[] }) {
  const { t } = useTranslation()
  const active = members.filter((m) => m.active && m.status === 'APPROVED')
  return (
    <Card className="p-4">
      <h3 className="mb-3 text-lg text-ink">{t('teamDetail.members')}</h3>
      <ul className="space-y-2">
        {active.map((m) => (
          <li key={m.id} className="flex items-center gap-2">
            <span className="font-bold text-ink">{m.characterName}</span>
            {m.vocation && <span className="text-sm text-ink/60">{m.vocation}</span>}
          </li>
        ))}
      </ul>
    </Card>
  )
}

function JoinCard({ listId, teamWorld, full }: { listId: number; teamWorld: string; full: boolean }) {
  const { t } = useTranslation()
  const user = useAuthStore((s) => s.user)
  const myChars = useMyCharacters()
  const detail = useListDetail(listId)
  const join = useJoinList(listId)
  const [characterId, setCharacterId] = useState('')
  const [error, setError] = useState('')
  const [ok, setOk] = useState('')

  // Só personagens do mesmo world do time são elegíveis (o backend revalida).
  const eligible = useMemo(
    () => (myChars.data ?? []).filter((c) => c.world === teamWorld),
    [myChars.data, teamWorld],
  )

  if (!user) {
    return (
      <Card className="p-4 text-center font-bold text-ink">
        <a href="/login" className="text-accent underline">
          {t('teamDetail.loginLink')}
        </a>{' '}
        {t('teamDetail.loginToJoin')}
      </Card>
    )
  }

  const submit = async () => {
    setError('')
    setOk('')
    try {
      await join.mutateAsync({ shareCode: detail.data!.summary.shareCode, characterId: Number(characterId) })
      setOk(t('teamDetail.requestSent'))
    } catch (err) {
      setError(getApiErrorMessage(err))
    }
  }

  return (
    <Card className="p-4">
      <h3 className="mb-3 text-lg text-ink">{t('teamDetail.join')}</h3>
      {full ? (
        <p className="font-bold text-accent">{t('teamDetail.teamFull')}</p>
      ) : eligible.length === 0 ? (
        <p className="text-sm font-bold text-ink/70">
          {t('teamDetail.noCharacterInWorld', { world: teamWorld })}
        </p>
      ) : (
        <div className="flex flex-wrap items-end gap-2 [&_span]:text-ink">
          <Select
            label={t('teamDetail.character')}
            value={characterId}
            onChange={(e) => setCharacterId(e.target.value)}
          >
            <option value="">{t('common.select')}</option>
            {eligible.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name}
              </option>
            ))}
          </Select>
          <Button variant="accent" disabled={!characterId || join.isPending} onClick={submit}>
            {t('teamDetail.sendRequest')}
          </Button>
        </div>
      )}
      {error && <p className="mt-2 font-bold text-accent">{error}</p>}
      {ok && <p className="mt-2 font-bold text-primary">{ok}</p>}
    </Card>
  )
}

function LeaveCard({ listId }: { listId: number }) {
  const { t } = useTranslation()
  const leave = useLeaveList()
  const [error, setError] = useState('')
  return (
    <Card className="flex items-center justify-between p-4">
      <span className="font-bold text-ink">{t('teamDetail.youParticipate')}</span>
      <Button
        variant="neutral"
        onClick={async () => {
          setError('')
          try {
            await leave.mutateAsync(listId)
          } catch (err) {
            setError(getApiErrorMessage(err))
          }
        }}
      >
        {t('teamDetail.leave')}
      </Button>
      {error && <p className="w-full font-bold text-accent">{error}</p>}
    </Card>
  )
}

function RequestsCard({ listId }: { listId: number }) {
  const { t } = useTranslation()
  const requests = usePendingRequests(listId, true)
  const { approve, reject } = useRequestDecision(listId)

  if (!requests.data || requests.data.length === 0) {
    return (
      <Card className="p-4">
        <h3 className="mb-2 text-lg text-ink">{t('teamDetail.requests')}</h3>
        <p className="text-sm font-bold text-ink/50">{t('teamDetail.noPendingRequests')}</p>
      </Card>
    )
  }

  return (
    <Card className="p-4">
      <h3 className="mb-3 text-lg text-ink">{t('teamDetail.requests')}</h3>
      <ul className="space-y-2">
        {requests.data.map((r) => (
          <li key={r.id} className="flex items-center gap-2">
            <span className="flex-1 font-bold text-ink">{r.characterName}</span>
            <Button variant="primary" className="!px-3 !py-1 !text-xs" onClick={() => approve.mutate(r.id)}>
              {t('teamDetail.approve')}
            </Button>
            <Button variant="neutral" className="!px-3 !py-1 !text-xs" onClick={() => reject.mutate(r.id)}>
              {t('teamDetail.reject')}
            </Button>
          </li>
        ))}
      </ul>
    </Card>
  )
}

function SuggestionsCard({ listId }: { listId: number }) {
  const { t } = useTranslation()
  const suggestions = useSuggestions(listId, true)
  const dismiss = useDismissSuggestion(listId)

  if (!suggestions.data || suggestions.data.length === 0) return null

  return (
    <Card className="p-4">
      <h3 className="mb-3 text-lg text-ink">{t('teamDetail.suggestions')}</h3>
      <ul className="space-y-2">
        {suggestions.data.map((s) => (
          <li key={s.id} className="flex items-center gap-2">
            <span className="flex-1 text-sm text-ink">
              <span className="font-bold">{s.creatureName}</span> — {s.reason}
            </span>
            <Button
              variant="neutral"
              className="!px-2 !py-1 !text-xs"
              onClick={() => dismiss.mutate(s.id)}
            >
              ×
            </Button>
          </li>
        ))}
      </ul>
    </Card>
  )
}
