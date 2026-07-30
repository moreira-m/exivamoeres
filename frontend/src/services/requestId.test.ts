import { AxiosError, AxiosHeaders } from 'axios'
import { beforeEach, describe, expect, it } from 'vitest'
import {
  esquecerRequestId,
  lembrarRequestId,
  requestIdDoErro,
  ultimoRequestId,
} from './requestId'

/** Erro do axios com (ou sem) resposta, como o interceptor recebe. */
function erroComId(id?: string) {
  const headers = id ? { 'x-request-id': id } : {}
  return new AxiosError('falhou', 'ERR_BAD_RESPONSE', { headers: new AxiosHeaders() }, null, {
    status: 500,
    statusText: '',
    headers,
    config: { headers: new AxiosHeaders() },
    data: { status: 500, message: 'Erro interno inesperado' },
  })
}

describe('requestId', () => {
  beforeEach(esquecerRequestId)

  it('lembra o id da ultima resposta', () => {
    lembrarRequestId('abc-123')

    expect(ultimoRequestId()).toBe('abc-123')
  })

  it('nao lembra vazio, espaco nem valor que nao e texto', () => {
    lembrarRequestId('abc-123')
    lembrarRequestId('')
    lembrarRequestId('   ')
    lembrarRequestId(undefined)
    lembrarRequestId(42)

    // O último id **válido** continua valendo: apagar por causa de uma resposta sem
    // header deixaria a tela de erro sem nada justo quando ela é aberta.
    expect(ultimoRequestId()).toBe('abc-123')
  })

  it('sem nunca ter visto resposta, nao ha id', () => {
    // O ponto do item: **não inventar**. Requisição que não saiu não tem linha de log,
    // e um id gerado aqui mandaria quem investiga procurar o que não existe.
    expect(ultimoRequestId()).toBeNull()
  })

  it('tira o id do erro do axios', () => {
    expect(requestIdDoErro(erroComId('do-erro-1'))).toBe('do-erro-1')
  })

  it('erro sem resposta ou sem header nao tem id', () => {
    expect(requestIdDoErro(erroComId())).toBeNull()
    expect(requestIdDoErro(new AxiosError('Network Error', 'ERR_NETWORK'))).toBeNull()
    expect(requestIdDoErro(new TypeError('x.map não é função'))).toBeNull()
  })

  it('o id do erro nao e afetado pelo ultimo visto', () => {
    lembrarRequestId('resposta-mais-recente')

    // Um id de **outra** requisição parece confiável e manda quem investiga para a
    // linha errada: onde o erro está em mãos, é o dele que vale.
    expect(requestIdDoErro(erroComId('do-erro-2'))).toBe('do-erro-2')
  })
})
