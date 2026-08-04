import { AxiosError, AxiosHeaders } from 'axios'
import { describe, expect, it } from 'vitest'
import i18n from '../i18n'
import { getApiErrorMessage, isNotFound } from './apiError'
import type { ErrorCode } from '../types/api'
import pt from '../i18n/locales/pt.json'

/**
 * Todos os códigos, lidos das **próprias traduções** em vez de copiados aqui.
 *
 * ⚠️ Antes esta lista era um terceiro espelho do `ErrorCode.java`, escrito à mão — e um
 * código que nunca chegasse aqui também nunca era conferido, que é justamente o furo que
 * este teste existia para fechar. Quem garante que a lista está completa é o
 * `scripts/error-codes-check.mjs` (Java → união → dois idiomas), que reprova o build.
 */
const CODIGOS = Object.keys(pt.errors.codes) as ErrorCode[]

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
        // Todos os params de todos os códigos juntos: o teste não sabe (nem precisa
        // saber) qual frase usa qual. `reason` é o do APPROVAL_BLOCKED, e vale um
        // código de verdade porque ele é traduzido antes de entrar na frase.
        params: { max: '5', limit: '3', minimum: '400', level: '150', character: 'X',
                  characterWorld: 'A', teamWorld: 'B', vocation: 'KNIGHT', status: 'CLOSED',
                  ownerLevel: '120', reason: 'FREE_ACCOUNT' },
        message: 'reserva em português',
      }))

      // Código sem frase cairia na reserva — que é seguro, mas deixaria o usuário em
      // inglês lendo português. Este teste é o que obriga a tradução a existir.
      expect(texto, `${code} em ${idioma}`).not.toBe('reserva em português')
      expect(texto).not.toContain('errors.codes.')
      expect(texto).not.toContain('{{')
      // Aninhamento do i18next (`$t(enums.vocation.{{vocation}})`) que não resolveu
      // apareceria cru na tela — o mesmo estrago da chave crua, com outra cara.
      expect(texto, `${code} em ${idioma}`).not.toContain('$t(')
      expect(texto).not.toContain('enums.')
    }
  })

  it('a recusa da aprovacao traz o motivo traduzido dentro dela', async () => {
    // Item T18: o reembrulho **descartava** o código do motivo, então o dono recebia duas
    // frases em português concatenadas. Agora o motivo vem em `params.reason` e as duas
    // partes são traduzidas.
    await i18n.changeLanguage('en')

    const texto = getApiErrorMessage(erro(422, {
      status: 422,
      code: 'APPROVAL_BLOCKED',
      params: { reason: 'BELOW_MINIMUM_LEVEL', minimum: '400', level: '150', character: 'Sir Exiva' },
      message: 'reserva em português',
    }))

    // A frase de fora e a de dentro, as duas em inglês e com os números do motivo.
    expect(texto).toContain('Could not approve')
    expect(texto).toContain('requires level 400')
    expect(texto).toContain('Sir Exiva')
    expect(texto).not.toContain('BELOW_MINIMUM_LEVEL')
  })

  it('motivo aninhado desconhecido cai na reserva, nao na chave crua', async () => {
    // Site mais antigo que a API: `APPROVAL_BLOCKED` é conhecido, o motivo não. Sem esta
    // guarda a tela mostraria "...porque errors.codes.MOTIVO_NOVO" no meio da frase.
    await i18n.changeLanguage('en')

    const texto = getApiErrorMessage(erro(422, {
      status: 422,
      code: 'APPROVAL_BLOCKED',
      params: { reason: 'MOTIVO_QUE_ESTE_SITE_NAO_CONHECE' },
      message: 'Não é possível aprovar este pedido agora: motivo novo. O pedido continua pendente.',
    }))

    expect(texto).toBe(
      'Não é possível aprovar este pedido agora: motivo novo. O pedido continua pendente.',
    )
    expect(texto).not.toContain('errors.codes.')
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
