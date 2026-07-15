import { useMemo, useState } from 'react'
import { Layout } from '../components/Layout'
import { TeamCard } from '../components/TeamCard'
import { Card } from '../components/ui/Card'
import { Combobox } from '../components/ui/Combobox'
import { Spinner } from '../components/ui/Spinner'
import { useSearchLists } from '../hooks/useLists'
import { useWorlds, useCreatures } from '../hooks/useCatalog'

/**
 * Área pública (sem login): busca de times existentes por world, criatura-alvo
 * e vaga disponível. Experiência principal de quem quer só encontrar um time.
 */
export function HomePage() {
  const [world, setWorld] = useState('')
  const [creatureId, setCreatureId] = useState('')
  const [onlyOpen, setOnlyOpen] = useState('')

  const worlds = useWorlds()
  const creatures = useCreatures()
  const search = useSearchLists({
    world: world || undefined,
    creatureId: creatureId ? Number(creatureId) : undefined,
    hasOpenSlots: onlyOpen === 'open' ? true : undefined,
  })

  const worldOptions = useMemo(
    () => (worlds.data ?? []).map((w) => ({ value: w, label: w })),
    [worlds.data],
  )
  const creatureOptions = useMemo(
    () => (creatures.data ?? []).map((c) => ({ value: String(c.id), label: c.name })),
    [creatures.data],
  )

  return (
    <Layout>
      <section className="mb-8">
        <h1 className="text-4xl text-white drop-shadow-[3px_3px_0_#1a1a1a]">
          Encontre seu time de Soul Core
        </h1>
        <p className="mt-2 max-w-2xl font-bold text-white/90">
          Grupos organizados para caçar bosses do Tibia e trocar soul cores. Filtre por
          mundo, criatura e vagas — entre em um time ou crie o seu.
        </p>
      </section>

      <Card className="mb-6 grid gap-4 p-4 sm:grid-cols-3">
        <Combobox
          label="Mundo"
          value={world}
          onChange={setWorld}
          options={worldOptions}
          allLabel="Todos"
          placeholder="Digite para buscar…"
        />
        <Combobox
          label="Criatura"
          value={creatureId}
          onChange={setCreatureId}
          options={creatureOptions}
          allLabel="Todas"
          placeholder="Digite para buscar…"
        />
        <Combobox
          label="Vagas"
          value={onlyOpen}
          onChange={setOnlyOpen}
          options={[{ value: 'open', label: 'Só com vaga aberta' }]}
          allLabel="Todos"
          searchable={false}
        />
      </Card>

      {search.isLoading ? (
        <Spinner />
      ) : search.data && search.data.content.length > 0 ? (
        <div className="grid gap-4 md:grid-cols-2">
          {search.data.content.map((team) => (
            <TeamCard key={team.id} team={team} />
          ))}
        </div>
      ) : (
        <Card className="p-6 text-center font-bold">
          Nenhum time encontrado com esses filtros. Que tal criar um?
        </Card>
      )}
    </Layout>
  )
}
