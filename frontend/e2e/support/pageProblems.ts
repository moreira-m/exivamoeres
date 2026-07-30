import type { Page } from '@playwright/test'

/**
 * Um problema observado no navegador enquanto a página era usada.
 * `where` é sempre uma URL — o que faltava nos relatos de "a tela não carrega".
 */
export interface PageProblem {
  kind: 'console' | 'exception' | 'request-failed' | 'http-error' | 'broken-image'
  message: string
  where: string
}

/**
 * Observador de problemas de uma página. **Só observa** — quem decide o que
 * reprova é o teste (ver `fixtures.ts`), e quem formata é `describeProblems`.
 *
 * Pega as cinco formas de o frontend quebrar sem avisar o usuário:
 *
 * 1. `console.error` — inclui os avisos do React sobre render inválido;
 * 2. exceção não tratada (`pageerror`) — o que deixa a tela branca;
 * 3. requisição que não completou — backend fora, DNS, **CORS**;
 * 4. resposta HTTP 4xx/5xx;
 * 5. **imagem quebrada em cena** — ver {@link findBrokenImages}. É a quinta porque
 *    faltou: uma imagem que não carrega não gera nenhum dos quatro eventos acima, e
 *    ficou dois commits mostrando um buraco no card sem ninguém ser avisado.
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

/**
 * As imagens **em cena** que terminaram de carregar sem bytes — o buraco no lugar do
 * ícone.
 *
 * Espera cada `<img>` assentar (`load` ou `error`) antes de julgar, com teto de tempo:
 * imagem ainda carregando não é imagem quebrada, e o erro clássico deste teste é
 * medir cedo. Foi assim que uma medição apressada me fez concluir que *nenhum* sprite
 * da TibiaData carregava — quando na verdade eles carregam, só não instantaneamente.
 *
 * ⚠️ **Não é um teste de terceiro.** Uma imagem de host externo pode falhar legitimamente
 * (a Cloudflare da TibiaData bloqueia IP de datacenter, por exemplo). O que esta função
 * cobra é o invariante do produto: quando a imagem falha, o componente troca por uma
 * reserva e o `<img>` **sai** do DOM (ver `CreatureIcon`). Então imagem quebrada em cena
 * significa reserva faltando — e isso é defeito nosso, em qualquer ambiente.
 */
export async function findBrokenImages(page: Page, timeoutMs = 5000): Promise<PageProblem[]> {
  const suspeitas = await procurar(page, timeoutMs)
  if (suspeitas.length === 0) {
    return []
  }
  // Segunda olhada, depois de um instante: entre o navegador marcar a imagem como
  // "terminou sem bytes" e o componente trocá-la pela reserva existe uma janela de um
  // render. Sem esta espera, o teste acusaria justamente a reserva funcionando — foi o
  // primeiro resultado ao escrever esta função.
  await page.waitForTimeout(500)
  const persistentes = await procurar(page, 0)
  return persistentes.map((img) => ({
    kind: 'broken-image' as const,
    message: `imagem sem conteúdo em cena (alt="${img.alt}") — ${img.src}`,
    where: page.url(),
  }))
}

/** As imagens que já terminaram de carregar e não trouxeram pixel nenhum. */
async function procurar(page: Page, timeoutMs: number) {
  return page.evaluate(async (teto) => {
    const imagens = [...document.images]
    await Promise.all(
      imagens.map((img) =>
        img.complete
          ? Promise.resolve()
          : new Promise<void>((pronto) => {
              const fim = () => pronto()
              img.addEventListener('load', fim, { once: true })
              img.addEventListener('error', fim, { once: true })
              setTimeout(fim, teto)
            }),
      ),
    )
    // `complete && naturalWidth === 0` = terminou e não veio pixel nenhum. Imagem que
    // ainda está a caminho tem `complete === false` e fica de fora.
    return imagens
      .filter((img) => img.complete && img.naturalWidth === 0)
      .map((img) => ({ src: img.currentSrc || img.src, alt: img.alt }))
  }, timeoutMs)
}

/** Mensagem de falha legível: um problema por linha, agrupado por tipo. */
export function describeProblems(problems: PageProblem[]): string {
  return problems
    .map((p, i) => `  ${i + 1}. [${p.kind}] ${p.message}\n     (visto em ${p.where})`)
    .join('\n')
}
