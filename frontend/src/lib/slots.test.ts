import { describe, expect, it } from 'vitest'
import i18n from '../i18n'
import { missingComposition } from './slots'
import type { TeamSlotResponse, Vocation } from '../types/api'

/** Vaga como o backend devolve; `characterName` nulo = aberta. */
function vaga(
  position: number,
  vocation: Vocation | null,
  characterName: string | null = null,
): TeamSlotResponse {
  return {
    id: position,
    position,
    vocation,
    characterId: characterName ? position * 10 : null,
    characterName,
    characterVocation: characterName ? vocation : null,
  }
}

const t = (key: string, options?: Record<string, unknown>) => i18n.t(key, options ?? {})

/**
 * O rótulo "o que falta no time" — a frase que decide se alguém clica no time na
 * busca. Errar aqui não quebra tela nenhuma: só anuncia a vaga errada, que é o
 * tipo de defeito que ninguém reporta como bug.
 */
describe('missingComposition', () => {
  it('agrupa por vocacao em vez de repetir', () => {
    const texto = missingComposition(
      [vaga(1, 'KNIGHT'), vaga(2, 'KNIGHT'), vaga(3, 'DRUID')],
      t,
    )

    // "Knight, Knight" seria ruído; "2 Knight" é o que se anuncia.
    expect(texto).toBe('2 Knight · 1 Druid')
  })

  it('vaga sem exigencia entra como "vaga livre", no plural certo', () => {
    expect(missingComposition([vaga(1, null)], t)).toBe('1 vaga livre')
    expect(missingComposition([vaga(1, null), vaga(2, null)], t)).toBe('2 vagas livres')
  })

  it('ignora vaga ocupada', () => {
    const texto = missingComposition(
      [vaga(1, 'KNIGHT', 'Cavaleiro'), vaga(2, 'DRUID')],
      t,
    )

    // Vaga preenchida não "falta"; contá-la faria o time pedir eternamente
    // alguém que já está nele.
    expect(texto).toBe('1 Druid')
  })

  it('time completo devolve vazio, e nao uma frase vazia de conteudo', () => {
    const texto = missingComposition([vaga(1, 'KNIGHT', 'Cavaleiro')], t)

    // String vazia é o contrato: quem chama decide não renderizar nada.
    expect(texto).toBe('')
  })

  it('time sem composicao devolve vazio', () => {
    expect(missingComposition([], t)).toBe('')
  })

  it('aguenta a lista vir ausente da API', () => {
    // Resposta antiga (ou campo somem numa versão) não pode derrubar a tela —
    // é a mesma defesa de `apiShapes.ts`.
    expect(missingComposition(undefined as unknown as TeamSlotResponse[], t)).toBe('')
  })
})
