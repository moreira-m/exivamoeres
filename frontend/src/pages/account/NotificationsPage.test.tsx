import { beforeEach, describe, expect, it, vi } from 'vitest'
import { NotificationsPage } from './NotificationsPage'
import { notificationsApi } from '../../services/notificationsApi'
import { conteudo, logarComo, renderizar } from '../../test/renderizar'
import type { NotificationResponse, NotificationType } from '../../types/api'

vi.mock('../../services/notificationsApi')
vi.mock('../../services/authService')

/** Todos os tipos que o backend pode mandar (`domain/NotificationType.java`). */
const TODOS_OS_TIPOS: NotificationType[] = [
  'JOIN_REQUEST_RECEIVED',
  'JOIN_REQUEST_APPROVED',
  'JOIN_REQUEST_REJECTED',
  'KICKED_FROM_TEAM',
  'TEAM_DELETED',
  'MEMBER_LEFT',
  'TEAM_SCHEDULE_CHANGED',
  'TEAM_MINIMUM_LEVEL_CHANGED',
  'JOIN_REQUEST_AT_RISK',
  'JOIN_REQUEST_COMPOSITION_MISMATCH',
  'JOIN_REQUEST_FITS_AGAIN',
]

/** Os que falam do pedido de quem recebe: levam para a aba "meus pedidos". */
const SOBRE_O_MEU_PEDIDO: NotificationType[] = [
  'JOIN_REQUEST_AT_RISK',
  'JOIN_REQUEST_COMPOSITION_MISMATCH',
  'JOIN_REQUEST_FITS_AGAIN',
]

function notificacao(campos: Partial<NotificationResponse> = {}): NotificationResponse {
  return {
    id: 1,
    type: 'JOIN_REQUEST_RECEIVED',
    listId: 7,
    listName: 'Time de Teste',
    read: false,
    createdAt: new Date().toISOString(),
    ...campos,
  }
}

function pagina(itens: NotificationResponse[]) {
  return { content: itens, number: 0, totalElements: itens.length, totalPages: 1 }
}

describe('NotificationsPage', () => {
  beforeEach(() => {
    logarComo()
    vi.mocked(notificationsApi.unreadCount).mockResolvedValue(0)
  })

  /**
   * A guarda que faltava: a frase vem de `t('notifications.types.' + tipo)`, montada em
   * tempo de execução — nem o `tsc` nem o `check:i18n` reprovam um tipo novo sem
   * tradução. O sintoma seria a chave crua na tela do usuário.
   */
  it.each(TODOS_OS_TIPOS)('%s tem frase, e nao chave crua', async (type) => {
    vi.mocked(notificationsApi.list).mockResolvedValue(pagina([notificacao({ type })]))
    renderizar(<NotificationsPage />)

    const linha = await conteudo().findByRole('link')
    expect(linha.textContent ?? '').not.toContain('notifications.types.')
    expect((linha.textContent ?? '').length).toBeGreaterThan(15)
    // O nome do time entra na frase de todos eles.
    expect(linha).toHaveTextContent(/Time de Teste/)
  })

  it.each(SOBRE_O_MEU_PEDIDO)('%s leva para a aba "meus pedidos"', async (type) => {
    vi.mocked(notificationsApi.list).mockResolvedValue(pagina([notificacao({ type })]))
    renderizar(<NotificationsPage />)

    // É onde estão o motivo (requisito × level, vocação) e o botão de cancelar. Levar
    // para a página do time deixa a pessoa sem o que fazer com a informação.
    expect(await conteudo().findByRole('link')).toHaveAttribute(
      'href',
      '/account/teams?tab=requests',
    )
  })

  it('aviso sobre o time leva para a pagina do time', async () => {
    vi.mocked(notificationsApi.list).mockResolvedValue(
      pagina([notificacao({ type: 'TEAM_SCHEDULE_CHANGED', listId: 42 })]),
    )
    renderizar(<NotificationsPage />)

    expect(await conteudo().findByRole('link')).toHaveAttribute('href', '/teams/42')
  })

  it('sem notificacao, diz que esta vazio em vez de mostrar nada', async () => {
    vi.mocked(notificationsApi.list).mockResolvedValue(pagina([]))
    renderizar(<NotificationsPage />)

    expect(await conteudo().findByText(/nenhuma notificação/i)).toBeInTheDocument()
  })

  it('falha ao carregar mostra erro com "tentar de novo"', async () => {
    vi.mocked(notificationsApi.list).mockRejectedValue(new Error('rede caiu'))
    renderizar(<NotificationsPage />)

    // "Nenhuma notificação" seria mentira — e esconderia justamente o aviso que a
    // pessoa veio ler.
    expect(await conteudo().findByRole('button', { name: /tentar de novo/i })).toBeInTheDocument()
  })
})
