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
import { conteudo, detalheDeTime, logarComo, personagem, renderizar } from '../test/renderizar'

vi.mock('../services/listsApi')
vi.mock('../services/charactersApi')
vi.mock('../services/chatApi')
vi.mock('../services/chatSocket')
vi.mock('../services/notificationsApi')
vi.mock('../services/authService')

const DONO = 1

function recusa(message: string) {
  return new AxiosError('falhou', 'ERR_BAD_REQUEST', { headers: new AxiosHeaders() }, null, {
    status: 422,
    statusText: '',
    headers: {},
    config: { headers: new AxiosHeaders() },
    data: { status: 422, message },
  })
}

/**
 * **Editar o time** (item T17) — o fluxo de escrita mais perigoso da tela, porque o
 * `PATCH` manda o **conjunto completo**: campo vazio **limpa** o valor no backend, não é
 * "não mexer". Errar aqui apaga a descrição de um time sem ninguém pedir.
 *
 * O outro lado do mesmo contrato: o formulário nasce **preenchido** com o que existe. Um
 * campo que não carrega o valor atual vira, no primeiro "salvar", um campo apagado.
 */
describe('TeamDetailPage — editar o time', () => {
  const TIME = detalheDeTime(
    {
      id: 7,
      name: 'Cyclops da madrugada',
      minimumLevel: 150,
      pricePerSlot: 5000,
      huntSchedule: 'Seg–Sex 20h',
      description: 'Trazer 200 SDs.',
    },
    { ownerId: DONO, contact: 'discord: dono#1234' },
  )

  beforeEach(() => {
    logarComo({ id: DONO })
    vi.mocked(listsApi.get).mockResolvedValue(TIME)
    vi.mocked(listsApi.update).mockResolvedValue(TIME)
    vi.mocked(listsApi.pendingRequests).mockResolvedValue([])
    vi.mocked(charactersApi.mine).mockResolvedValue([
      personagem({ id: 10, name: 'Dono de Antica', world: 'Antica' }),
    ])
    vi.mocked(chatApi.history).mockResolvedValue({
      content: [],
      number: 0,
      totalElements: 0,
      totalPages: 0,
    })
    vi.mocked(notificationsApi.unreadCount).mockResolvedValue(0)
  })

  /** Abre `/teams/7` como dono e expande o cartão de edição. */
  async function abrirEditor() {
    renderizar(
      <Routes>
        <Route path="/teams/:id" element={<TeamDetailPage />} />
      </Routes>,
      { rota: '/teams/7' },
    )
    await userEvent.click(await conteudo().findByRole('button', { name: /^editar time$/i }))
    return conteudo().getByRole('button', { name: /salvar/i })
  }

  /** O corpo do último PATCH (`listsApi.update(listId, corpo)`). */
  function ultimoPatch() {
    const chamadas = vi.mocked(listsApi.update).mock.calls
    const [listId, corpo] = chamadas[chamadas.length - 1]
    expect(listId).toBe(7)
    return corpo
  }

  it('o formulario nasce com os valores atuais do time', async () => {
    await abrirEditor()

    // Campo vazio num formulário que salva o conjunto completo é campo apagado no
    // primeiro "salvar" — então carregar o valor atual **é** parte da regra.
    expect(conteudo().getByLabelText(/título do time/i)).toHaveValue('Cyclops da madrugada')
    expect(conteudo().getByLabelText(/level mínimo/i)).toHaveValue(150)
    expect(conteudo().getByLabelText(/preço por vaga/i)).toHaveValue(5000)
    expect(conteudo().getByLabelText(/horário/i)).toHaveValue('Seg–Sex 20h')
    expect(conteudo().getByLabelText(/descrição/i)).toHaveValue('Trazer 200 SDs.')
    // O contato vem do `detail.contact`, não do summary: é dado privado, e o backend só o
    // manda para quem pode ver.
    expect(conteudo().getByLabelText(/contato/i)).toHaveValue('discord: dono#1234')
  })

  it('salvar sem mexer em nada reenvia os mesmos valores', async () => {
    const salvar = await abrirEditor()

    await userEvent.click(salvar)

    await waitFor(() => expect(listsApi.update).toHaveBeenCalled())
    // O caso mais comum de todos (abriu para editar e desistiu) não pode alterar nada.
    expect(ultimoPatch()).toEqual({
      name: 'Cyclops da madrugada',
      minimumLevel: 150,
      pricePerSlot: 5000,
      huntSchedule: 'Seg–Sex 20h',
      description: 'Trazer 200 SDs.',
      contact: 'discord: dono#1234',
    })
  })

  it('limpar a descricao manda null, e nao string vazia', async () => {
    const salvar = await abrirEditor()

    await userEvent.clear(conteudo().getByLabelText(/descrição/i))
    await userEvent.click(salvar)

    await waitFor(() => expect(listsApi.update).toHaveBeenCalled())
    // `""` e `null` são a mesma intenção aqui (limpar), mas só `null` é o contrato — o
    // backend normaliza, e mandar string em branco pela rede é sujeira que engana quem lê
    // o payload numa investigação.
    expect(ultimoPatch().description).toBeNull()
  })

  it('limpar level e preco manda null, nao NaN', async () => {
    const salvar = await abrirEditor()

    await userEvent.clear(conteudo().getByLabelText(/level mínimo/i))
    await userEvent.clear(conteudo().getByLabelText(/preço por vaga/i))
    await userEvent.click(salvar)

    await waitFor(() => expect(listsApi.update).toHaveBeenCalled())
    // `Number('')` é `0`, e `Number(undefined)` é `NaN`: os dois virariam requisito de
    // level onde a pessoa quis dizer "sem requisito".
    expect(ultimoPatch().minimumLevel).toBeNull()
    expect(ultimoPatch().pricePerSlot).toBeNull()
  })

  it('level e preco novos vao como numero', async () => {
    const salvar = await abrirEditor()

    await userEvent.clear(conteudo().getByLabelText(/level mínimo/i))
    await userEvent.type(conteudo().getByLabelText(/level mínimo/i), '400')
    await userEvent.click(salvar)

    await waitFor(() => expect(listsApi.update).toHaveBeenCalled())
    expect(ultimoPatch().minimumLevel).toBe(400)
  })

  it('nome vazio manda null (o backend volta a usar o nome da criatura)', async () => {
    const salvar = await abrirEditor()

    await userEvent.clear(conteudo().getByLabelText(/título do time/i))
    await userEvent.click(salvar)

    await waitFor(() => expect(listsApi.update).toHaveBeenCalled())
    // Contrato do P14: título vazio volta a assumir o nome da criatura-alvo. String vazia
    // deixaria o time **sem** nome.
    expect(ultimoPatch().name).toBeNull()
  })

  it('sucesso fecha o formulario', async () => {
    const salvar = await abrirEditor()

    await userEvent.click(salvar)

    await waitFor(() =>
      expect(conteudo().queryByRole('button', { name: /salvar/i })).not.toBeInTheDocument(),
    )
    expect(conteudo().getByRole('button', { name: /^editar time$/i })).toBeInTheDocument()
  })

  it('recusa do backend aparece e o formulario continua aberto', async () => {
    vi.mocked(listsApi.update).mockRejectedValue(
      recusa('Você não pode exigir level 900: seu personagem no time tem level 500'),
    )
    const salvar = await abrirEditor()

    await userEvent.clear(conteudo().getByLabelText(/level mínimo/i))
    await userEvent.type(conteudo().getByLabelText(/level mínimo/i), '900')
    await userEvent.click(salvar)

    // Fechar o formulário numa recusa jogaria fora o que a pessoa acabou de digitar.
    expect(await conteudo().findByText(/seu personagem no time tem level 500/i)).toBeInTheDocument()
    expect(conteudo().getByRole('button', { name: /salvar/i })).toBeInTheDocument()
  })

  it('quem nao e dono nao ve o cartao de edicao', async () => {
    logarComo({ id: 999 })
    renderizar(
      <Routes>
        <Route path="/teams/:id" element={<TeamDetailPage />} />
      </Routes>,
      { rota: '/teams/7' },
    )

    await screen.findByRole('heading', { level: 1, name: /demon/i })
    // O backend recusa com 403, mas oferecer o botão é convidar ao erro.
    expect(conteudo().queryByRole('button', { name: /^editar time$/i })).not.toBeInTheDocument()
  })
})
