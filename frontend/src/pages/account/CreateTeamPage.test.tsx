import { fireEvent, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AxiosError, AxiosHeaders } from 'axios'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { CreateTeamPage } from './CreateTeamPage'
import { listsApi } from '../../services/listsApi'
import { charactersApi } from '../../services/charactersApi'
import { creaturesApi } from '../../services/creaturesApi'
import { notificationsApi } from '../../services/notificationsApi'
import {
  conteudo,
  detalheDeTime,
  logarComo,
  personagem,
  renderizar,
} from '../../test/renderizar'
import type { ListDetailResponse } from '../../types/api'

// A camada mockada é `services/`, não o axios: é o contrato que os componentes
// realmente usam, e mockar axios amarraria o teste ao transporte (ver TESTS.md §6).
vi.mock('../../services/listsApi')
vi.mock('../../services/charactersApi')
vi.mock('../../services/creaturesApi')
vi.mock('../../services/notificationsApi')
vi.mock('../../services/authService')

const navegou = vi.fn()
vi.mock('react-router', async (original) => ({
  ...(await original<typeof import('react-router')>()),
  useNavigate: () => navegou,
}))

/** Erro do backend no envelope padronizado (`ApiErrorResponse`). */
function erroDaApi(status: number, corpo: Record<string, unknown>) {
  return new AxiosError('falhou', 'ERR_BAD_REQUEST', { headers: new AxiosHeaders() }, null, {
    status,
    statusText: '',
    headers: {},
    config: { headers: new AxiosHeaders() },
    data: corpo,
  })
}

