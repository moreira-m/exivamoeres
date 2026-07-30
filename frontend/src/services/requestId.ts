import { AxiosError } from 'axios'

/**
 * O `X-Request-Id` da última resposta que a API deu — o id que aparece em **toda linha
 * de log** daquela requisição no servidor (ver `RequestIdFilter`, item T6).
 *
 * Existe para a tela poder mostrar *"deu erro; me manda este código"* e a investigação
 * começar por uma busca exata em vez de varredura por horário.
 *
 * **Por que um módulo e não estado global de UI:** nada aqui é renderizado a partir de
 * uma mudança — quem lê, lê no momento de desenhar uma tela de erro. Um `store` faria
 * toda a aplicação re-renderizar a cada resposta da API, em troca de nada.
 */
let ultimoId: string | null = null

/** Registra o id de uma resposta (ou de um erro que teve resposta). */
export function lembrarRequestId(id: unknown): void {
  if (typeof id === 'string' && id.trim()) {
    ultimoId = id.trim()
  }
}

/**
 * O último id visto, ou `null`.
 *
 * ⚠️ **Nunca inventa.** Se a requisição não saiu (rede caindo, DNS), não existe linha de
 * log correspondente no servidor: mostrar um id gerado aqui mandaria quem investiga
 * procurar o que não existe — pior que não mostrar nada.
 */
export function ultimoRequestId(): string | null {
  return ultimoId
}

/**
 * O id **daquela** falha, quando o backend respondeu.
 *
 * Preferir este ao {@link ultimoRequestId} onde o erro está em mãos (é o caso do
 * `QueryError`): o último id visto pode ser de outra requisição que veio depois — e um
 * id que não é o do erro é pior que nenhum, porque parece confiável.
 */
export function requestIdDoErro(error: unknown): string | null {
  if (!(error instanceof AxiosError)) {
    return null
  }
  const id = error.response?.headers?.['x-request-id']
  return typeof id === 'string' && id.trim() ? id.trim() : null
}

/** Só para os testes: zera o id lembrado entre casos. */
export function esquecerRequestId(): void {
  ultimoId = null
}
