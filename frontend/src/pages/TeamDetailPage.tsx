import { useMemo, useState } from 'react'
import { useNavigate, useParams } from 'react-router'
import { ErrorBoundary } from '../components/ErrorBoundary'
import { Layout } from '../components/Layout'
import { Card } from '../components/ui/Card'
import { Button } from '../components/ui/Button'
import { Badge } from '../components/ui/Badge'
import { Input, Select, Textarea } from '../components/ui/Input'
import { Spinner } from '../components/ui/Spinner'
import { QueryError } from '../components/ui/QueryError'
import { CreatureIcon } from '../components/CreatureIcon'
import { SlotComposer, MAX_TEAM_SLOTS, emptyComposition } from '../components/SlotComposer'
import { ChatPanel } from '../components/ChatPanel'
import {
  useListDetail,
  useUpdateList,
  useJoinList,
  useLeaveList,
  usePendingRequests,
  useRequestDecision,
  useRenewTeam,
  useKickMember,
  useDeleteTeam,
  useReplaceSlots,
} from '../hooks/useLists'
import { useMyCharacters } from '../hooks/useCharacters'
import { useAuthStore } from '../store/authStore'
import { getApiErrorMessage, isNotFound } from '../lib/apiError'
import { formatExpiry, tibiaCharacterUrl } from '../lib/format'
import { useTranslation } from 'react-i18next'
import type {
  ListDetailResponse,
  MembershipResponse,
  TeamSlotResponse,
  Vocation,
} from '../types/api'

