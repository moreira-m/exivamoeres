import { AxiosError, AxiosHeaders } from 'axios'
import { describe, expect, it } from 'vitest'
import i18n from '../i18n'
import { getApiErrorMessage, isNotFound } from './apiError'
import type { ErrorCode } from '../types/api'

/** Códigos que o backend pode mandar (espelho de `ErrorCode.java`). */
const CODIGOS: ErrorCode[] = [
  'TEAM_FULL',
  'WORLD_MISMATCH',
  'FREE_ACCOUNT',
  'BELOW_MINIMUM_LEVEL',
  'CHARACTER_NOT_FOUND',
  'VOCATION_WITHOUT_SLOT',
  'ALREADY_MEMBER',
  'PENDING_REQUEST_EXISTS',
  'TEAM_NOT_ACCEPTING',
  'ACTIVE_TEAM_LIMIT',
]

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

/**
 * A tradução por **código** (item T2). O backend manda `code` + `params` e a frase em
 * português como reserva; a tela monta o texto no idioma do usuário.
 */
describe('getApiErrorMessage — código traduzido', () => {
  it('monta a frase no idioma do usuario, com os valores', () => {
    const texto = getApiErrorMessage(erro(422, {
      status: 422,
      code: 'TEAM_FULL',
      params: { max: '5' },
      message: 'O time está cheio (máximo de 5 jogadores)',
    }))

    expect(texto).toBe('O time está cheio (máximo de 5 jogadores).')
  })

  it('a mesma recusa sai em ingles quando o idioma e ingles', async () => {
    await i18n.changeLanguage('en')

    const texto = getApiErrorMessage(erro(422, {
      status: 422,
      code: 'WORLD_MISMATCH',
      params: { character: 'Sir Exiva', characterWorld: 'Antica', teamWorld: 'Bona' },
      message: "Personagem 'Sir Exiva' é do world Antica, mas o time é do world Bona",
    }))

    // É o motivo do item existir: a frase pronta do backend chegava sempre em português.
    expect(texto).toBe('Sir Exiva is on Antica, and the team is on Bona.')
  })

  it('codigo que este site nao conhece cai na frase do backend', () => {
    const texto = getApiErrorMessage(erro(422, {
      status: 422,
      code: 'ALGO_QUE_AINDA_NAO_EXISTE',
      params: null,
      message: 'Uma regra nova recusou isto',
    }))

    // Site e API sobem separados: sem esta reserva, uma API mais nova mostraria a chave
    // crua (`errors.codes.ALGO_…`) na tela.
    expect(texto).toBe('Uma regra nova recusou isto')
  })

  it('recusa sem codigo continua usando a frase do backend', () => {
    const texto = getApiErrorMessage(erro(422, {
      status: 422,
      code: null,
      params: null,
      message: 'O dono não pode sair do próprio time; transfira ou exclua o time',
    }))

    // É o que mantém a migração incremental: regra não convertida funciona como antes.
    expect(texto).toContain('não pode sair do próprio time')
  })

  it('erro de campo ganha do codigo', () => {
    const texto = getApiErrorMessage(erro(400, {
      status: 400,
      code: 'TEAM_FULL',
      params: { max: '5' },
      message: 'Requisição inválida',
      fieldErrors: { minimumLevel: 'Level mínimo deve ser positivo' },
    }))

    // Validação de campo é mais específica que qualquer código: diz o que corrigir.
    expect(texto).toBe('Level mínimo deve ser positivo')
  })

  it.each(CODIGOS)('%s tem frase nos dois idiomas', async (code) => {
    for (const idioma of ['pt', 'en'] as const) {
      await i18n.changeLanguage(idioma)
      const texto = getApiErrorMessage(erro(422, {
        status: 422,
        code,
        params: { max: '5', limit: '3', minimum: '400', level: '150', character: 'X',
                  characterWorld: 'A', teamWorld: 'B', vocation: 'KNIGHT', status: 'CLOSED' },
        message: 'reserva em português',
      }))

      // Código sem frase cairia na reserva — que é seguro, mas deixaria o usuário em
      // inglês lendo português. Este teste é o que obriga a tradução a existir.
      expect(texto, `${code} em ${idioma}`).not.toBe('reserva em português')
      expect(texto).not.toContain('errors.codes.')
      expect(texto).not.toContain('{{')
    }
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
