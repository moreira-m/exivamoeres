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
/** Quem está numa vaga, na hora de editar a composição. */
export interface SlotOccupant {
  /** Posição (1-based) da vaga. */
  position: number
  characterName: string
  vocation: Vocation | null
}

export function SlotComposer({
  value,
  onChange,
  occupants = [],
}: {
  value: (Vocation | null)[]
  onChange: (slots: (Vocation | null)[]) => void
  /**
   * Quem já está dentro, por vaga. Serve para **marcar**, nunca para travar: a regra
   * do backend é "a composição nova precisa caber em quem já está no time" (reordenar
   * é permitido, e a recusa nomeia quem ficaria de fora).
   *
   * Mostrar o **nome** é o ponto: sem ele, o dono sabe que a vaga tem alguém e não
   * quem — e é justamente quem que precisa caber na composição nova. Antes a prop era
   * `number[]` (só as posições) e antes disso `disabledPositions`, que desabilitava o
   * campo porque a primeira versão do P3 tinha a regra "vaga ocupada não muda" — regra
   * que mudou no meio da entrega. Ver NEXT_STEPS P23 e P24.
   */
  occupants?: SlotOccupant[]
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
        const ocupante = occupants.find((o) => o.position === position)
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
            {ocupante && (
              // Informação, não impedimento. As vagas que o dono acrescenta no editor
              // (o rascunho vai até MAX_TEAM_SLOTS) não têm ocupante e não mostram nada.
              <span className="shrink-0 text-xs font-bold text-ink/50">
                {t('slots.occupiedBy', {
                  name: ocupante.characterName,
                  vocation: ocupante.vocation
                    ? t(`enums.vocation.${ocupante.vocation}`)
                    : t('slots.any'),
                })}
              </span>
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
