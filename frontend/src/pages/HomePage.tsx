import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { Layout } from '../components/Layout'
import { TeamCard } from '../components/TeamCard'
import { Button } from '../components/ui/Button'
import { Card } from '../components/ui/Card'
import { Combobox } from '../components/ui/Combobox'
import { Spinner } from '../components/ui/Spinner'
import { QueryError } from '../components/ui/QueryError'
import { SELECTABLE_VOCATIONS } from '../components/SlotComposer'
import { useSearchLists } from '../hooks/useLists'
import { useWorlds, useCreatures } from '../hooks/useCatalog'
import { useSearchFilters } from '../hooks/useSearchFilters'
import type { Vocation } from '../types/api'

/**
 * Área pública (sem login): busca de times existentes por world, criatura-alvo,
 * vaga disponível e vocação que cabe. Experiência principal de quem quer só
 * encontrar um time.
 */
export function HomePage() {
  const { t } = useTranslation()
  // Os filtros vivem na **URL** (item P22): assim a busca é compartilhável, o botão voltar
  // devolve o que estava filtrado, e recarregar não zera nada.
  const { filters, setFilter } = useSearchFilters()
  const { world, creatureId, vocation, onlyOpen } = filters

  const worlds = useWorlds()
  const creatures = useCreatures()
  const search = useSearchLists({
    world: world || undefined,
    creatureId: creatureId ? Number(creatureId) : undefined,
    hasOpenSlots: onlyOpen === 'open' ? true : undefined,
    // "Sou Druid" = me mostre os times onde um Druid entra hoje, não os times que
    // exigem Druid em alguma vaga (a vaga pode estar ocupada por outro Druid).
    vocation: (vocation || undefined) as Vocation | undefined,
  })

  const worldOptions = useMemo(
    () => (worlds.data ?? []).map((w) => ({ value: w, label: w })),
    [worlds.data],
  )
  const creatureOptions = useMemo(
    () => (creatures.data ?? []).map((c) => ({ value: String(c.id), label: c.name })),
    [creatures.data],
  )
  const vocationOptions = useMemo(
    () => SELECTABLE_VOCATIONS.map((v) => ({ value: v, label: t(`enums.vocation.${v}`) })),
    [t],
  )

  const teams = useMemo(
    () => (search.data?.pages ?? []).flatMap((page) => page.content),
    [search.data],
  )
  const total = search.data?.pages[0]?.totalElements ?? 0

  return (
    <Layout>
      <section className="mb-8">
        <h1 className="text-4xl text-white drop-shadow-[3px_3px_0_#1a1a1a]">{t('home.title')}</h1>
        <p className="mt-2 max-w-2xl font-bold text-white/90">{t('home.subtitle')}</p>
      </section>

      <Card className="mb-6 grid gap-4 p-4 sm:grid-cols-2 lg:grid-cols-4">
        <Combobox
          label={t('home.world')}
          value={world}
          onChange={(valor) => setFilter('world', valor)}
          options={worldOptions}
          allLabel={t('common.all')}
          placeholder={t('home.searchPlaceholder')}
        />
        <Combobox
          label={t('home.creature')}
          value={creatureId}
          onChange={(valor) => setFilter('creatureId', valor)}
          options={creatureOptions}
          allLabel={t('common.allF')}
          placeholder={t('home.searchPlaceholder')}
        />
        <Combobox
          label={t('home.vocation')}
          value={vocation}
          onChange={(valor) => setFilter('vocation', valor)}
          options={vocationOptions}
          allLabel={t('home.anyVocation')}
          searchable={false}
        />
        <Combobox
          label={t('home.slots')}
          value={onlyOpen}
          onChange={(valor) => setFilter('onlyOpen', valor)}
          options={[{ value: 'open', label: t('home.openOnly') }]}
          allLabel={t('common.all')}
          searchable={false}
        />
      </Card>

      {vocation && (
        /* O filtro é "cabe agora", e sem esta linha ele parece "exige esta vocação" —
           o usuário estranharia ver time sem composição no resultado. */
        <p className="mb-3 text-sm font-bold text-white/80">
          {t('home.vocationHint', { vocation: t(`enums.vocation.${vocation}`) })}
        </p>
      )}

      {search.isLoading ? (
        <Spinner />
      ) : search.isError ? (
        /* Antes desta guarda, uma busca que falhava caía no galho de "vazio" e a
           home afirmava que não existe time — mentira que manda o usuário embora. */
        <QueryError
          error={search.error}
          onRetry={() => void search.refetch()}
          retrying={search.isFetching}
        />
      ) : teams.length > 0 ? (
        <>
          <p className="mb-3 font-bold text-white/90">{t('home.results', { count: total })}</p>
          <div className="grid gap-4 md:grid-cols-2">
            {teams.map((team) => (
              <TeamCard key={team.id} team={team} />
            ))}
          </div>
          {search.hasNextPage && (
            <div className="mt-6 flex justify-center">
              <Button
                variant="neutral"
                onClick={() => void search.fetchNextPage()}
                disabled={search.isFetchingNextPage}
              >
                {search.isFetchingNextPage ? t('common.loading') : t('home.loadMore')}
              </Button>
            </div>
          )}
        </>
      ) : (
        <Card className="p-6 text-center font-bold">{t('home.empty')}</Card>
      )}
    </Layout>
  )
}
