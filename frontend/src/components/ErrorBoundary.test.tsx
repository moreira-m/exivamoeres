import { screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ErrorBoundary } from './ErrorBoundary'
import { esquecerRequestId, lembrarRequestId } from '../services/requestId'
import { renderizar } from '../test/renderizar'

/** Componente que estoura no render — o que o boundary existe para pegar. */
function Explode(): never {
  throw new Error('undefined is not an object')
}

/**
 * O boundary e o **código para reportar** (itens T9 e P26).
 *
 * Aqui o id é o **último visto**, não o do erro: erro de render não tem resposta HTTP em
 * mãos, e a última requisição é quase sempre a que trouxe o dado que quebrou a tela —
 * foi exatamente esse o caso que criou o boundary (`team.slots.length` num campo que a
 * API deixou de mandar).
 */
describe('ErrorBoundary', () => {
  beforeEach(() => {
    esquecerRequestId()
    // O boundary registra o erro no console de propósito (é o único destino hoje);
    // silenciar aqui evita poluir a saída da suíte sem esconder nada do produto.
    vi.spyOn(console, 'error').mockImplementation(() => {})
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('erro de render vira tela de falha, nao tela branca', () => {
    renderizar(
      <ErrorBoundary>
        <Explode />
      </ErrorBoundary>,
    )

    expect(screen.getByText(/algo deu errado nesta tela/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /recarregar a página/i })).toBeInTheDocument()
  })

  it('mostra o codigo da ultima requisicao vista', () => {
    lembrarRequestId('req-do-dado-que-quebrou')

    renderizar(
      <ErrorBoundary>
        <Explode />
      </ErrorBoundary>,
    )

    expect(screen.getByText('req-do-dado-que-quebrou')).toBeInTheDocument()
  })

  it('sem requisicao nenhuma, nao mostra codigo', () => {
    renderizar(
      <ErrorBoundary>
        <Explode />
      </ErrorBoundary>,
    )

    // Erro de render antes de qualquer chamada à API não tem linha de log no servidor.
    expect(screen.queryByText(/código para reportar/i)).not.toBeInTheDocument()
  })

  it('com section, a falha tem o tamanho do bloco e tambem traz o codigo', () => {
    lembrarRequestId('req-da-secao')

    renderizar(
      <ErrorBoundary section="chat">
        <Explode />
      </ErrorBoundary>,
    )

    // Regressão do T9: o aviso é do tamanho do bloco (sem "recarregar a página", que é
    // do boundary de cima).
    expect(screen.getByText(/não foi possível mostrar: chat/i)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /recarregar a página/i })).not.toBeInTheDocument()
    expect(screen.getByText('req-da-secao')).toBeInTheDocument()
  })

  it('sem erro, renderiza o conteudo e nao mostra codigo', () => {
    lembrarRequestId('req-irrelevante')

    renderizar(
      <ErrorBoundary>
        <p>conteúdo normal</p>
      </ErrorBoundary>,
    )

    expect(screen.getByText('conteúdo normal')).toBeInTheDocument()
    expect(screen.queryByText('req-irrelevante')).not.toBeInTheDocument()
  })
})
