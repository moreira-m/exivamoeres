import { useState } from 'react'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { SlotComposer, emptyComposition, MAX_TEAM_SLOTS } from './SlotComposer'
import { renderizar } from '../test/renderizar'
import type { Vocation } from '../types/api'

/** Envolve o composer no estado que o formulário de verdade mantém. */
function ComposerControlado({ inicial }: { inicial: (Vocation | null)[] }) {
  const [slots, setSlots] = useState(inicial)
  return (
    <>
      <SlotComposer value={slots} onChange={setSlots} />
      <output data-testid="estado">{JSON.stringify(slots)}</output>
    </>
  )
}

describe('SlotComposer', () => {
  it('uma linha por vaga, todas em "qualquer" por padrao', () => {
    renderizar(<SlotComposer value={emptyComposition()} onChange={vi.fn()} />)

    const vagas = screen.getAllByRole('combobox')
    expect(vagas).toHaveLength(MAX_TEAM_SLOTS)
    // "Qualquer" é o default de propósito: quem não quer restringir composição
    // não precisa entender esta seção.
    vagas.forEach((vaga) => expect(vaga).toHaveValue(''))
    expect(screen.getByText(/vaga 1/i)).toBeInTheDocument()
  })

  it('mudar uma vaga nao mexe nas outras', async () => {
    renderizar(<ComposerControlado inicial={emptyComposition(3)} />)

    await userEvent.selectOptions(screen.getAllByRole('combobox')[1], 'PALADIN')

    // O `setAt` copia o array; se mutasse o `value` recebido, o React podia não
    // re-renderizar — e a tela mostraria a vaga antiga.
    expect(screen.getByTestId('estado')).toHaveTextContent('[null,"PALADIN",null]')
  })

  it('voltar para "qualquer" grava null, nao string vazia', async () => {
    renderizar(<ComposerControlado inicial={['KNIGHT', null]} />)

    await userEvent.selectOptions(screen.getAllByRole('combobox')[0], '')

    // `''` chegaria ao backend como vocação inválida; `null` é "vaga livre".
    expect(screen.getByTestId('estado')).toHaveTextContent('[null,null]')
  })

  it('NONE nao e oferecido como exigencia de vaga', () => {
    renderizar(<SlotComposer value={emptyComposition(1)} onChange={vi.fn()} />)

    const opcoes = screen.getAllByRole('option').map((o) => o.getAttribute('value'))
    // Ninguém exige "sem vocação" numa vaga; oferecer isso é oferecer um time
    // que nenhum personagem preenche.
    expect(opcoes).toEqual(['', 'KNIGHT', 'PALADIN', 'SORCERER', 'DRUID', 'MONK'])
  })

  describe('vaga ocupada', () => {
    const ocupada = [{ position: 2, characterName: 'Druida de Antica', vocation: 'DRUID' as const }]

    it('mostra quem esta na vaga, com a vocacao', () => {
      renderizar(
        <SlotComposer value={[null, 'DRUID', null]} onChange={vi.fn()} occupants={ocupada} />,
      )

      // Mostrar o **nome** é o ponto: é quem precisa caber na composição nova.
      expect(screen.getByText(/ocupada por Druida de Antica \(Druid\)/i)).toBeInTheDocument()
    })

    it('marca, mas nao trava o campo', () => {
      renderizar(
        <SlotComposer value={[null, 'DRUID', null]} onChange={vi.fn()} occupants={ocupada} />,
      )

      // Regressão do P24: a primeira versão desabilitava a vaga ocupada, o que
      // impedia **reordenar** — e reordenar é permitido pelo backend (a regra é
      // "a composição nova precisa caber em quem já está no time").
      const vagasOcupadas = screen
        .getAllByRole('combobox')
        .filter((vaga) => vaga.closest('div')?.textContent?.includes('ocupada por'))
      expect(vagasOcupadas).not.toHaveLength(0)
      vagasOcupadas.forEach((vaga) => expect(vaga).toBeEnabled())
    })

    it('vaga sem ocupante nao mostra rotulo nenhum', () => {
      renderizar(
        <SlotComposer value={[null, 'DRUID', null]} onChange={vi.fn()} occupants={ocupada} />,
      )

      // As vagas que o dono acrescenta no editor não têm ocupante: um rótulo
      // vazio ali sugeriria alguém que não existe.
      expect(screen.getAllByText(/ocupada por/i)).toHaveLength(1)
    })
  })

  it('emptyComposition devolve o tamanho pedido, todo livre', () => {
    expect(emptyComposition()).toEqual([null, null, null, null, null])
    expect(emptyComposition(2)).toEqual([null, null])
  })
})
