import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AxiosError, AxiosHeaders } from 'axios'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ChatPanel } from './ChatPanel'
import { chatApi } from '../services/chatApi'
import { subscribeToChat } from '../services/chatSocket'
import { logarComo, renderizar } from '../test/renderizar'
import type { ChatMessageResponse } from '../types/api'

vi.mock('../services/chatApi')
// O socket não é assunto aqui: ele entrega o que chega **depois**, e o que estes testes
// cobrem é o histórico e o envio. Sem o mock, o `stompjs` tentaria abrir uma conexão real.
vi.mock('../services/chatSocket')

function mensagem(id: number, characterName: string, content: string): ChatMessageResponse {
  return {
    id,
    listId: 7,
    senderId: 1,
    senderDisplayName: 'Jogador',
    characterId: 10,
    characterName,
    content,
    sentAt: new Date(2026, 6, 30, 12, id).toISOString(),
  }
}

/** O histórico vem do backend em ordem **decrescente** (mais nova primeiro). */
function historico(...mensagens: ChatMessageResponse[]) {
  return { content: mensagens, number: 0, totalElements: mensagens.length, totalPages: 1 }
}

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
 * O chat (item T17). Duas coisas só existem aqui, e uma delas **já quebrou uma vez**:
 *
 * <ul>
 *   <li>mensagem vazia não é enviada (o backend recusaria, mas o clique não pode sair);</li>
 *   <li>histórico que <b>falhou</b> não é histórico <b>vazio</b> — o painel dizia "nenhuma
 *       mensagem ainda" para um time que tinha conversado a semana toda.</li>
 * </ul>
 */
describe('ChatPanel', () => {
  beforeEach(() => {
    logarComo()
    vi.mocked(chatApi.history).mockResolvedValue(historico())
    vi.mocked(chatApi.send).mockResolvedValue(mensagem(1, 'Sir Exiva', 'oi'))
    // O `useChat` chama a função devolvida no cleanup do efeito: sem isto, o automock
    // devolve `undefined` e o React estoura ao desmontar.
    vi.mocked(subscribeToChat).mockReturnValue(() => {})
  })

  const abrir = () => renderizar(<ChatPanel listId={7} actingCharacterId={10} />)

  it('mostra o historico em ordem cronologica', async () => {
    // O backend manda decrescente; a tela lê de cima para baixo.
    vi.mocked(chatApi.history).mockResolvedValue(
      historico(mensagem(2, 'Druida', 'segunda'), mensagem(1, 'Sir Exiva', 'primeira')),
    )
    abrir()

    await screen.findByText('primeira')
    const textos = screen.getAllByText(/primeira|segunda/).map((e) => e.textContent)
    expect(textos).toEqual(['primeira', 'segunda'])
  })

  it('mensagem vazia ou so espaco nao e enviada', async () => {
    abrir()
    await screen.findByText(/nenhuma mensagem ainda/i)

    await userEvent.click(screen.getByRole('button', { name: /enviar/i }))
    await userEvent.type(screen.getByPlaceholderText(/mensagem/i), '   ')
    await userEvent.click(screen.getByRole('button', { name: /enviar/i }))

    // Clique que sai com nada gasta uma requisição e volta com 400 — e o campo ainda
    // parece que enviou algo.
    expect(chatApi.send).not.toHaveBeenCalled()
  })

  it('envia o texto aparado com o personagem do time, e limpa o campo', async () => {
    abrir()
    const campo = await screen.findByPlaceholderText(/mensagem/i)

    await userEvent.type(campo, '  bora hoje 20h  ')
    await userEvent.click(screen.getByRole('button', { name: /enviar/i }))

    // O `actingCharacterId` é o personagem **deste** time: mandar outro é 403 no backend.
    await waitFor(() => expect(chatApi.send).toHaveBeenCalledWith(7, 10, 'bora hoje 20h'))
    expect(campo).toHaveValue('')
  })

  it('recusa do backend aparece e o texto nao e perdido', async () => {
    vi.mocked(chatApi.send).mockRejectedValue(
      recusa('Você está enviando mensagens rápido demais; aguarde um pouco'),
    )
    abrir()
    const campo = await screen.findByPlaceholderText(/mensagem/i)

    await userEvent.type(campo, 'muitas mensagens')
    await userEvent.click(screen.getByRole('button', { name: /enviar/i }))

    expect(await screen.findByText(/rápido demais/i)).toBeInTheDocument()
    // Perder o texto numa recusa de rate limit obriga a redigitar para tentar de novo.
    expect(campo).toHaveValue('muitas mensagens')
  })

  it('historico que falhou nao vira "nenhuma mensagem ainda"', async () => {
    vi.mocked(chatApi.history).mockRejectedValue(new Error('rede caiu'))
    abrir()

    // A regressão que este teste guarda: o painel afirmava que o time nunca conversou.
    expect(await screen.findByText(/não foi possível carregar o histórico/i)).toBeInTheDocument()
    expect(screen.queryByText(/nenhuma mensagem ainda/i)).not.toBeInTheDocument()
  })

  it('o "tentar de novo" do historico pede de novo', async () => {
    vi.mocked(chatApi.history).mockRejectedValueOnce(new Error('rede caiu'))
    abrir()
    await screen.findByText(/não foi possível carregar o histórico/i)
    vi.mocked(chatApi.history).mockResolvedValue(historico(mensagem(1, 'Sir Exiva', 'voltou')))

    await userEvent.click(screen.getByRole('button', { name: /tentar de novo/i }))

    expect(await screen.findByText('voltou')).toBeInTheDocument()
    expect(screen.queryByText(/não foi possível carregar o histórico/i)).not.toBeInTheDocument()
  })

  it('enviar continua possivel mesmo com o historico falhando', async () => {
    vi.mocked(chatApi.history).mockRejectedValue(new Error('rede caiu'))
    abrir()
    await screen.findByText(/não foi possível carregar o histórico/i)

    await userEvent.type(screen.getByPlaceholderText(/mensagem/i), 'estou aqui')
    await userEvent.click(screen.getByRole('button', { name: /enviar/i }))

    // O histórico é leitura; a escrita não depende dele. Travar o envio por causa de uma
    // falha de leitura seria transformar um problema em dois.
    await waitFor(() => expect(chatApi.send).toHaveBeenCalledWith(7, 10, 'estou aqui'))
  })
})
