import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
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
  const { t } = useTranslation()
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
        <h1 className="text-4xl text-white drop-shadow-[3px_3px_0_#1a1a1a]">{t('home.title')}</h1>
        <p className="mt-2 max-w-2xl font-bold text-white/90">{t('home.subtitle')}</p>
      </section>

      <Card className="mb-6 grid gap-4 p-4 sm:grid-cols-3">
        <Combobox
          label={t('home.world')}
          value={world}
          onChange={setWorld}
          options={worldOptions}
          allLabel={t('common.all')}
          placeholder={t('home.searchPlaceholder')}
        />
        <Combobox
          label={t('home.creature')}
          value={creatureId}
          onChange={setCreatureId}
          options={creatureOptions}
          allLabel={t('common.allF')}
          placeholder={t('home.searchPlaceholder')}
        />
        <Combobox
          label={t('home.slots')}
          value={onlyOpen}
          onChange={setOnlyOpen}
          options={[{ value: 'open', label: t('home.openOnly') }]}
          allLabel={t('common.all')}
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
        <Card className="p-6 text-center font-bold">{t('home.empty')}</Card>
      )}
    </Layout>
  )
}
