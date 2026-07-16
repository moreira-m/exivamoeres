import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { Layout } from '../../components/Layout'
import { Card } from '../../components/ui/Card'
import { Button } from '../../components/ui/Button'
import { Input, Select } from '../../components/ui/Input'
import { useCreateList, useMyLists } from '../../hooks/useLists'
import { useCreatures } from '../../hooks/useCatalog'
import { useMyCharacters } from '../../hooks/useCharacters'
import { useAuth } from '../../hooks/useAuth'
import { getApiErrorMessage } from '../../lib/apiError'
import type { JoinPolicy } from '../../types/api'

const FREE_ACTIVE_LIMIT = 3

export function CreateTeamPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const creatures = useCreatures()
  const characters = useMyCharacters()
  const createList = useCreateList()
  const myLists = useMyLists()
  const { user } = useAuth()

  const [characterId, setCharacterId] = useState('')
  const [creatureId, setCreatureId] = useState('')
  const [joinPolicy, setJoinPolicy] = useState<JoinPolicy>('MANUAL_APPROVAL')
  const [minimumLevel, setMinimumLevel] = useState('')
  const [pricePerSlot, setPricePerSlot] = useState('')
  const [error, setError] = useState('')

  // O world do time é ditado pelo personagem escolhido (regra: todos do mesmo world).
  const selectedCharacter = useMemo(
    () => characters.data?.find((c) => c.id === Number(characterId)),
    [characters.data, characterId],
  )

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError('')
    if (!selectedCharacter) {
      setError(t('createTeam.chooseCharacter'))
      return
    }
    try {
      const detail = await createList.mutateAsync({
        world: selectedCharacter.world,
        targetCreatureId: Number(creatureId),
        joinPolicy,
        characterId: Number(characterId),
        minimumLevel: minimumLevel ? Number(minimumLevel) : null,
        pricePerSlot: pricePerSlot ? Number(pricePerSlot) : null,
      })
      navigate(`/teams/${detail.summary.id}`)
    } catch (err) {
      setError(getApiErrorMessage(err))
    }
  }

  const noCharacters = characters.data && characters.data.length === 0

  // Vagas do plano free (o backend é quem garante o limite; aqui é só UX).
  const activeCount = (myLists.data ?? []).filter((t) => t.status === 'ACTIVE').length
  const isFree = user?.plan === 'FREE'
  const atLimit = isFree && activeCount >= FREE_ACTIVE_LIMIT

  return (
    <Layout>
      <h1 className="mb-6 text-3xl text-white drop-shadow-[3px_3px_0_#1a1a1a]">
        {t('createTeam.title')}
      </h1>

      {isFree && (
        <Card className="mb-4 flex max-w-xl items-center justify-between p-4">
          <span className="font-bold text-ink">
            {t('createTeam.freeSlots', { count: activeCount, limit: FREE_ACTIVE_LIMIT })}
          </span>
          {atLimit && (
            <a href="/account/billing" className="font-extrabold uppercase text-accent underline">
              {t('myTeams.subscribePremium')}
            </a>
          )}
        </Card>
      )}

      {atLimit && (
        <Card className="mb-4 max-w-xl p-4 font-bold text-accent">
          {t('createTeam.atLimit', { limit: FREE_ACTIVE_LIMIT })}
        </Card>
      )}

      <Card className="max-w-xl p-6">
        {noCharacters ? (
          <p className="font-bold text-ink">
            {t('createTeam.needCharacter')}{' '}
            <a href="/account/characters" className="text-accent underline">
              {t('nav.characters')}
            </a>
          </p>
        ) : (
          <form onSubmit={submit} className="space-y-4 [&_span]:text-ink">
            <Select
              label={t('createTeam.yourCharacter')}
              value={characterId}
              onChange={(e) => setCharacterId(e.target.value)}
              required
            >
              <option value="">{t('common.select')}</option>
              {characters.data?.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name} · {c.world}
                </option>
              ))}
            </Select>
            {selectedCharacter && (
              <p className="text-sm font-bold text-ink/70">
                {t('createTeam.worldInfo', { world: selectedCharacter.world })}
              </p>
            )}
            <Select
              label={t('createTeam.targetCreature')}
              value={creatureId}
              onChange={(e) => setCreatureId(e.target.value)}
              required
            >
              <option value="">{t('common.select')}</option>
              {creatures.data?.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                  {c.difficulty ? ` (dif. ${c.difficulty})` : ''}
                </option>
              ))}
            </Select>
            <Select
              label={t('createTeam.joinPolicy')}
              value={joinPolicy}
              onChange={(e) => setJoinPolicy(e.target.value as JoinPolicy)}
            >
              <option value="MANUAL_APPROVAL">{t('createTeam.joinPolicyManual')}</option>
              <option value="AUTO_ACCEPT">{t('createTeam.joinPolicyAuto')}</option>
            </Select>
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

            {error && <p className="font-bold text-accent">{error}</p>}

            <Button type="submit" disabled={createList.isPending || atLimit}>
              {t('createTeam.submit')}
            </Button>
          </form>
        )}
      </Card>
    </Layout>
  )
}
