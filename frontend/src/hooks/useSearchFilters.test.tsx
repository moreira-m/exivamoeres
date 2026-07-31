import { describe, expect, it } from 'vitest'
import { useSearchFilters } from './useSearchFilters'
import { renderizar } from '../test/renderizar'
import { cleanup, screen } from '@testing-library/react'

/** Mostra os filtros saneados, para o teste afirmar sobre o contrato do hook. */
function Sonda() {
  const { filters } = useSearchFilters()
  return <output data-testid="filtros">{JSON.stringify(filters)}</output>
}

/**
 * Renderiza a sonda numa URL e devolve os filtros.
 *
 * `cleanup()` antes de cada render porque um teste pode chamar isto mais de uma vez — e
 * duas sondas montadas fazem o `getByTestId` achar duas e reprovar por ambiguidade.
 */
function filtrosDe(rota: string) {
  cleanup()
  renderizar(<Sonda />, { rota })
  return JSON.parse(screen.getByTestId('filtros').textContent ?? '{}')
}

/**
 * O contrato do hook (item P22): **parâmetro de URL é entrada de fora**, e quem chama
 * recebe valor já saneado.
 *
 * Por que testar aqui e não só pela `HomePage`: o `Combobox` disfarça parte do problema
 * (ele cai no rótulo "Todos" quando não reconhece o valor), então um teste de tela passaria
 * mesmo sem saneamento. O contrato é do hook — e quem consumir esses filtros amanhã (um
 * chip de "limpar filtros", um evento de analytics) recebe o mesmo cuidado.
 */
describe('useSearchFilters', () => {
  it('le os quatro filtros da URL', () => {
    expect(filtrosDe('/?world=Refugia&creature=7&vocation=DRUID&slots=open')).toEqual({
      world: 'Refugia',
      creatureId: '7',
      vocation: 'DRUID',
      onlyOpen: 'open',
    })
  })

  it('URL sem parametro nenhum vira filtros vazios', () => {
    expect(filtrosDe('/')).toEqual({ world: '', creatureId: '', vocation: '', onlyOpen: '' })
  })

  it('vocacao que nao existe e descartada', () => {
    // `enums.vocation.XPTO` na frase da tela, e 400 no backend (S11).
    expect(filtrosDe('/?vocation=XPTO').vocation).toBe('')
    expect(filtrosDe('/?vocation=druid').vocation).toBe('') // sem maiúsculas não é o enum
  })

  it('criatura precisa ser numero', () => {
    // `Number('abc')` é `NaN` — e `creatureId=NaN` na query string é 400 na hora.
    expect(filtrosDe('/?creature=abc').creatureId).toBe('')
    expect(filtrosDe('/?creature=7abc').creatureId).toBe('')
    expect(filtrosDe('/?creature=7').creatureId).toBe('7')
  })

  it('slots so aceita o valor que o seletor produz', () => {
    expect(filtrosDe('/?slots=talvez').onlyOpen).toBe('')
    expect(filtrosDe('/?slots=open').onlyOpen).toBe('open')
  })

  it('espaco em volta do mundo e aparado', () => {
    // Copiar e colar link traz espaço com frequência; `world=%20Antica` não é outro mundo.
    expect(filtrosDe('/?world=%20Antica%20').world).toBe('Antica')
  })
})
