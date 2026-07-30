import type { Page } from '@playwright/test'
import { expect, test, visit } from './support/fixtures'

/**
 * Os cabeçalhos de segurança **como o navegador os recebe** (item S9).
 *
 * Por que este arquivo existe além do teste de unidade de `src/security/headers.ts`:
 * o unitário prova que a função **monta** a política certa; este prova que ela
 * chega na resposta e que o site **funciona** debaixo dela. As duas coisas falham
 * separado — CSP correta que ninguém serve não protege, e CSP servida que bloqueia
 * a própria API protege o usuário do site.
 *
 * ⚠️ Aqui os cabeçalhos vêm do `preview` do Vite (`server`/`preview.headers` no
 * `vite.config.ts`). Em produção quem os serve é o Netlify, pelo `dist/_headers`
 * gerado no build — **a mesma função**, então o que este teste prende vale nos dois
 * lugares. O que ele não alcança é o Netlify obedecer ao arquivo: isso é uma linha
 * do checklist de produção ([`ACOES_MANUAIS.md` A5](../../ACOES_MANUAIS.md)).
 */

/** Os cabeçalhos da resposta do documento (não de um asset). */
async function cabecalhosDaPagina(page: Page, rota = '/') {
  const resposta = await page.goto(rota)
  const cabecalhos = resposta?.headers() ?? {}
  return cabecalhos
}

test('a resposta traz CSP, nosniff, DENY e Referrer-Policy', async ({ page }) => {
  const cabecalhos = await cabecalhosDaPagina(page)

  expect(cabecalhos['content-security-policy']).toContain("default-src 'self'")
  expect(cabecalhos['x-content-type-options']).toBe('nosniff')
  // Clickjacking em cima de "encerrar time" / "expulsar membro".
  expect(cabecalhos['x-frame-options']).toBe('DENY')
  expect(cabecalhos['referrer-policy']).toBe('strict-origin-when-cross-origin')
  expect(cabecalhos['permissions-policy']).toContain('geolocation=()')
})

test('a CSP libera a API e o WebSocket, e nada além do necessário', async ({ page }) => {
  const csp = (await cabecalhosDaPagina(page))['content-security-policy']

  // Sem estas duas, a home abre e **nada** carrega — e o chat morre em silêncio.
  expect(csp).toMatch(/connect-src [^;]*http:\/\/localhost:8080/)
  expect(csp).toMatch(/connect-src [^;]*ws:\/\/localhost:8080/)
  expect(csp).toContain("script-src 'self'")
  expect(csp).not.toContain('unsafe-inline')
})

test('em ambiente local nao vem HSTS', async ({ page }) => {
  const cabecalhos = await cabecalhosDaPagina(page)

  // HSTS de localhost obriga o navegador a tentar https na 5173 pelo próximo ano
  // — um problema inventado, e chato de desfazer no perfil do navegador.
  expect(cabecalhos['strict-transport-security']).toBeUndefined()
})

test('a home carrega debaixo da CSP, e o sprite da TibiaData nao e bloqueado', async ({ page }) => {
  // Requisição **tentada** é a prova certa para `img-src`: quando a CSP bloqueia,
  // o navegador nem sai para a rede (e registra erro de console, que o fixture
  // `problems` reprova). O que a TibiaData responde depois não é assunto da CSP —
  // e hoje ela está atrás de um desafio da Cloudflare que nunca completa, ver
  // NEXT_STEPS3 P27.
  const tentativas: string[] = []
  page.on('request', (r) => {
    if (r.url().startsWith('https://static.tibia.com')) tentativas.push(r.url())
  })

  await visit(page, '/')

  const sprites = await page.locator('main img[src^="https://static.tibia.com"]').count()
  if (sprites > 0) {
    expect(
      tentativas,
      'a página tem <img> da TibiaData mas nenhuma requisição saiu: img-src está bloqueando',
    ).not.toEqual([])
  }
})

test('as telas de conta tambem carregam debaixo da CSP', async ({ loggedPage }) => {
  // As rotas de escrita são as que mais chamam a API; se `connect-src` estivesse
  // errado, é aqui que apareceria primeiro.
  await visit(loggedPage, '/account/teams')
  await visit(loggedPage, '/account/teams/new')
})