describe('CreateTeamPage', () => {
  beforeEach(() => {
    logarComo()
    vi.mocked(charactersApi.mine).mockResolvedValue([
      personagem({ id: 10, name: 'Druida de Antica', world: 'Antica' }),
      personagem({ id: 11, name: 'Cavaleiro de Bona', world: 'Bona', vocation: 'Elite Knight' }),
    ])
    vi.mocked(creaturesApi.list).mockResolvedValue([
      { id: 1, name: 'Demon', imageUrl: null, difficulty: 4 },
    ])
    vi.mocked(listsApi.mine).mockResolvedValue([])
    vi.mocked(listsApi.create).mockResolvedValue(detalheDeTime())
    // A NavBar acompanha toda página: sem isto, o sino de notificações quebra a
    // query e polui a saída de todos os testes.
    vi.mocked(notificationsApi.unreadCount).mockResolvedValue(0)
  })

  /**
   * Abre a tela e espera os **dados** chegarem, não só o formulário aparecer.
   *
   * O formulário renderiza antes das queries responderem (personagem e criatura
   * ainda `undefined`), então esperar pelo botão não basta: os `selectOptions`
   * seguintes não achariam opção nenhuma. Esperar por uma opção real é o sinal
   * honesto de "a tela está usável".
   */
  async function abrirFormulario() {
    renderizar(<CreateTeamPage />)
    await screen.findByRole('option', { name: /Druida de Antica/ })
    return conteudo().getByRole('button', { name: /criar time/i })
  }

  it('sem personagem escolhido, avisa e nao chama o servico', async () => {
    await abrirFormulario()

    // Por que `submit` no formulário e não clique no botão: com o `required` do
    // HTML, o navegador (e o jsdom) barram o envio antes de o React ver o
    // evento — o guarda do componente existe para o caso em que o `value` do
    // select **não** está vazio e ainda assim não aponta para personagem
    // nenhum: id que sumiu da lista entre escolher e enviar (personagem
    // apagado em outra aba, e um refetch depois). Sem o guarda, esse caminho
    // manda `characterId: NaN` para o backend.
    fireEvent.submit(conteudo().getByRole('button', { name: /criar time/i }).closest('form')!)

    expect(await conteudo().findByText(/escolha um personagem verificado/i)).toBeInTheDocument()
    expect(listsApi.create).not.toHaveBeenCalled()
  })

  it('o world do time vem do personagem escolhido, nao de um campo', async () => {
    await abrirFormulario()

    await userEvent.selectOptions(
      screen.getByLabelText(/seu personagem/i),
      screen.getByRole('option', { name: /Cavaleiro de Bona/ }),
    )

    // Regra do produto: todos do mesmo world. A tela precisa dizer qual, senão
    // quem tem personagem em dois mundos cria o time no errado.
    expect(screen.getByText(/o time será do mundo bona/i)).toBeInTheDocument()
  })

  it('campos opcionais em branco viram null no pedido, e nao string vazia', async () => {
    await abrirFormulario()

    await userEvent.selectOptions(screen.getByLabelText(/seu personagem/i), '10')
    await userEvent.selectOptions(screen.getByLabelText(/criatura-alvo/i), '1')
    await userEvent.click(conteudo().getByRole('button', { name: /criar time/i }))

    await waitFor(() => expect(listsApi.create).toHaveBeenCalledTimes(1))
    expect(vi.mocked(listsApi.create).mock.calls[0][0]).toEqual({
      world: 'Antica',
      targetCreatureId: 1,
      joinPolicy: 'MANUAL_APPROVAL',
      characterId: 10,
      minimumLevel: null,
      pricePerSlot: null,
      description: null,
      huntSchedule: null,
      contact: null,
      // Tudo em "qualquer" = time sem composição. Mandar cinco `null` é o
      // contrato: o backend normaliza para "sem composição".
      slots: [null, null, null, null, null],
    })
  })

  it('preenchido, manda numero em level e preco e texto aparado', async () => {
    await abrirFormulario()

    await userEvent.selectOptions(screen.getByLabelText(/seu personagem/i), '10')
    await userEvent.selectOptions(screen.getByLabelText(/criatura-alvo/i), '1')
    await userEvent.type(screen.getByLabelText(/level mínimo/i), '250')
    await userEvent.type(screen.getByLabelText(/preço por vaga/i), '5000')
    await userEvent.type(screen.getByLabelText(/^contato$/i), '  discord: eu#1  ')
    await userEvent.click(conteudo().getByRole('button', { name: /criar time/i }))

    await waitFor(() => expect(listsApi.create).toHaveBeenCalled())
    const enviado = vi.mocked(listsApi.create).mock.calls[0][0]
    // Número, não string: `"250"` passaria pelo TypeScript de quem chama e
    // chegaria ao backend como texto.
    expect(enviado.minimumLevel).toBe(250)
    expect(enviado.pricePerSlot).toBe(5000)
    expect(enviado.contact).toBe('discord: eu#1')
  })

  it('escolher vocacao numa vaga entra na composicao enviada', async () => {
    await abrirFormulario()

    await userEvent.selectOptions(screen.getByLabelText(/seu personagem/i), '10')
    await userEvent.selectOptions(screen.getByLabelText(/criatura-alvo/i), '1')
    // A vaga 1 fica "Knight"; as outras quatro seguem em "qualquer".
    const vagas = screen.getAllByRole('combobox', { name: '' })
    await userEvent.selectOptions(vagas[0], 'KNIGHT')
    await userEvent.click(conteudo().getByRole('button', { name: /criar time/i }))

    await waitFor(() => expect(listsApi.create).toHaveBeenCalled())
    expect(vi.mocked(listsApi.create).mock.calls[0][0].slots).toEqual([
      'KNIGHT',
      null,
      null,
      null,
      null,
    ])
  })

  it('sucesso leva para a pagina do time criado', async () => {
    const criado: ListDetailResponse = detalheDeTime({ id: 42 })
    vi.mocked(listsApi.create).mockResolvedValue(criado)
    await abrirFormulario()

    await userEvent.selectOptions(screen.getByLabelText(/seu personagem/i), '10')
    await userEvent.selectOptions(screen.getByLabelText(/criatura-alvo/i), '1')
    await userEvent.click(conteudo().getByRole('button', { name: /criar time/i }))

    await waitFor(() => expect(navegou).toHaveBeenCalledWith('/teams/42'))
  })

  it('recusa do backend aparece na tela com a mensagem do backend', async () => {
    vi.mocked(listsApi.create).mockRejectedValue(
      erroDaApi(422, { status: 422, message: 'Você não cabe na composição (falta DRUID)' }),
    )
    await abrirFormulario()

    await userEvent.selectOptions(screen.getByLabelText(/seu personagem/i), '10')
    await userEvent.selectOptions(screen.getByLabelText(/criatura-alvo/i), '1')
    await userEvent.click(conteudo().getByRole('button', { name: /criar time/i }))

    // Sem isto, a recusa do servidor virava um clique que "não fez nada".
    expect(await screen.findByText(/falta DRUID/)).toBeInTheDocument()
  })

  it('erro de campo do backend ganha prioridade sobre a mensagem geral', async () => {
    vi.mocked(listsApi.create).mockRejectedValue(
      erroDaApi(400, {
        status: 400,
        message: 'Requisição inválida',
        fieldErrors: { minimumLevel: 'Level mínimo deve ser positivo' },
      }),
    )
    await abrirFormulario()

    await userEvent.selectOptions(screen.getByLabelText(/seu personagem/i), '10')
    await userEvent.selectOptions(screen.getByLabelText(/criatura-alvo/i), '1')
    await userEvent.click(conteudo().getByRole('button', { name: /criar time/i }))

    // "Requisição inválida" não diz o que corrigir; o erro do campo diz.
    expect(await screen.findByText(/level mínimo deve ser positivo/i)).toBeInTheDocument()
  })

  describe('limite do plano free', () => {
    const ativo = (id: number) => detalheDeTime({ id }).summary

    it('no limite, o botao fica desabilitado e a tela explica', async () => {
      vi.mocked(listsApi.mine).mockResolvedValue([ativo(1), ativo(2), ativo(3)])
      const botao = await abrirFormulario()

      await waitFor(() => expect(botao).toBeDisabled())
      expect(conteudo().getByText(/atingiu o limite de 3 times ativos/i)).toBeInTheDocument()
      expect(conteudo().getByText(/plano free: 3\/3/i)).toBeInTheDocument()
    })

    it('time encerrado nao conta para o limite', async () => {
      vi.mocked(listsApi.mine).mockResolvedValue([
        ativo(1),
        ativo(2),
        detalheDeTime({ id: 3, status: 'CLOSED' }).summary,
      ])
      const botao = await abrirFormulario()

      // Encerrar um time é o caminho que o próprio aviso de limite sugere: se
      // CLOSED contasse, quem seguiu o conselho continuaria travado.
      expect(await conteudo().findByText(/plano free: 2\/3/i)).toBeInTheDocument()
      expect(botao).toBeEnabled()
    })

    it('conta premium nao ve o aviso de limite', async () => {
      logarComo({ plan: 'PREMIUM' })
      vi.mocked(listsApi.mine).mockResolvedValue([ativo(1), ativo(2), ativo(3), ativo(4)])
      const botao = await abrirFormulario()

      expect(botao).toBeEnabled()
      expect(conteudo().queryByText(/plano free/i)).not.toBeInTheDocument()
    })
  })

  it('falha ao listar personagens mostra erro com "tentar de novo", nao seletor vazio', async () => {
    vi.mocked(charactersApi.mine).mockRejectedValue(new Error('rede caiu'))
    renderizar(<CreateTeamPage />)

    // O modo de falha antigo: formulário normal, seletor vazio, e a pessoa
    // concluindo que não tem personagem verificado.
    expect(await screen.findByRole('button', { name: /tentar de novo/i })).toBeInTheDocument()
    expect(screen.queryByLabelText(/seu personagem/i)).not.toBeInTheDocument()
  })

  it('sem personagem nenhum, manda cadastrar em vez de mostrar formulario', async () => {
    vi.mocked(charactersApi.mine).mockResolvedValue([])
    renderizar(<CreateTeamPage />)

    expect(await screen.findByText(/precisa de um personagem verificado/i)).toBeInTheDocument()
    expect(conteudo().queryByRole('button', { name: /criar time/i })).not.toBeInTheDocument()
  })
})
