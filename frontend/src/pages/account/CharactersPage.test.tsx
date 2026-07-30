import { waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AxiosError, AxiosHeaders } from 'axios'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { CharactersPage } from './CharactersPage'
import { claimService } from '../../services/claimService'
import { notificationsApi } from '../../services/notificationsApi'
import { conteudo, logarComo, renderizar } from '../../test/renderizar'
import type { ClaimResponse } from '../../types/api'

vi.mock('../../services/claimService')
vi.mock('../../services/notificationsApi')
vi.mock('../../services/authService')

function claim(campos: Partial<ClaimResponse> = {}): ClaimResponse {
  return {
    id: 1,
    characterName: 'Sir Exiva',
    world: 'Antica',
    level: 300,
    verificationCode: 'EXIVA-7F3A9B',
    status: 'PENDING',
    lastCheckedAt: null,
    createdAt: new Date().toISOString(),
    expiresAt: new Date(Date.now() + 3600_000).toISOString(),
    ...campos,
  }
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
 * A verificação de personagem (item T17) — o fluxo de escrita que **abre** o produto: sem
 * personagem verificado não se cria time nem se entra em nenhum.
 *
 * O que estes testes prendem é a parte que só existe na tela: o **código** aparecer para
 * ser copiado, e a recusa da TibiaData virar frase em vez de silêncio.
 */
describe('CharactersPage', () => {
  beforeEach(() => {
    logarComo()
    vi.mocked(claimService.list).mockResolvedValue([])
    vi.mocked(claimService.create).mockResolvedValue(claim())
    vi.mocked(notificationsApi.unreadCount).mockResolvedValue(0)
  })

  it('claim pendente mostra o codigo para colar no Comment', async () => {
    vi.mocked(claimService.list).mockResolvedValue([claim({ verificationCode: 'EXIVA-ABC123' })])
    renderizar(<CharactersPage />)

    // O código **é** a verificação: sem ele na tela, o fluxo inteiro para.
    expect(await conteudo().findByText('EXIVA-ABC123')).toBeInTheDocument()
    expect(conteudo().getByText(/cole/i)).toBeInTheDocument()
    // E o aviso de que a checagem é automática — sem ele a pessoa fica esperando um botão.
    expect(conteudo().getByText(/automaticamente|alguns minutos/i)).toBeInTheDocument()
  })

  it('claim aprovado nao mostra codigo nenhum', async () => {
    vi.mocked(claimService.list).mockResolvedValue([claim({ status: 'APPROVED' })])
    renderizar(<CharactersPage />)

    await conteudo().findByText('Sir Exiva')
    // Código de um claim já aprovado é ruído — e sugere que ainda falta fazer algo.
    expect(conteudo().queryByText('EXIVA-7F3A9B')).not.toBeInTheDocument()
  })

  it('envia o nome aparado e limpa o campo', async () => {
    renderizar(<CharactersPage />)

    await userEvent.type(await conteudo().findByLabelText(/nome do personagem/i), '  Sir Exiva  ')
    await userEvent.click(conteudo().getByRole('button', { name: /verificar|iniciar/i }))

    // Espaço nas pontas é o que sobra de copiar do Tibia.com; mandar assim faz o
    // backend não achar o personagem.
    //
    // ⚠️ Primeiro **argumento**, não `toHaveBeenCalledWith`: o React Query 5 passa um
    // segundo parâmetro (contexto da mutação) para o `mutationFn`, então a asserção de
    // igualdade exata falharia por causa de algo que não é do nosso código.
    await waitFor(() => expect(claimService.create).toHaveBeenCalled())
    expect(vi.mocked(claimService.create).mock.calls[0][0]).toBe('Sir Exiva')
    expect(conteudo().getByLabelText(/nome do personagem/i)).toHaveValue('')
  })

  it('recusa da TibiaData aparece na tela', async () => {
    vi.mocked(claimService.create).mockRejectedValue(
      recusa('Personagem não encontrado na TibiaData'),
    )
    renderizar(<CharactersPage />)

    await userEvent.type(await conteudo().findByLabelText(/nome do personagem/i), 'Nao Existe')
    await userEvent.click(conteudo().getByRole('button', { name: /verificar|iniciar/i }))

    // Sem isto, digitar o nome errado é um clique que "não fez nada".
    expect(await conteudo().findByText(/não encontrado na tibiadata/i)).toBeInTheDocument()
  })

  it('o campo continua preenchido quando a recusa acontece', async () => {
    vi.mocked(claimService.create).mockRejectedValue(recusa('Personagem já verificado'))
    renderizar(<CharactersPage />)

    await userEvent.type(await conteudo().findByLabelText(/nome do personagem/i), 'Sir Exiva')
    await userEvent.click(conteudo().getByRole('button', { name: /verificar|iniciar/i }))

    // Limpar o campo numa recusa obriga a redigitar para corrigir um acento.
    await conteudo().findByText(/já verificado/i)
    expect(conteudo().getByLabelText(/nome do personagem/i)).toHaveValue('Sir Exiva')
  })

  it('sem personagem nenhum, diz que esta vazio', async () => {
    renderizar(<CharactersPage />)

    expect(await conteudo().findByText(/nenhum personagem/i)).toBeInTheDocument()
  })

  it('falha ao carregar nao vira "nenhum personagem"', async () => {
    vi.mocked(claimService.list).mockRejectedValue(new Error('rede caiu'))
    renderizar(<CharactersPage />)

    // O modo de falha que isto evita: a pessoa acha que a verificação se perdeu e começa
    // um claim novo por cima do que já existe.
    expect(await conteudo().findByRole('button', { name: /tentar de novo/i })).toBeInTheDocument()
    expect(conteudo().queryByText(/nenhum personagem/i)).not.toBeInTheDocument()
  })
})
