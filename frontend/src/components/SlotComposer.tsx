import { useTranslation } from 'react-i18next'
import { Select } from './ui/Input'
import type { Vocation } from '../types/api'

/** As vocações escolhíveis numa vaga. `NONE` não entra: ninguém exige "sem vocação". */
export const SELECTABLE_VOCATIONS: Vocation[] = ['KNIGHT', 'PALADIN', 'SORCERER', 'DRUID', 'MONK']

/**
 * Máximo de vagas de um time. O backend é a autoridade
 * (`app.team.max-members`) e recusa mais que isto — aqui é só UX, como o
 * `FREE_ACTIVE_LIMIT` da tela de criar.
 */
export const MAX_TEAM_SLOTS = 5

/**
 * Editor da composição por vocação: uma linha por vaga, cada uma "qualquer" ou uma
 * vocação. Usado na criação e na edição do time — a mesma forma nos dois lugares,
 * porque é a mesma decisão.
 *
 * **Tudo em "qualquer" = time sem composição** (é como o backend normaliza), e é o
 * default: quem não quer restringir não precisa entender a seção.
 */
export function SlotComposer({
  value,
  onChange,
  occupiedPositions = [],
}: {
  value: (Vocation | null)[]
  onChange: (slots: (Vocation | null)[]) => void
  /**
   * Posições (1-based) que têm alguém dentro. Elas são **marcadas**, não travadas:
   * a regra do backend é "a composição nova precisa caber em quem já está no time"
   * (reordenar é permitido, e a recusa nomeia quem ficaria de fora).
   *
   * Antes esta prop se chamava `disabledPositions` e desabilitava o campo, porque a
   * primeira versão do P3 tinha a regra "vaga ocupada não muda". A regra mudou no meio
   * da entrega e a UI ficou para trás, proibindo o que o servidor aceita — ver
   * NEXT_STEPS P23.
   */
  occupiedPositions?: number[]
}) {
  const { t } = useTranslation()

  const setAt = (index: number, vocation: Vocation | null) => {
    const proximo = [...value]
    proximo[index] = vocation
    onChange(proximo)
  }

  return (
    <div className="space-y-2">
      {value.map((vocation, index) => {
        const position = index + 1
        const ocupada = occupiedPositions.includes(position)
        return (
          <div key={position} className="flex items-center gap-2">
            <span className="w-16 shrink-0 text-sm font-extrabold uppercase text-ink/60">
              {t('slots.slotN', { position })}
            </span>
            <Select
              value={vocation ?? ''}
              onChange={(e) => setAt(index, (e.target.value || null) as Vocation | null)}
            >
              <option value="">{t('slots.any')}</option>
              {SELECTABLE_VOCATIONS.map((v) => (
                <option key={v} value={v}>
                  {t(`enums.vocation.${v}`)}
                </option>
              ))}
            </Select>
            {ocupada && (
              // Informação, não impedimento: o dono precisa saber quem já está dentro
              // para escolher uma composição que caiba.
              <span className="shrink-0 text-xs font-bold text-ink/50">{t('slots.occupied')}</span>
            )}
          </div>
        )
      })}
    </div>
  )
}

/** Lista de vagas "vazia" para o formulário: todas livres = sem composição. */
export function emptyComposition(size = MAX_TEAM_SLOTS): (Vocation | null)[] {
  return Array.from({ length: size }, () => null)
}
