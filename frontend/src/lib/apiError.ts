import { AxiosError } from 'axios'
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

/** Extrai a mensagem amigável do envelope de erro padronizado do backend. */
export function getApiErrorMessage(error: unknown, fallback = 'Algo deu errado'): string {
  if (error instanceof AxiosError) {
    const data = error.response?.data as ApiErrorResponse | undefined
    if (data?.fieldErrors && Object.keys(data.fieldErrors).length > 0) {
      return Object.values(data.fieldErrors)[0]
    }
    if (data?.message) {
      return data.message
    }
  }
  return fallback
}