export function TeamDetailPage() {
  const { t } = useTranslation()
  const { id } = useParams()
  const listId = Number(id)
  // Id de time é inteiro positivo. Qualquer outra coisa na URL não é "erro do
  // servidor", é endereço que não aponta para time nenhum — e a tela diz isso na
  // hora, sem gastar requisição.
  const listIdValido = Number.isInteger(listId) && listId > 0
  const detail = useListDetail(listId, listIdValido)
  const user = useAuthStore((s) => s.user)
  const myChars = useMyCharacters()

  if (!listIdValido) {
    return (
      <Layout>
        <Card className="p-6 text-center font-bold">{t('teamDetail.notFound')}</Card>
      </Layout>
    )
  }
  if (detail.isLoading) {
    return (
      <Layout>
        <Spinner />
      </Layout>
    )
  }
  // 404 é definitivo ("este time não existe"); qualquer outra falha é transitória
  // e pede "tentar de novo". Antes, as duas viravam "time não encontrado" — o que
  // faz o dono achar que o time dele desapareceu.
  if (detail.isError && !isNotFound(detail.error)) {
    return (
      <Layout>
        <QueryError
          error={detail.error}
          onRetry={() => void detail.refetch()}
          retrying={detail.isFetching}
        />
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
        <CreatureIcon imageUrl={team.targetCreatureImageUrl} name={team.targetCreatureName} size={72} />
        <div className="min-w-0 flex-1">
          {/* Rótulo do que o número grande significa: sem ele, "Demon" no topo pode
              ser lido como o nome do time (que aparece logo abaixo). */}
          <span className="text-xs font-extrabold uppercase tracking-wide text-ink/50">
            {t('teamDetail.target')}
          </span>
          <div className="flex flex-wrap items-center gap-2">
            {/* Criatura-alvo em destaque; nome do time abaixo, secundário. */}
            <h1 className="text-3xl text-ink">{team.targetCreatureName}</h1>
            {team.featured && <Badge tone="accent">{t('teamCard.featured')}</Badge>}
            {!isActive && <Badge tone="neutral">{t(`enums.teamStatus.${team.status}`)}</Badge>}
          </div>
          <p className="font-bold text-ink/60">
            {t('teamDetail.world')}: {team.world}
          </p>
          <div className="mt-1 flex flex-wrap items-center gap-x-4 gap-y-1 text-sm font-bold">
            <span className="text-ink/50">
              {isActive
                ? formatExpiry(team.expiresAt)
                : t('teamDetail.teamStatusInfo', { status: t(`enums.teamStatus.${team.status}`) })}
            </span>
            {team.minimumLevel != null && (
              <span className="text-ink/70">
                {t('teamDetail.minimumLevel')}: {team.minimumLevel}
              </span>
            )}
            {team.pricePerSlot != null && (
              <span className="text-accent">
                {t('teamDetail.pricePerSlot')}: {team.pricePerSlot.toLocaleString()}{' '}
                {t('teamDetail.priceUnit')}
              </span>
            )}
            {team.huntSchedule && (
              <span className="text-ink/70">
                🕑 {t('teamDetail.huntSchedule')}: {team.huntSchedule}
              </span>
            )}
          </div>
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

      {/* Time encerrado: a página abria sem botão de entrar e sem chat, **sem dizer por
          quê** — e um link de time encerrado continua circulando (compartilhamento,
          histórico). O `ARCHIVED` tinha o RenewCard para o dono; o `CLOSED` não tinha
          nada, para ninguém. A frase existia traduzida e nenhuma tela a mostrava. */}
      {team.status === 'CLOSED' && (
        <Card className="mb-6 p-4 font-bold text-accent">{t('teamDetail.closedInfo')}</Card>
      )}

      {isOwner && team.status === 'ARCHIVED' && <RenewCard listId={listId} />}

      {/* Editar é ação de dono e só em time ativo — o backend recusa o resto.
          Cercado por boundary de seção: é bloco secundário, e um erro aqui não pode
          esconder o time de quem só quer ler (T9). */}
      {isOwner && isActive && (
        <ErrorBoundary section={t('teamDetail.edit')}>
          <EditTeamCard listId={listId} detail={detail.data} />
        </ErrorBoundary>
      )}

      {/* Composição: mostra a todos (é o que faz alguém decidir se serve para ele)
          e deixa o dono reconfigurar as vagas VAZIAS. */}
      {(team.slots.length > 0 || (isOwner && isActive)) && (
        // Este é o bloco que originou o T9: foi um `team.slots.length` daqui que
        // derrubou a página do time inteira.
        <ErrorBoundary section={t('slots.title')}>
          <SlotsCard listId={listId} slots={team.slots} canEdit={isOwner && isActive} />
        </ErrorBoundary>
      )}

      {team.description && (
        <Card className="mb-6 p-5">
          <h3 className="mb-2 text-lg text-ink">{t('teamDetail.description')}</h3>
          {/* whitespace-pre-line: o dono escreveu em linhas; respeite as linhas. */}
          <p className="whitespace-pre-line break-words font-bold text-ink/80">{team.description}</p>
        </Card>
      )}

      {/* O contato só chega aqui para dono e membros aprovados — quem decide é o
          backend (o campo vem null para os outros), não esta condição. */}
      {detail.data.contact && (
        <Card className="mb-6 p-5">
          <h3 className="mb-2 text-lg text-ink">{t('teamDetail.contact')}</h3>
          <p className="break-words font-mono font-bold text-ink">{detail.data.contact}</p>
          <p className="mt-1 text-sm font-bold text-ink/60">{t('teamDetail.contactVisibility')}</p>
        </Card>
      )}

      <div className="grid gap-6 lg:grid-cols-[1fr_1fr]">
        <div className="space-y-6">
          <MembersCard
            members={detail.data.members}
            listId={listId}
            ownerId={detail.data.ownerId}
            canManage={isOwner && isActive}
          />
          {isActive && !isMember && (
            <JoinCard listId={listId} teamWorld={team.world} full={!team.hasOpenSlots} />
          )}
          {isMember && isActive && <LeaveCard listId={listId} />}
          {isOwner && isActive && <RequestsCard listId={listId} />}
          {isOwner && isActive && <DeleteTeamCard listId={listId} />}
        </div>
        <div className="space-y-6">
          {canWrite && (
            <ErrorBoundary section={t('chat.title')}>
              <ChatPanel listId={listId} actingCharacterId={canWrite} />
            </ErrorBoundary>
          )}
        </div>
      </div>
    </Layout>
  )
}

/**
 * Edição dos campos que o dono digita à mão. Existe porque, sem ela, um Discord
 * com uma letra errada só se corrigia encerrando o time — perdendo chat,
 * histórico e membros aprovados.
 *
 * Manda o formulário inteiro: campo vazio **limpa** o valor (é o contrato do
 * PATCH). World, criatura e política de entrada não estão aqui de propósito.
 */
function EditTeamCard({ listId, detail }: { listId: number; detail: ListDetailResponse }) {
  const { t } = useTranslation()
  const update = useUpdateList(listId)
  const team = detail.summary
  const [open, setOpen] = useState(false)
  const [name, setName] = useState(team.name)
  const [minimumLevel, setMinimumLevel] = useState(team.minimumLevel?.toString() ?? '')
  const [pricePerSlot, setPricePerSlot] = useState(team.pricePerSlot?.toString() ?? '')
  const [huntSchedule, setHuntSchedule] = useState(team.huntSchedule ?? '')
  const [description, setDescription] = useState(team.description ?? '')
  const [contact, setContact] = useState(detail.contact ?? '')
  const [error, setError] = useState('')
  const [ok, setOk] = useState('')

  if (!open) {
    return (
      <Card className="mb-6 flex flex-wrap items-center justify-between gap-3 p-4">
        <span className="font-bold text-ink">{t('teamDetail.editInfo')}</span>
        <Button variant="neutral" onClick={() => setOpen(true)}>
          {t('teamDetail.edit')}
        </Button>
      </Card>
    )
  }

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError('')
    setOk('')
    try {
      await update.mutateAsync({
        name: name.trim() || null,
        minimumLevel: minimumLevel ? Number(minimumLevel) : null,
        pricePerSlot: pricePerSlot ? Number(pricePerSlot) : null,
        huntSchedule: huntSchedule.trim() || null,
        description: description.trim() || null,
        contact: contact.trim() || null,
      })
      setOk(t('teamDetail.editSaved'))
      setOpen(false)
    } catch (err) {
      setError(getApiErrorMessage(err))
    }
  }

  return (
    <Card className="mb-6 p-5">
      <h3 className="mb-3 text-lg text-ink">{t('teamDetail.edit')}</h3>
      <form onSubmit={submit} className="space-y-4 [&_span]:text-ink">
        <Input
          label={t('teamDetail.editName')}
          maxLength={100}
          placeholder={team.targetCreatureName}
          value={name}
          onChange={(e) => setName(e.target.value)}
        />
        <div className="grid gap-4 sm:grid-cols-2">
          <Input
            label={t('createTeam.minimumLevel')}
            type="number"
            min={1}
            value={minimumLevel}
            onChange={(e) => setMinimumLevel(e.target.value)}
          />
          <Input
            label={t('createTeam.pricePerSlot')}
            type="number"
            min={0}
            value={pricePerSlot}
            onChange={(e) => setPricePerSlot(e.target.value)}
          />
        </div>
        <p className="text-sm font-bold text-ink/60">{t('teamDetail.editLevelHint')}</p>
        <Input
          label={t('createTeam.huntSchedule')}
          maxLength={120}
          placeholder={t('createTeam.huntSchedulePlaceholder')}
          value={huntSchedule}
          onChange={(e) => setHuntSchedule(e.target.value)}
        />
        <div>
          <Textarea
            label={t('createTeam.description')}
            maxLength={500}
            rows={4}
            value={description}
            onChange={(e) => setDescription(e.target.value)}
          />
          <p className="mt-1 text-sm font-bold text-ink/60">
            {t('createTeam.charsLeft', { count: 500 - description.length })}
          </p>
        </div>
        <div>
          <Input
            label={t('createTeam.contact')}
            maxLength={120}
            placeholder={t('createTeam.contactPlaceholder')}
            value={contact}
            onChange={(e) => setContact(e.target.value)}
          />
          <p className="mt-1 text-sm font-bold text-primary">{t('createTeam.contactHint')}</p>
        </div>

        <p className="text-sm font-bold text-ink/60">{t('teamDetail.editClearHint')}</p>
        {error && <p className="font-bold text-accent">{error}</p>}
        {ok && <p className="font-bold text-primary">{ok}</p>}

        <div className="flex flex-wrap gap-2">
          <Button type="submit" disabled={update.isPending}>
            {t('common.save')}
          </Button>
          <Button type="button" variant="neutral" onClick={() => setOpen(false)}>
            {t('common.cancel')}
          </Button>
        </div>
      </form>
    </Card>
  )
}

