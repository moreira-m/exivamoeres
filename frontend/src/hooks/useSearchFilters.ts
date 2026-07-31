import { useCallback, useMemo } from 'react'
import { useSearchParams } from 'react-router'
import { SELECTABLE_VOCATIONS } from '../components/SlotComposer'
import type { Vocation } from '../types/api'

/** Os filtros da busca, já saneados — o que a tela e a API podem usar sem medo. */
export interface SearchFilters {
  world: string
  creatureId: string
  vocation: string
  onlyOpen: string
}

export type SearchFilterName = keyof SearchFilters

/** O nome de cada filtro na URL. Curto porque a URL é para ser lida e mandada. */
const PARAM: Record<SearchFilterName, string> = {
  world: 'world',
  creatureId: 'creature',
  vocation: 'vocation',
  onlyOpen: 'slots',
}

/**
 * Os filtros da busca **na URL** em vez de em `useState` (item P22).
 *
 * <p>Três coisas que só existem quando o filtro é endereço:</p>
 *
 * - **"me manda o link"**: quem achou três times para um Druid em Refugia consegue
 *   compartilhar isso — antes a instrução era "abra o site e escolha…", que ninguém segue;
 * - o **botão voltar** devolve a busca como estava, em vez da lista inteira do começo;
 * - **recarregar** não zera nada.
 *
 * ⚠️ **Parâmetro de URL é entrada de fora**, e a tela é o primeiro filtro: valor inválido é
 * **ignorado** aqui em vez de virar requisição (`vocation=XPTO` daria 400 no backend desde o
 * S11 — mas 400 evitável é ruído no alerta de erro) e em vez de virar `enums.vocation.XPTO`
 * na frase da tela.
 */
export function useSearchFilters() {
  const [searchParams, setSearchParams] = useSearchParams()

  const filters = useMemo<SearchFilters>(() => ({
    world: searchParams.get(PARAM.world)?.trim() ?? '',
    // Só dígitos: `Number('abc')` seria `NaN` e a API responderia 400.
    creatureId: somenteDigitos(searchParams.get(PARAM.creatureId)),
    // Só uma vocação que existe — a lista é a mesma que o seletor oferece.
    vocation: vocacaoValida(searchParams.get(PARAM.vocation)),
    // Só o valor que o seletor produz; qualquer outro é "sem filtro".
    onlyOpen: searchParams.get(PARAM.onlyOpen) === 'open' ? 'open' : '',
  }), [searchParams])

  /**
   * Troca um filtro na URL.
   *
   * `replace: true` de propósito: sem isso, cada tecla digitada num seletor com busca vira
   * um passo no histórico, e o botão voltar deixaria de sair da página — passaria a
   * desfazer letra por letra.
   *
   * Valor vazio **remove** o parâmetro em vez de deixar `?world=`: a URL é o artefato que
   * a pessoa copia, e lixo nela dá a impressão de que o link tem estado escondido.
   */
  const setFilter = useCallback((name: SearchFilterName, value: string) => {
    setSearchParams((atual) => {
      const proximo = new URLSearchParams(atual)
      if (value) {
        proximo.set(PARAM[name], value)
      } else {
        proximo.delete(PARAM[name])
      }
      return proximo
    }, { replace: true })
  }, [setSearchParams])

  return { filters, setFilter }
}

function somenteDigitos(valor: string | null): string {
  return valor && /^\d+$/.test(valor) ? valor : ''
}

function vocacaoValida(valor: string | null): string {
  return SELECTABLE_VOCATIONS.includes(valor as Vocation) ? (valor as string) : ''
}
