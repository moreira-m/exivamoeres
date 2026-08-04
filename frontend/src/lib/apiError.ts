import { AxiosError } from 'axios'
import i18n from '../i18n'
import type { ApiErrorResponse } from '../types/api'

/**
 * O recurso não existe (404) — distinto de "a requisição falhou".
 *
 * Serve para a tela não dizer "não encontrado" quando o que houve foi backend
 * fora do ar, rede caindo ou CORS: são mensagens diferentes e ações diferentes
 * (uma é definitiva, a outra pede "tentar de novo").
 */
export function isNotFound(error: unknown): boolean {
  return error instanceof AxiosError && error.response?.status === 404
}

/**
 * A mensagem amigável do envelope de erro do backend, **no idioma do usuário quando
 * possível** (item T2).
 *
 * A ordem não é arbitrária:
 *
 * 1. **`fieldErrors`** — validação de campo já vem específica ("level mínimo deve ser
 *    positivo") e diz o que corrigir;
 * 2. **`code`** — recusa de regra convertida: a frase é montada aqui, com os `params`, no
 *    idioma escolhido;
 * 3. **`message`** — a frase em português que o backend manda sempre, como reserva. É o que
 *    mantém a migração do T2 incremental: regra ainda não convertida (ou código que este
 *    site não conhece, porque a API é mais nova) continua aparecendo.
 *
 * Usa o `i18n` singleton em vez de receber `t`: é o mesmo padrão do `lib/format.ts`, e
 * evita passar `t` por dez chamadas que só querem uma string.
 */
export function getApiErrorMessage(error: unknown, fallback = 'Algo deu errado'): string {
  if (error instanceof AxiosError) {
    const data = error.response?.data as ApiErrorResponse | undefined
    if (data?.fieldErrors && Object.keys(data.fieldErrors).length > 0) {
      return Object.values(data.fieldErrors)[0]
    }
    const traduzida = traduzirCodigo(data)
    if (traduzida) {
      return traduzida
    }
    if (data?.message) {
      return data.message
    }
  }
  return fallback
}

/**
 * A frase do código, ou `null` quando não há código **ou** quando este site não conhece o
 * código que chegou.
 *
 * ⚠️ O segundo caso é real, e é o motivo do `i18n.exists`: site e API sobem separados, então
 * a API pode mandar um código para o qual esta versão do site não tem frase. Sem a
 * checagem, a tela mostraria a chave crua (`errors.codes.ALGO_NOVO`) em vez de cair na frase
 * em português — trocando uma tradução faltando por uma tela quebrada.
 */
function traduzirCodigo(data: ApiErrorResponse | undefined): string | null {
  if (!data?.code) {
    return null
  }
  const chave = `errors.codes.${data.code}`
  if (!i18n.exists(chave)) {
    return null
  }
  const params = { ...data.params }
  if (params.reason !== undefined) {
    const motivo = traduzirMotivoAninhado(params.reason, params)
    if (motivo === null) {
      // Mesma regra do `i18n.exists` acima, um nível abaixo: sem a frase do motivo, a
      // do dono sairia com a **chave crua** no meio ("...porque errors.codes.ALGO_NOVO").
      // Cair no `message` em português é pior no idioma e melhor na tela.
      return null
    }
    params.reason = motivo
  }
  return i18n.t(chave, params)
}

/**
 * Um código dentro de outro (`APPROVAL_BLOCKED` carrega `reason`, item T18).
 *
 * ⚠️ Traduzido **aqui** em vez de com o `$t()` do i18next dentro da frase: o `$t()` com
 * chave interpolada funciona, mas não tem como perguntar antes se a chave existe — e código
 * desconhecido viraria chave crua na tela. O `reason` recebe os **mesmos params**, porque
 * eles são os da frase de dentro (o level exigido, o nome do personagem).
 */
function traduzirMotivoAninhado(
  reason: string,
  params: Record<string, string>,
): string | null {
  const chave = `errors.codes.${reason}`
  return i18n.exists(chave) ? i18n.t(chave, params) : null
}
