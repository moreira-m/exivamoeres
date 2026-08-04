import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { MyTeamsPage } from './MyTeamsPage'
import { listsApi } from '../../services/listsApi'
import { notificationsApi } from '../../services/notificationsApi'
import { conteudo, detalheDeTime, logarComo, pagina, renderizar } from '../../test/renderizar'
import type {
  JoinRequestIssue,
  ListSummaryResponse,
  MyJoinRequestResponse,
  Vocation,
} from '../../types/api'

vi.mock('../../services/listsApi')
vi.mock('../../services/notificationsApi')
vi.mock('../../services/authService')

/** Um pedido pendente como o backend entrega em `/api/lists/mine/requests`. */
function pedido(campos: Partial<MyJoinRequestResponse> = {}): MyJoinRequestResponse {
  return {
    id: 1,
    listId: 7,
    listName: 'Time de Teste',
    world: 'Antica',
    targetCreatureName: 'Demon',
    targetCreatureImageUrl: null,
    teamStatus: 'ACTIVE',
    minimumLevel: null,
    characterId: 10,
    characterName: 'Cavaleiro de Antica',
    characterLevel: 300,
    characterVocation: 'KNIGHT',
    status: 'PENDING',
    issue: null,
    requestedAt: new Date().toISOString(),
    ...campos,
  }
}

/**
 * As abas de times vêm **paginadas e recortadas pelo servidor** (item P12).
 *
 * Antes `/api/lists/mine` devolvia um array com todos os status e a tela filtrava. Numa conta
 * antiga isso é histórico que ela nem mostra ao abrir: medido no backend, 9 de 12 itens.
 *
 * ⚠️ O ponto frágil é o **contador**: os rótulos das abas e o aviso do limite do plano free
 * usam o total da aba, não o tamanho do que já foi carregado. Um contador que conte a página
 * faz o aviso do plano mentir sobre quantos times ativos a pessoa tem.
 */
describe('MyTeamsPage — abas paginadas', () => {
  const time = (id: number, status: ListSummaryResponse['status'] = 'ACTIVE') =>
    detalheDeTime({ id, name: `Time ${id}`, status }).summary

  /** Os cartões de time: o `TeamCard` é um link para `/teams/:id`. */
  const cartoes = () =>
    conteudo()
      .getAllByRole('link')
      .filter((a) => a.getAttribute('href')?.startsWith('/teams/'))

  beforeEach(() => {
    logarComo()
    vi.mocked(listsApi.myRequests).mockResolvedValue([])
    vi.mocked(notificationsApi.unreadCount).mockResolvedValue(0)
  })

  /** Responde por aba, que é como o backend passou a atender. */
  function servidorCom(ativos: ListSummaryResponse[], historico: ListSummaryResponse[],
                       totais?: { ativos?: number; historico?: number }) {
    vi.mocked(listsApi.mine).mockImplementation((params = {}) =>
      Promise.resolve(
        params.scope === 'HISTORY'
          ? pagina(historico, totais?.historico)
          : pagina(ativos, totais?.ativos),
      ),
    )
  }

  it('pede as duas abas ao servidor, uma por escopo', async () => {
    servidorCom([time(1)], [time(2, 'ARCHIVED')])
    renderizar(<MyTeamsPage />, { rota: '/account/teams' })

    await waitFor(() => expect(listsApi.mine).toHaveBeenCalledTimes(2))
    const escopos = vi.mocked(listsApi.mine).mock.calls.map(([p]) => p?.scope)
    // As duas de uma vez porque os dois rótulos mostram contagem — e cada uma é um
    // pedido limitado, que é o que a entrega troca por uma resposta sem teto.
    expect(escopos).toEqual(expect.arrayContaining(['ACTIVE', 'HISTORY']))
  })

  it('o contador da aba vem do total, nao do que ja foi carregado', async () => {
    // 2 ativos nesta página, 7 no total: é o caso que só existe quando há paginação, e
    // exatamente o que faria o aviso do plano free dizer "2/3" para quem tem 7.
    servidorCom([time(1), time(2)], [], { ativos: 7 })
    renderizar(<MyTeamsPage />, { rota: '/account/teams' })

    expect(await conteudo().findByRole('button', { name: /ativos \(7\)/i })).toBeInTheDocument()
  })

  it('a tela nao filtra por status: o que vem na aba de ativos e mostrado', async () => {
    // O recorte é do servidor agora. Se a tela voltasse a filtrar, ela esconderia o que o
    // servidor mandou — e as duas verdades divergiriam sem ninguém notar.
    servidorCom([time(1), time(2)], [])
    renderizar(<MyTeamsPage />, { rota: '/account/teams' })

    await waitFor(() => expect(cartoes()).toHaveLength(2))
  })

  it('"carregar mais" aparece quando ha mais paginas e traz a proxima', async () => {
    servidorCom([time(1)], [], { ativos: 2 })
    renderizar(<MyTeamsPage />, { rota: '/account/teams' })
    await conteudo().findByRole('button', { name: /carregar mais/i })

    await userEvent.click(conteudo().getByRole('button', { name: /carregar mais/i }))

    await waitFor(() =>
      expect(vi.mocked(listsApi.mine).mock.calls.some(([p]) => p?.page === 1)).toBe(true),
    )
  })

  it('sem mais paginas, nao oferece "carregar mais"', async () => {
    servidorCom([time(1)], [])
    renderizar(<MyTeamsPage />, { rota: '/account/teams' })
    await waitFor(() => expect(cartoes()).toHaveLength(1))

    expect(conteudo().queryByRole('button', { name: /carregar mais/i })).not.toBeInTheDocument()
  })

  it('falha numa aba mostra "tentar de novo", nao "voce nao tem times"', async () => {
    // "Você não tem times" seria mentira cruel para quem tem: o dono acharia que perdeu os
    // times dele.
    vi.mocked(listsApi.mine).mockRejectedValue(new Error('rede caiu'))
    renderizar(<MyTeamsPage />, { rota: '/account/teams' })

    expect(await conteudo().findByRole('button', { name: /tentar de novo/i })).toBeInTheDocument()
  })
})

