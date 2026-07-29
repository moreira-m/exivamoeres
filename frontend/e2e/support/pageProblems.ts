import type { Page } from '@playwright/test'

/**
 * Um problema observado no navegador enquanto a página era usada.
 * `where` é sempre uma URL — o que faltava nos relatos de "a tela não carrega".
 */
export interface PageProblem {
  kind: 'console' | 'exception' | 'request-failed' | 'http-error'
  message: string
  where: string
}

/**
 * Observador de problemas de uma página. **Só observa** — quem decide o que
 * reprova é o teste (ver `fixtures.ts`), e quem formata é `describeProblems`.
 *
 * Pega as quatro formas de o frontend quebrar sem avisar o usuário:
 *
 * 1. `console.error` — inclui os avisos do React sobre render inválido;
 * 2. exceção não tratada (`pageerror`) — o que deixa a tela branca;
 * 3. requisição que não completou — backend fora, DNS, **CORS**;
 * 4. resposta HTTP 4xx/5xx.
 */
export function watchForProblems(page: Page) {
  const problems: PageProblem[] = []
  const allowed: RegExp[] = []

  const record = (problem: PageProblem) => {
    if (allowed.some((pattern) => pattern.test(problem.message))) return
    problems.push(problem)
  }

  page.on('console', (message) => {
    if (message.type() !== 'error') return
    // "Failed to load resource: ... 404" é o navegador narrando o que o listener
    // de `response` já registra — com método e URL, que esta mensagem não traz.
    // Registrar os dois dobraria cada erro de rede e tornaria o `allow` confuso.
    if (message.text().startsWith('Failed to load resource')) return
    record({ kind: 'console', message: message.text(), where: page.url() })
  })

  page.on('pageerror', (error) => {
    record({ kind: 'exception', message: error.message, where: page.url() })
  })

  page.on('requestfailed', (request) => {
    const motivo = request.failure()?.errorText ?? 'desconhecido'
    // Requisição cancelada é rotina de SPA: o React Query aborta a busca
    // anterior quando o filtro muda antes de a resposta chegar.
    if (motivo.includes('ERR_ABORTED')) return
    record({ kind: 'request-failed', message: `${motivo} — ${request.url()}`, where: page.url() })
  })

  page.on('response', (response) => {
    if (response.status() < 400) return
    record({
      kind: 'http-error',
      message: `HTTP ${response.status()} — ${response.request().method()} ${response.url()}`,
      where: page.url(),
    })
  })

  return {
    /** Tudo que foi observado até agora. */
    list: () => [...problems],
    /**
     * Silencia problemas cuja mensagem casa com o padrão. Use com um comentário
     * dizendo **por que** aquilo é esperado — o objetivo é que a lista fique
     * curta e justificada, não que os testes fiquem verdes.
     */
    allow: (pattern: RegExp) => allowed.push(pattern),
  }
}

export type ProblemWatcher = ReturnType<typeof watchForProblems>

/** Mensagem de falha legível: um problema por linha, agrupado por tipo. */
export function describeProblems(problems: PageProblem[]): string {
  return problems
    .map((p, i) => `  ${i + 1}. [${p.kind}] ${p.message}\n     (visto em ${p.where})`)
    .join('\n')
}
