import { Route, Routes } from 'react-router'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AxiosError, AxiosHeaders } from 'axios'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { TeamDetailPage } from './TeamDetailPage'
import { listsApi } from '../services/listsApi'
import { charactersApi } from '../services/charactersApi'
import { chatApi } from '../services/chatApi'
import { notificationsApi } from '../services/notificationsApi'
import { useAuthStore } from '../store/authStore'
import {
  conteudo,
  detalheDeTime,
  logarComo,
  personagem,
  renderizar,
} from '../test/renderizar'

vi.mock('../services/listsApi')
vi.mock('../services/charactersApi')
vi.mock('../services/chatApi')
vi.mock('../services/chatSocket')
vi.mock('../services/notificationsApi')
vi.mock('../services/authService')

function recusaDaApi(status: number, message: string) {
  return new AxiosError('falhou', 'ERR_BAD_REQUEST', { headers: new AxiosHeaders() }, null, {
    status,
    statusText: '',
    headers: {},
    config: { headers: new AxiosHeaders() },
    data: { status, message },
  })
}

/**
 * O fluxo de **pedir para entrar** — o outro lado da escrita que a suíte de
 * navegação não alcança (ela abre a página como visitante e não envia nada).
 *
 * A regra que estes testes prendem: só personagem do **mesmo world** do time é
 * oferecido, e a recusa do backend chega à tela em vez de virar um clique que
 * "não fez nada".
 */
describe('TeamDetailPage — pedir para entrar', () => {
  const TIME = detalheDeTime({ id: 7, world: 'Antica', shareCode: 'ABC123' })

  beforeEach(() => {
    logarComo({ id: 1 })
    vi.mocked(listsApi.get).mockResolvedValue(TIME)
    vi.mocked(listsApi.join).mockResolvedValue(TIME)
    vi.mocked(charactersApi.mine).mockResolvedValue([
      personagem({ id: 10, name: 'Druida de Antica', world: 'Antica' }),
      personagem({ id: 11, name: 'Bruxo de Bona', world: 'Bona' }),
    ])
    vi.mocked(chatApi.history).mockResolvedValue({
      content: [],
      number: 0,
      totalElements: 0,
      totalPages: 0,
    })
    vi.mocked(notificationsApi.unreadCount).mockResolvedValue(0)
  })

  /**
   * Abre `/teams/7` e espera o time carregar.
   *
   * Espera pelo **nome da criatura-alvo** (o `h1` da página), não pelo cartão de
   * entrada: o cartão muda de forma conforme o caso (visitante, time cheio, sem
   * personagem no world), e esperar por ele deixaria o teste de cada caso
   * esperando justamente o que ele quer provar que não existe.
   */
  async function abrirTime() {
    renderizar(
      <Routes>
        <Route path="/teams/:id" element={<TeamDetailPage />} />
      </Routes>,
      { rota: '/teams/7' },
    )
    await screen.findByRole('heading', { level: 1, name: /demon/i })
  }

  it('so oferece personagem do mesmo world do time', async () => {
    await abrirTime()

    // O de Bona não pode entrar (regra: todos do mesmo world). Oferecê-lo seria
    // convidar a pessoa a tomar um 422 que a tela podia ter evitado.
    expect(screen.getByRole('option', { name: 'Druida de Antica' })).toBeInTheDocument()
    expect(screen.queryByRole('option', { name: 'Bruxo de Bona' })).not.toBeInTheDocument()
  })

  it('sem personagem no world do time, explica em vez de oferecer campo vazio', async () => {
    vi.mocked(charactersApi.mine).mockResolvedValue([
      personagem({ id: 11, name: 'Bruxo de Bona', world: 'Bona' }),
    ])
    await abrirTime()

    expect(await screen.findByText(/não tem personagem verificado no mundo antica/i)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /pedir para entrar/i })).not.toBeInTheDocument()
  })

  it('o botao so libera depois de escolher o personagem', async () => {
    await abrirTime()

    const botao = screen.getByRole('button', { name: /pedir para entrar/i })
    expect(botao).toBeDisabled()

    await userEvent.selectOptions(screen.getByLabelText(/^personagem$/i), '10')

    expect(botao).toBeEnabled()
  })

  it('envia o shareCode do time e o personagem escolhido, e confirma na tela', async () => {
    await abrirTime()

    await userEvent.selectOptions(screen.getByLabelText(/^personagem$/i), '10')
    await userEvent.click(screen.getByRole('button', { name: /pedir para entrar/i }))

    await waitFor(() => expect(listsApi.join).toHaveBeenCalledWith('ABC123', 10))
    // Sem a confirmação, o pedido enviado é indistinguível do clique perdido —
    // e a pessoa clica de novo, tomando "já existe um pedido pendente".
    expect(await screen.findByText(/pedido enviado/i)).toBeInTheDocument()
  })

  it('recusa do backend aparece com a mensagem do backend', async () => {
    vi.mocked(listsApi.join).mockRejectedValue(
      recusaDaApi(422, 'Seu personagem está abaixo do level mínimo (400)'),
    )
    await abrirTime()

    await userEvent.selectOptions(screen.getByLabelText(/^personagem$/i), '10')
    await userEvent.click(screen.getByRole('button', { name: /pedir para entrar/i }))

    expect(await screen.findByText(/abaixo do level mínimo \(400\)/i)).toBeInTheDocument()
    expect(screen.queryByText(/pedido enviado/i)).not.toBeInTheDocument()
  })

  it('time cheio nao mostra formulario de entrada', async () => {
    vi.mocked(listsApi.get).mockResolvedValue(
      detalheDeTime({ id: 7, hasOpenSlots: false, memberCount: 5 }),
    )
    await abrirTime()

    expect(screen.getByText(/este time está cheio/i)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /pedir para entrar/i })).not.toBeInTheDocument()
  })

  it('visitante sem sessao ve o convite para entrar na conta, nao o formulario', async () => {
    // Sem sessão: o `setup.ts` já limpa o store depois de cada teste, aqui é
    // desfazer o `logarComo` do `beforeEach`.
    useAuthStore.setState({ accessToken: null, refreshToken: null, user: null })
    await abrirTime()

    expect(conteudo().getByRole('link', { name: /entre/i })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /pedir para entrar/i })).not.toBeInTheDocument()
  })

  it('falha ao listar personagens nao vira "voce nao tem personagem neste world"', async () => {
    vi.mocked(charactersApi.mine).mockRejectedValue(new Error('rede caiu'))
    await abrirTime()

    // A diferença importa: "não tem personagem" manda a pessoa criar um claim
    // que já existe; "falhou, tente de novo" manda ela tentar de novo.
    expect(await screen.findByRole('button', { name: /tentar de novo/i })).toBeInTheDocument()
    expect(screen.queryByText(/não tem personagem verificado no mundo/i)).not.toBeInTheDocument()
  })
})
