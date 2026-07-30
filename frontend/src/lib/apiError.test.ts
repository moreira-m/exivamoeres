import { AxiosError, AxiosHeaders } from 'axios'
import { describe, expect, it } from 'vitest'
import { getApiErrorMessage, isNotFound } from './apiError'

/** Erro HTTP como o axios entrega, com o envelope do backend no corpo. */
function erro(status: number, data: unknown) {
  return new AxiosError('falhou', 'ERR_BAD_REQUEST', { headers: new AxiosHeaders() }, null, {
    status,
    statusText: '',
    headers: {},
    config: { headers: new AxiosHeaders() },
    data,
  })
}

/**
 * A tradução de erro do backend para frase de tela. É o único caminho por onde a
 * recusa do servidor chega ao usuário — quando ele devolve o texto errado, o
 * sintoma é "cliquei e não aconteceu nada".
 */
describe('getApiErrorMessage', () => {
  it('erro de campo ganha da mensagem geral', () => {
    const texto = getApiErrorMessage(
      erro(400, {
        status: 400,
        message: 'Requisição inválida',
        fieldErrors: { minimumLevel: 'Level mínimo deve ser positivo' },
      }),
    )

    // "Requisição inválida" não diz o que corrigir; o erro do campo diz.
    expect(texto).toBe('Level mínimo deve ser positivo')
  })

  it('sem erro de campo, usa a mensagem do backend', () => {
    expect(getApiErrorMessage(erro(422, { status: 422, message: 'O time está cheio' })))
      .toBe('O time está cheio')
  })

  it('fieldErrors vazio nao engole a mensagem', () => {
    // `{}` é o que o backend manda em alguns 400: sem a checagem de tamanho,
    // `Object.values({})[0]` seria `undefined` e a tela mostraria "undefined".
    const texto = getApiErrorMessage(erro(400, {
      status: 400,
      message: 'Parâmetro inválido',
      fieldErrors: {},
    }))

    expect(texto).toBe('Parâmetro inválido')
  })

  it('erro sem resposta (rede caindo) cai no texto padrao', () => {
    const semResposta = new AxiosError('Network Error', 'ERR_NETWORK')

    expect(getApiErrorMessage(semResposta)).toBe('Algo deu errado')
    expect(getApiErrorMessage(semResposta, 'Não deu')).toBe('Não deu')
  })

  it('erro que nem e do axios tambem cai no padrao', () => {
    // Erro de render, `TypeError` de código nosso: nada disso tem envelope.
    expect(getApiErrorMessage(new TypeError('x.map não é função'))).toBe('Algo deu errado')
  })
})

describe('isNotFound', () => {
  it('404 e "nao existe"', () => {
    expect(isNotFound(erro(404, { status: 404, message: 'Time não encontrado' }))).toBe(true)
  })

  it('500 e rede caindo nao sao "nao existe"', () => {
    // A distinção existe para a tela não dizer "não encontrado" quando o backend
    // caiu: uma é definitiva, a outra pede "tentar de novo".
    expect(isNotFound(erro(500, {}))).toBe(false)
    expect(isNotFound(new AxiosError('Network Error', 'ERR_NETWORK'))).toBe(false)
    expect(isNotFound(new Error('qualquer coisa'))).toBe(false)
  })
})
