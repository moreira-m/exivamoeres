import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AxiosError, AxiosHeaders } from 'axios'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { QueryError } from './QueryError'
import { esquecerRequestId, lembrarRequestId } from '../../services/requestId'
import { renderizar } from '../../test/renderizar'

function erro(status: number, { id, message }: { id?: string; message?: string } = {}) {
  return new AxiosError('falhou', 'ERR_BAD_RESPONSE', { headers: new AxiosHeaders() }, null, {
    status,
    statusText: '',
    headers: id ? { 'x-request-id': id } : {},
    config: { headers: new AxiosHeaders() },
    data: message ? { status, message } : undefined,
  })
}

/**
 * O card de falha de carregamento e o **código para reportar** (item P26).
 *
 * O `X-Request-Id` existe desde o T6 e marca toda linha de log da requisição no
 * servidor — mas vivia só no cabeçalho, visível para quem abre o devtools. Sem ele na
 * tela, "deu erro; me manda o id" não fecha.
 */
describe('QueryError', () => {
  beforeEach(esquecerRequestId)

  it('mostra a mensagem do backend e o codigo para reportar', () => {
    renderizar(<QueryError error={erro(500, { id: 'req-abc-123', message: 'Erro interno inesperado' })} />)

    expect(screen.getByText(/erro interno inesperado/i)).toBeInTheDocument()
    expect(screen.getByText(/código para reportar/i)).toBeInTheDocument()
    expect(screen.getByText('req-abc-123')).toBeInTheDocument()
  })

  it('o codigo e selecionavel de uma vez, para copiar', () => {
    renderizar(<QueryError error={erro(500, { id: 'req-copiavel' })} />)

    // O gesto esperado é copiar e colar num relato; `select-all` faz um clique pegar
    // o id inteiro.
    expect(screen.getByText('req-copiavel')).toHaveClass('select-all')
  })

  it('falha sem resposta nao mostra codigo nenhum', () => {
    renderizar(<QueryError error={new AxiosError('Network Error', 'ERR_NETWORK')} />)

    // Requisição que não chegou ao servidor não tem linha de log: um id aqui mandaria
    // quem investiga procurar o que não existe.
    expect(screen.queryByText(/código para reportar/i)).not.toBeInTheDocument()
    expect(screen.getByText(/não conseguimos falar com o servidor/i)).toBeInTheDocument()
  })

  it('nao usa o ultimo id visto quando o erro nao tem o seu', () => {
    lembrarRequestId('de-outra-requisicao')

    renderizar(<QueryError error={new AxiosError('Network Error', 'ERR_NETWORK')} />)

    // Aqui o erro está em mãos: mostrar o id de outra requisição seria pior que nada,
    // porque parece confiável.
    expect(screen.queryByText('de-outra-requisicao')).not.toBeInTheDocument()
  })

  it('o botao de tentar de novo continua chamando o refetch', async () => {
    const tentar = vi.fn()
    renderizar(<QueryError error={erro(503)} onRetry={tentar} />)

    await userEvent.click(screen.getByRole('button', { name: /tentar de novo/i }))

    expect(tentar).toHaveBeenCalledTimes(1)
  })
})