/**
 * A aba **"meus pedidos"** — a tela que existe para o pedido pendente não virar
 * limbo. O que ela mostra é o `JoinRequestIssue` que o backend calcula com dado
 * local; a frase é montada aqui, no idioma do usuário (direção do T2).
 */
describe('MyTeamsPage — aba "meus pedidos"', () => {
  beforeEach(() => {
    logarComo()
    vi.mocked(listsApi.mine).mockResolvedValue(pagina([]))
    vi.mocked(listsApi.myRequests).mockResolvedValue([pedido()])
    vi.mocked(notificationsApi.unreadCount).mockResolvedValue(0)
  })

  /** Abre a página já na aba de pedidos (é o destino do link da notificação). */
  async function abrirPedidos() {
    renderizar(<MyTeamsPage />, { rota: '/account/teams?tab=requests' })
    return screen.findByText(/com Cavaleiro de Antica/i)
  }

  it('sem problema aparente, nao inventa aviso', async () => {
    await abrirPedidos()

    // Ausência de aviso não é promessa de aprovação (perda de Premium não é
    // detectável com dado local) — mas inventar aviso é pior.
    expect(conteudo().queryByText(/talvez não seja aprovado/i)).not.toBeInTheDocument()
  })

  it('composicao sem vaga: diz a vocacao e o que fazer', async () => {
    vi.mocked(listsApi.myRequests).mockResolvedValue([
      pedido({ issue: 'VOCATION_NOT_IN_COMPOSITION', characterVocation: 'KNIGHT' }),
    ])
    await abrirPedidos()

    // A vocação **na frase** é o que torna o aviso acionável: sem ela, a pessoa vai
    // conferir qual personagem usou antes de entender o problema.
    expect(await conteudo().findByText(/não tem mais vaga para Knight/i)).toBeInTheDocument()
    expect(conteudo().getByText(/use outro personagem/i)).toBeInTheDocument()
  })

  it('composicao sem vaga e vocacao desconhecida: frase generica, nao "undefined"', async () => {
    vi.mocked(listsApi.myRequests).mockResolvedValue([
      pedido({ issue: 'VOCATION_NOT_IN_COMPOSITION', characterVocation: null }),
    ])
    await abrirPedidos()

    expect(await conteudo().findByText(/vaga para a vocação do seu personagem/i)).toBeInTheDocument()
    expect(conteudo().queryByText(/undefined|NONE/)).not.toBeInTheDocument()
  })

  it('level abaixo do minimo continua mostrando os numeros', async () => {
    vi.mocked(listsApi.myRequests).mockResolvedValue([
      pedido({ issue: 'BELOW_MINIMUM_LEVEL', minimumLevel: 400, characterLevel: 300 }),
    ])
    await abrirPedidos()

    // Regressão do P4: os dois números juntos é o que explica o aviso.
    expect(await conteudo().findByText(/exige level 400 e seu personagem tem 300/i)).toBeInTheDocument()
  })

  it('world diferente continua nomeando o world do time', async () => {
    vi.mocked(listsApi.myRequests).mockResolvedValue([
      pedido({ issue: 'WORLD_MISMATCH', world: 'Bona' }),
    ])
    await abrirPedidos()

    expect(await conteudo().findByText(/não é mais do world Bona/i)).toBeInTheDocument()
  })

  it('cancelar o pedido chama o servico com a membership', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    vi.mocked(listsApi.cancelMyRequest).mockResolvedValue(undefined)
    await abrirPedidos()

    await userEvent.click(conteudo().getByRole('button', { name: /cancelar pedido/i }))

    await waitFor(() => expect(listsApi.cancelMyRequest).toHaveBeenCalledWith(1))
  })

  it('recusar a confirmacao nao cancela nada', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(false)
    await abrirPedidos()

    await userEvent.click(conteudo().getByRole('button', { name: /cancelar pedido/i }))

    // Cancelar pedido é irreversível do ponto de vista da pessoa (perde a posição na
    // fila do dono): o "não" tem que ser respeitado.
    expect(listsApi.cancelMyRequest).not.toHaveBeenCalled()
  })

  it('pedido ja recusado nao oferece cancelar', async () => {
    vi.mocked(listsApi.myRequests).mockResolvedValue([pedido({ status: 'REJECTED' })])
    await abrirPedidos()

    expect(conteudo().queryByRole('button', { name: /cancelar pedido/i })).not.toBeInTheDocument()
  })

  it('falha ao carregar os pedidos mostra erro com "tentar de novo"', async () => {
    vi.mocked(listsApi.myRequests).mockRejectedValue(new Error('rede caiu'))
    renderizar(<MyTeamsPage />, { rota: '/account/teams?tab=requests' })

    // "Nenhum pedido" seria mentira: a lista nem carregou, e quem acredita nela
    // pede de novo — tomando "já existe um pedido pendente".
    expect(await conteudo().findByRole('button', { name: /tentar de novo/i })).toBeInTheDocument()
  })
})