/**
 * A composição do time por vocação. Para quem olha, é "quem falta"; para o dono, é
 * onde ele reconfigura — só as **vagas vazias**, porque mudar a exigência de uma
 * vaga ocupada só daria em expulsar alguém ou anunciar composição irreal (o backend
 * recusa, e a UI trava o campo para não prometer o que não pode).
 */
function SlotsCard({
  listId,
  slots,
  canEdit,
}: {
  listId: number
  slots: TeamSlotResponse[]
  canEdit: boolean
}) {
  const { t } = useTranslation()
  const replace = useReplaceSlots(listId)
  const [editing, setEditing] = useState(false)
  const [draft, setDraft] = useState<(Vocation | null)[]>([])
  const [error, setError] = useState('')

  const abrirEdicao = () => {
    // Preenche o rascunho com a composição atual, completando até o máximo do time
    // para o dono poder acrescentar vaga sem outro controle.
    const atual = slots.map((s) => s.vocation)
    setDraft([...atual, ...emptyComposition(Math.max(0, MAX_TEAM_SLOTS - atual.length))])
    setError('')
    setEditing(true)
  }

  const salvar = async () => {
    setError('')
    try {
      await replace.mutateAsync(draft)
      setEditing(false)
    } catch (err) {
      setError(getApiErrorMessage(err))
    }
  }

  // O dado já vem no `TeamSlotResponse` — antes ele era jogado fora ao montar o
  // rascunho, e o editor só sabia dizer "ocupada".
  const ocupantes = slots
    .filter((s) => s.characterName != null)
    .map((s) => ({
      position: s.position,
      characterName: s.characterName as string,
      vocation: s.characterVocation,
    }))

  return (
    <Card className="mb-6 p-5">
      <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
        <h3 className="text-lg text-ink">{t('slots.title')}</h3>
        {canEdit && !editing && (
          <Button variant="neutral" className="!px-3 !py-1 !text-xs" onClick={abrirEdicao}>
            {slots.length > 0 ? t('slots.edit') : t('slots.define')}
          </Button>
        )}
      </div>

      {editing ? (
        <>
          <SlotComposer value={draft} onChange={setDraft} occupants={ocupantes} />
          <p className="mt-2 text-sm font-bold text-ink/60">{t('slots.editHint')}</p>
          {error && <p className="mt-2 font-bold text-accent">{error}</p>}
          <div className="mt-3 flex flex-wrap gap-2">
            <Button disabled={replace.isPending} onClick={salvar}>
              {t('common.save')}
            </Button>
            <Button variant="neutral" onClick={() => setEditing(false)}>
              {t('common.cancel')}
            </Button>
          </div>
        </>
      ) : slots.length === 0 ? (
        <p className="text-sm font-bold text-ink/60">{t('slots.none')}</p>
      ) : (
        <ul className="space-y-2">
          {slots.map((slot) => (
            <li key={slot.id} className="flex flex-wrap items-center gap-2 text-sm">
              <span className="w-16 shrink-0 font-extrabold uppercase text-ink/60">
                {t('slots.slotN', { position: slot.position })}
              </span>
              <Badge tone={slot.vocation ? 'muted' : 'neutral'}>
                {slot.vocation ? t(`enums.vocation.${slot.vocation}`) : t('slots.any')}
              </Badge>
              {slot.characterName ? (
                <a
                  href={tibiaCharacterUrl(slot.characterName)}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="font-bold text-primary underline decoration-2 underline-offset-2 hover:text-accent"
                >
                  {slot.characterName}
                </a>
              ) : (
                <span className="font-bold text-accent">{t('slots.open')}</span>
              )}
            </li>
          ))}
        </ul>
      )}
    </Card>
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

function MembersCard({
  members,
  listId,
  ownerId,
  canManage,
}: {
  members: MembershipResponse[]
  listId: number
  ownerId: number
  canManage: boolean
}) {
  const { t } = useTranslation()
  const kick = useKickMember(listId)
  const [error, setError] = useState('')
  const active = members.filter((m) => m.active && m.status === 'APPROVED')

  const doKick = async (membershipId: number) => {
    if (!window.confirm(t('teamDetail.kickConfirm'))) return
    setError('')
    try {
      await kick.mutateAsync(membershipId)
    } catch (err) {
      setError(getApiErrorMessage(err))
    }
  }

  return (
    <Card className="p-4">
      <h3 className="mb-3 text-lg text-ink">{t('teamDetail.members')}</h3>
      <ul className="space-y-2">
        {active.map((m) => (
          <li key={m.id} className="flex items-center gap-2">
            {/* Nome linka para a página do personagem no Tibia.com. */}
            <a
              href={tibiaCharacterUrl(m.characterName)}
              target="_blank"
              rel="noopener noreferrer"
              className="font-bold text-primary underline decoration-2 underline-offset-2 hover:text-accent"
            >
              {m.characterName}
              {m.level != null && <span className="text-ink/60"> ({m.level})</span>}
            </a>
            {m.vocation && <span className="text-sm text-ink/60">{m.vocation}</span>}
            {/* Expulsar: só o dono, só em time ativo, e nunca a si mesmo. */}
            {canManage && m.userId !== ownerId && (
              <Button
                variant="neutral"
                className="ml-auto !px-2 !py-1 !text-xs"
                disabled={kick.isPending}
                onClick={() => doKick(m.id)}
              >
                {t('teamDetail.kick')}
              </Button>
            )}
          </li>
        ))}
      </ul>
      {error && <p className="mt-2 font-bold text-accent">{error}</p>}
    </Card>
  )
}

function DeleteTeamCard({ listId }: { listId: number }) {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const deleteTeam = useDeleteTeam()
  const [error, setError] = useState('')

  const doDelete = async () => {
    if (!window.confirm(t('teamDetail.deleteConfirm'))) return
    setError('')
    try {
      await deleteTeam.mutateAsync(listId)
      navigate('/account/teams')
    } catch (err) {
      setError(getApiErrorMessage(err))
    }
  }

  return (
    <Card className="p-4">
      <Button variant="neutral" disabled={deleteTeam.isPending} onClick={doDelete}>
        {t('teamDetail.deleteTeam')}
      </Button>
      {error && <p className="mt-2 font-bold text-accent">{error}</p>}
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
      ) : myChars.isError ? (
        /* "Você não tem personagem neste world" seria mentira: a lista nem
           carregou. Quem acredita nela vai criar um claim que já existe. */
        <QueryError
          error={myChars.error}
          onRetry={() => void myChars.refetch()}
          retrying={myChars.isFetching}
        />
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

  // Sem esta guarda, uma listagem que falha diz ao dono que não há pedidos —
  // e ele perde candidatos sem nunca saber que existiram.
  if (requests.isError) {
    return (
      <Card className="p-4">
        <h3 className="mb-2 text-lg text-ink">{t('teamDetail.requests')}</h3>
        <QueryError
          error={requests.error}
          onRetry={() => void requests.refetch()}
          retrying={requests.isFetching}
        />
      </Card>
    )
  }

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

