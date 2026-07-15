import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Layout } from '../../components/Layout'
import { Card } from '../../components/ui/Card'
import { Button } from '../../components/ui/Button'
import { Input, Select } from '../../components/ui/Input'
import { useCreateList } from '../../hooks/useLists'
import { useCreatures } from '../../hooks/useCatalog'
import { useMyCharacters } from '../../hooks/useCharacters'
import { getApiErrorMessage } from '../../lib/apiError'
import type { JoinPolicy } from '../../types/api'

export function CreateTeamPage() {
  const navigate = useNavigate()
  const creatures = useCreatures()
  const characters = useMyCharacters()
  const createList = useCreateList()

  const [name, setName] = useState('')
  const [characterId, setCharacterId] = useState('')
  const [creatureId, setCreatureId] = useState('')
  const [joinPolicy, setJoinPolicy] = useState<JoinPolicy>('MANUAL_APPROVAL')
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
      setError('Escolha um personagem verificado')
      return
    }
    try {
      const detail = await createList.mutateAsync({
        name: name.trim(),
        world: selectedCharacter.world,
        targetCreatureId: Number(creatureId),
        joinPolicy,
        characterId: Number(characterId),
      })
      navigate(`/teams/${detail.summary.id}`)
    } catch (err) {
      setError(getApiErrorMessage(err))
    }
  }

  const noCharacters = characters.data && characters.data.length === 0

  return (
    <Layout>
      <h1 className="mb-6 text-3xl text-white drop-shadow-[3px_3px_0_#1a1a1a]">Criar time</h1>

      <Card className="max-w-xl p-6">
        {noCharacters ? (
          <p className="font-bold text-ink">
            Você precisa de um personagem verificado para criar um time. Vá em{' '}
            <a href="/account/characters" className="text-accent underline">
              Meus personagens
            </a>
            .
          </p>
        ) : (
          <form onSubmit={submit} className="space-y-4 [&_span]:text-ink">
            <Input
              label="Nome do time"
              value={name}
              onChange={(e) => setName(e.target.value)}
              required
            />
            <Select
              label="Seu personagem"
              value={characterId}
              onChange={(e) => setCharacterId(e.target.value)}
              required
            >
              <option value="">Selecione…</option>
              {characters.data?.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name} · {c.world}
                </option>
              ))}
            </Select>
            {selectedCharacter && (
              <p className="text-sm font-bold text-ink/70">
                O time será do mundo <span className="text-accent">{selectedCharacter.world}</span>.
              </p>
            )}
            <Select
              label="Criatura-alvo (boss)"
              value={creatureId}
              onChange={(e) => setCreatureId(e.target.value)}
              required
            >
              <option value="">Selecione…</option>
              {creatures.data?.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                  {c.difficulty ? ` (dif. ${c.difficulty})` : ''}
                </option>
              ))}
            </Select>
            <Select
              label="Política de entrada"
              value={joinPolicy}
              onChange={(e) => setJoinPolicy(e.target.value as JoinPolicy)}
            >
              <option value="MANUAL_APPROVAL">Aprovação manual</option>
              <option value="AUTO_ACCEPT">Entrada automática</option>
            </Select>

            {error && <p className="font-bold text-accent">{error}</p>}

            <Button type="submit" disabled={createList.isPending}>
              Criar time
            </Button>
          </form>
        )}
      </Card>
    </Layout>
  )
}
