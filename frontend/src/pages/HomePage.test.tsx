import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { HomePage } from './HomePage'
import { listsApi } from '../services/listsApi'
import { worldsApi } from '../services/worldsApi'
import { creaturesApi } from '../services/creaturesApi'
import { notificationsApi } from '../services/notificationsApi'
import { SondaDaUrl, conteudo, renderizar, urlAtual } from '../test/renderizar'

vi.mock('../services/listsApi')
vi.mock('../services/worldsApi')
vi.mock('../services/creaturesApi')
vi.mock('../services/notificationsApi')
vi.mock('../services/authService')

/** Uma página vazia da busca, no formato do backend. */
const SEM_RESULTADO = { content: [], number: 0, totalElements: 0, totalPages: 0 }

/** Os parâmetros da última busca disparada. */
function ultimaBusca() {
  const chamadas = vi.mocked(listsApi.search).mock.calls
  return chamadas[chamadas.length - 1][0]
}

/**
 * Os filtros da busca **na URL** (item P22).
 *
 * O que isto destrava é "me manda o link": quem achou três times para um Druid em Refugia
 * consegue compartilhar a busca. De quebra, o botão voltar devolve a lista filtrada e
 * recarregar não zera nada — os dois vêm de graça quando o filtro é endereço.
 *
 * ⚠️ Metade destes testes é sobre **entrada de fora**: parâmetro de URL é digitado por
 * qualquer um, e valor inválido não pode virar requisição nem frase quebrada na tela.
 */
describe('HomePage — filtros na URL', () => {
  beforeEach(() => {
    vi.mocked(worldsApi.list).mockResolvedValue(['Antica', 'Refugia', 'Secura'])
    vi.mocked(creaturesApi.list).mockResolvedValue([
      { id: 7, name: 'Demon', imageUrl: null, difficulty: 4 },
      { id: 9, name: 'Rotworm', imageUrl: null, difficulty: 1 },
    ])
    vi.mocked(listsApi.search).mockResolvedValue(SEM_RESULTADO)
    vi.mocked(notificationsApi.unreadCount).mockResolvedValue(0)
  })

  it('abre sem filtro nenhum quando a URL nao tem parametro', async () => {
    renderizar(<HomePage />, { rota: '/' })

    await waitFor(() => expect(listsApi.search).toHaveBeenCalled())
    expect(ultimaBusca()).toMatchObject({
      world: undefined,
      creatureId: undefined,
      vocation: undefined,
      hasOpenSlots: undefined,
    })
  })

  it('a busca sai filtrada pelo que veio na URL', async () => {
    renderizar(<HomePage />, { rota: '/?world=Refugia&creature=7&vocation=DRUID&slots=open' })

    // É o teste do "abri o link que me mandaram": a primeira requisição já sai filtrada.
    await waitFor(() => expect(listsApi.search).toHaveBeenCalled())
    expect(ultimaBusca()).toMatchObject({
      world: 'Refugia',
      creatureId: 7,
      vocation: 'DRUID',
      hasOpenSlots: true,
    })
  })

  it('os seletores aparecem preenchidos com o que veio na URL', async () => {
    renderizar(<HomePage />, { rota: '/?world=Refugia&vocation=DRUID' })

    // Sem isto, o link abriria a lista certa com os seletores dizendo "Todos" — e a pessoa
    // não saberia por que está vendo só aqueles times.
    // ⚠️ `waitFor` porque o rótulo do mundo só existe depois de a lista de mundos chegar:
    // o seletor mostra o **label** da opção (`Refugia`), não o valor cru da URL.
    await waitFor(() => expect(conteudo().getByLabelText(/mundo/i)).toHaveValue('Refugia'))
    // O seletor de vocação não é pesquisável: ele é um botão, e mostra o rótulo como
    // texto — daí a asserção diferente da de cima.
    expect(conteudo().getByLabelText(/minha vocação/i)).toHaveTextContent('Druid')
  })

  it('escolher um filtro escreve na URL', async () => {
    renderizar(<><HomePage /><SondaDaUrl /></>, { rota: '/' })
    await conteudo().findByLabelText(/mundo/i)

    await userEvent.click(conteudo().getByLabelText(/mundo/i))
    await userEvent.click(await screen.findByRole('option', { name: 'Refugia' }))

    // É o que torna a busca compartilhável: o que está na tela está no endereço.
    await waitFor(() => expect(urlAtual()).toContain('world=Refugia'))
  })

  it('voltar para "todos" tira o parametro da URL, em vez de deixar vazio', async () => {
    renderizar(<><HomePage /><SondaDaUrl /></>, { rota: '/?world=Refugia' })
    await conteudo().findByLabelText(/mundo/i)

    await userEvent.click(conteudo().getByLabelText(/mundo/i))
    await userEvent.click(await screen.findByRole('option', { name: /^todos$/i }))

    // `?world=` na URL dá a impressão de que o link carrega estado escondido.
    await waitFor(() => expect(urlAtual()).not.toContain('world'))
  })

  describe('parâmetro inválido é entrada de fora, não requisição', () => {
    it('vocacao que nao existe e ignorada', async () => {
      renderizar(<HomePage />, { rota: '/?vocation=XPTO' })

      await waitFor(() => expect(listsApi.search).toHaveBeenCalled())
      // Mandar `XPTO` daria 400 no backend (S11) — 400 evitável é ruído no alerta.
      expect(ultimaBusca().vocation).toBeUndefined()
      // E a frase "mostrando times onde um XPTO cabe" não aparece.
      expect(conteudo().queryByText(/xpto/i)).not.toBeInTheDocument()
    })

    it('criatura que nao e numero e ignorada', async () => {
      renderizar(<HomePage />, { rota: '/?creature=abc' })

      await waitFor(() => expect(listsApi.search).toHaveBeenCalled())
      // `Number('abc')` seria `NaN`, e `creatureId=NaN` na query string é 400 na hora.
      expect(ultimaBusca().creatureId).toBeUndefined()
    })

    it('valor estranho em slots nao vira filtro na busca', async () => {
      renderizar(<HomePage />, { rota: '/?slots=talvez' })

      await waitFor(() => expect(listsApi.search).toHaveBeenCalled())
      expect(ultimaBusca().hasOpenSlots).toBeUndefined()
      // O seletor mostra "Todos" — mas isso o `Combobox` já garantiria sozinho (ele cai no
      // `allLabel` quando não acha o valor). Quem garante o **contrato** do filtro saneado
      // é o teste do próprio hook, em `useSearchFilters.test.tsx`.
      expect(conteudo().getByLabelText(/^vagas$/i)).toHaveTextContent(/todos/i)
    })
  })

  it('a dica da vocacao aparece com a vocacao que veio na URL', async () => {
    renderizar(<HomePage />, { rota: '/?vocation=KNIGHT' })

    // A dica existe porque o filtro é "cabe agora", não "exige esta vocação" — e ela
    // precisa acompanhar o filtro que veio do link.
    expect(await conteudo().findByText(/onde um Knight cabe/i)).toBeInTheDocument()
  })
})
