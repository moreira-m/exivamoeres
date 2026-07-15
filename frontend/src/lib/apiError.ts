import { AxiosError } from 'axios'
import type { ApiErrorResponse } from '../types/api'

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