/**
 * Guarda de exaustividade: o `issueText` da página tem um `case` por código, e o
 * `tsc` reprova quando o backend ganha um valor novo. Este teste é o lembrete de que
 * a lista abaixo precisa crescer junto — e falha se alguém adicionar o código no
 * TypeScript e esquecer a frase.
 */
describe('todo JoinRequestIssue tem frase', () => {
  const CODIGOS: JoinRequestIssue[] = [
    'BELOW_MINIMUM_LEVEL',
    'WORLD_MISMATCH',
    'VOCATION_NOT_IN_COMPOSITION',
  ]

  beforeEach(() => {
    logarComo()
    vi.mocked(listsApi.mine).mockResolvedValue(pagina([]))
    vi.mocked(notificationsApi.unreadCount).mockResolvedValue(0)
  })

  it.each(CODIGOS)('%s vira texto, e nao chave crua', async (issue) => {
    const vocacao: Vocation = 'KNIGHT'
    vi.mocked(listsApi.myRequests).mockResolvedValue([
      pedido({ issue, minimumLevel: 400, characterLevel: 300, characterVocation: vocacao }),
    ])
    renderizar(<MyTeamsPage />, { rota: '/account/teams?tab=requests' })

    const aviso = await conteudo().findByText(/talvez não seja aprovado/i)
    const texto = aviso.textContent ?? ''
    // Chave crua na tela (`myRequests.issueX`) é o sintoma de tradução faltando.
    expect(texto).not.toContain('myRequests.')
    expect(texto.length).toBeGreaterThan(30)
  })
})
