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
} from '../hooks/useLists'
import { useMyCharacters } from '../hooks/useCharacters'
import { useAuthStore } from '../store/authStore'
import { getApiErrorMessage } from '../lib/apiError'

export function TeamDetailPage() {
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
        <Card className="p-6 text-center font-bold">Time não encontrado.</Card>
      </Layout>
    )
  }

  const team = detail.data.summary
  const isOwner = !!user && detail.data.ownerId === user.id

  // Personagem meu que é membro ativo/aprovado deste time (para agir no time).
  const myCharacterIds = new Set((myChars.data ?? []).map((c) => c.id))
  const myActiveMembership = detail.data.members.find(
    (m) => m.active && m.status === 'APPROVED' && myCharacterIds.has(m.characterId),
  )
  const actingCharacterId = myActiveMembership?.characterId
  const isMember = !!actingCharacterId

  return (
    <Layout>
      <Card className="mb-6 flex flex-wrap items-center gap-4 p-5">
        <CreatureIcon imageUrl={team.targetCreatureImageUrl} name={team.targetCreatureName} size={64} />
        <div className="min-w-0 flex-1">
          <h1 className="text-3xl text-ink">{team.name}</h1>
          <p className="font-bold text-ink/70">
            Alvo: {team.targetCreatureName} · Mundo: {team.world}
          </p>
        </div>
        <div className="flex flex-col items-end gap-1">
          <Badge tone={team.hasOpenSlots ? 'primary' : 'neutral'}>
            {team.memberCount}/{team.maxMembers} membros
          </Badge>
          <Badge tone={team.joinPolicy === 'AUTO_ACCEPT' ? 'accent' : 'muted'}>
            {team.joinPolicy === 'AUTO_ACCEPT' ? 'entrada automática' : 'aprovação manual'}
          </Badge>
        </div>
      </Card>

      <div className="grid gap-6 lg:grid-cols-[1fr_1fr]">
        <div className="space-y-6">
          <MembersCard members={detail.data.members} />
          {!isMember && <JoinCard listId={listId} teamWorld={team.world} full={!team.hasOpenSlots} />}
          {isMember && <LeaveCard listId={listId} />}
          {isOwner && <RequestsCard listId={listId} />}
          {isMember && <SuggestionsCard listId={listId} />}
        </div>
        <div className="space-y-6">
          <SoulcoreBoard listId={listId} actingCharacterId={actingCharacterId} />
          {isMember && actingCharacterId && (
            <ChatPanel listId={listId} actingCharacterId={actingCharacterId} />
          )}
        </div>
      </div>
    </Layout>
  )
}

function MembersCard({ members }: { members: { id: number; characterName: string; vocation: string | null; status: string; active: boolean }[] }) {
  const active = members.filter((m) => m.active && m.status === 'APPROVED')
  return (
    <Card className="p-4">
      <h3 className="mb-3 text-lg text-ink">Membros</h3>
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
          Entre
        </a>{' '}
        para participar deste time.
      </Card>
    )
  }

  const submit = async () => {
    setError('')
    setOk('')
    try {
      await join.mutateAsync({ shareCode: detail.data!.summary.shareCode, characterId: Number(characterId) })
      setOk('Pedido enviado!')
    } catch (err) {
      setError(getApiErrorMessage(err))
    }
  }

  return (
    <Card className="p-4">
      <h3 className="mb-3 text-lg text-ink">Entrar no time</h3>
      {full ? (
        <p className="font-bold text-accent">Este time está cheio.</p>
      ) : eligible.length === 0 ? (
        <p className="text-sm font-bold text-ink/70">
          Você não tem personagem verificado no mundo {teamWorld}.
        </p>
      ) : (
        <div className="flex flex-wrap items-end gap-2 [&_span]:text-ink">
          <Select
            label="Personagem"
            value={characterId}
            onChange={(e) => setCharacterId(e.target.value)}
          >
            <option value="">Selecione…</option>
            {eligible.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name}
              </option>
            ))}
          </Select>
          <Button variant="accent" disabled={!characterId || join.isPending} onClick={submit}>
            Pedir para entrar
          </Button>
        </div>
      )}
      {error && <p className="mt-2 font-bold text-accent">{error}</p>}
      {ok && <p className="mt-2 font-bold text-primary">{ok}</p>}
    </Card>
  )
}

function LeaveCard({ listId }: { listId: number }) {
  const leave = useLeaveList()
  const [error, setError] = useState('')
  return (
    <Card className="flex items-center justify-between p-4">
      <span className="font-bold text-ink">Você participa deste time.</span>
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
        Sair
      </Button>
      {error && <p className="w-full font-bold text-accent">{error}</p>}
    </Card>
  )
}

function RequestsCard({ listId }: { listId: number }) {
  const requests = usePendingRequests(listId, true)
  const { approve, reject } = useRequestDecision(listId)

  if (!requests.data || requests.data.length === 0) {
    return (
      <Card className="p-4">
        <h3 className="mb-2 text-lg text-ink">Pedidos de entrada</h3>
        <p className="text-sm font-bold text-ink/50">Nenhum pedido pendente.</p>
      </Card>
    )
  }

  return (
    <Card className="p-4">
      <h3 className="mb-3 text-lg text-ink">Pedidos de entrada</h3>
      <ul className="space-y-2">
        {requests.data.map((r) => (
          <li key={r.id} className="flex items-center gap-2">
            <span className="flex-1 font-bold text-ink">{r.characterName}</span>
            <Button variant="primary" className="!px-3 !py-1 !text-xs" onClick={() => approve.mutate(r.id)}>
              Aceitar
            </Button>
            <Button variant="neutral" className="!px-3 !py-1 !text-xs" onClick={() => reject.mutate(r.id)}>
              Recusar
            </Button>
          </li>
        ))}
      </ul>
    </Card>
  )
}

function SuggestionsCard({ listId }: { listId: number }) {
  const suggestions = useSuggestions(listId, true)
  const dismiss = useDismissSuggestion(listId)

  if (!suggestions.data || suggestions.data.length === 0) return null

  return (
    <Card className="p-4">
      <h3 className="mb-3 text-lg text-ink">Sugestões de próximos bosses</h3>
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
