import { useSoulcoreBoard, useSoulcoreActions } from '../hooks/useLists'
import { useCreatures } from '../hooks/useCatalog'
import { Card } from './ui/Card'
import { Badge } from './ui/Badge'
import { Button } from './ui/Button'
import { Spinner } from './ui/Spinner'
import { useState } from 'react'
import { getApiErrorMessage } from '../lib/apiError'

interface Props {
  listId: number
  /** Se definido, o usuário é membro e pode registrar ações com este personagem. */
  actingCharacterId?: number
}

/** Board dos soul cores rastreados no time; membros marcam obtido/desbloqueado. */
export function SoulcoreBoard({ listId, actingCharacterId }: Props) {
  const board = useSoulcoreBoard(listId)
  const creatures = useCreatures()
  const { obtain, unlock } = useSoulcoreActions(listId)
  const [creatureId, setCreatureId] = useState('')
  const [error, setError] = useState('')

  const act = async (kind: 'obtain' | 'unlock', targetCreatureId: number) => {
    if (!actingCharacterId) return
    setError('')
    try {
      const mutation = kind === 'obtain' ? obtain : unlock
      await mutation.mutateAsync({ creatureId: targetCreatureId, characterId: actingCharacterId })
    } catch (err) {
      setError(getApiErrorMessage(err))
    }
  }

  return (
    <Card className="p-4">
      <h3 className="mb-3 text-lg text-ink">Soul cores</h3>

      {board.isLoading ? (
        <Spinner label="Carregando board…" />
      ) : board.data && board.data.length > 0 ? (
        <ul className="space-y-2">
          {board.data.map((sc) => (
            <li key={sc.id} className="flex flex-wrap items-center gap-2 border-b-2 border-ink/10 pb-2">
              <span className="font-bold text-ink">{sc.creatureName}</span>
              <Badge tone={sc.status === 'UNLOCKED' ? 'primary' : 'accent'}>{sc.status}</Badge>
              {sc.obtainedByCharacterName && (
                <span className="text-sm text-ink/60">por {sc.obtainedByCharacterName}</span>
              )}
              {actingCharacterId && sc.status === 'OBTAINED' && (
                <Button
                  variant="primary"
                  className="ml-auto !px-3 !py-1 !text-xs"
                  onClick={() => act('unlock', sc.creatureId)}
                >
                  Desbloquear
                </Button>
              )}
            </li>
          ))}
        </ul>
      ) : (
        <p className="text-sm font-bold text-ink/50">Nenhum core registrado ainda.</p>
      )}

      {actingCharacterId && (
        <div className="mt-4 flex flex-wrap items-end gap-2">
          <select
            value={creatureId}
            onChange={(e) => setCreatureId(e.target.value)}
            className="border-[3px] border-ink bg-white px-3 py-2 font-mono text-ink"
          >
            <option value="">Registrar core obtido…</option>
            {creatures.data?.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name}
              </option>
            ))}
          </select>
          <Button
            variant="accent"
            disabled={!creatureId}
            onClick={() => creatureId && act('obtain', Number(creatureId))}
          >
            Marcar obtido
          </Button>
        </div>
      )}
      {error && <p className="mt-2 text-sm font-bold text-accent">{error}</p>}
    </Card>
  )
}
