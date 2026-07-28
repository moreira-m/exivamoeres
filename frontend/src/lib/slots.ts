import type { TeamSlotResponse } from '../types/api'

type Translate = (key: string, options?: Record<string, unknown>) => string

/**
 * Resumo do que falta na composição: `"1 Knight · 1 Druid · 1 vaga livre"`.
 *
 * Devolve string vazia quando o time não tem composição ou já está completo — quem
 * chama decide não renderizar nada nesse caso. Agrupa por vocação porque "2 Knight"
 * é o que se anuncia; listar "Knight, Knight" seria ruído.
 */
export function missingComposition(slots: TeamSlotResponse[], t: Translate): string {
  const abertas = (slots ?? []).filter((s) => s.characterName == null)
  if (abertas.length === 0) return ''

  const contagem = new Map<string, number>()
  for (const slot of abertas) {
    const chave = slot.vocation ?? 'ANY'
    contagem.set(chave, (contagem.get(chave) ?? 0) + 1)
  }
  return [...contagem.entries()]
    .map(([chave, quantidade]) =>
      chave === 'ANY'
        ? t('slots.freeSlots', { count: quantidade })
        : `${quantidade} ${t(`enums.vocation.${chave}`)}`,
    )
    .join(' · ')
}
