import type { Page } from '@playwright/test'
import { expect, test, visit } from './support/fixtures'

/**
 * O **código para reportar** chegando à tela (item P26).
 *
 * O `X-Request-Id` existe desde o [T6](../../NEXT_STEPS.md): o servidor marca com ele
 * toda linha de log da requisição. Só que ele vivia no cabeçalho da resposta — visível
 * para quem abre o devtools —, então o fluxo *"deu erro; me manda o id"* não fechava.
 *
 * Aqui a resposta de erro é **simulada** (`page.route`), e é de propósito: um 500 de
 * verdade exige derrubar o banco, o que faz o backend levar 30s de timeout de conexão e
 * transforma o teste num teste de paciência. O que estes casos provam é o caminho do
 * dado — cabeçalho → tela — com a resposta vindo de outra origem, como em produção.
 *
 * ⚠️ **A outra metade da garantia é do backend**, e mora lá: sem
 * `Access-Control-Expose-Headers`, o navegador esconde o cabeçalho do JavaScript e nada
 * disto funciona. Ver `RequestIdVisibilityIntegrationTest`.
 */

/** Serve um erro do servidor com (ou sem) o cabeçalho de correlação. */
async function servirErro(page: Page, { id }: { id?: string }) {
  await page.route('**/api/lists/search**', async (route) => {
    await route.fulfill({
      status: 500,
      headers: {
        'content-type': 'application/json',
        'access-control-allow-origin': '*',
        // Exatamente o que o backend manda — sem esta linha o `fetch` não enxerga o
        // cabeçalho, e é esse o ponto do teste.
        ...(id ? { 'x-request-id': id, 'access-control-expose-headers': 'X-Request-Id' } : {}),
      },
      body: JSON.stringify({
        timestamp: new Date().toISOString(),
        status: 500,
        message: 'Erro interno inesperado',
        fieldErrors: null,
      }),
    })
  })
}

test('o erro na home mostra o codigo da requisicao', async ({ page, problems }) => {
  // O 500 é o assunto do teste, não um acidente.
  problems.allow(/HTTP 500/)
  await servirErro(page, { id: 'req-de-teste-123' })

  await visit(page, '/')

  const conteudo = page.locator('main')
  await expect(conteudo).toContainText(/Code to report/i)
  await expect(conteudo.locator('code')).toHaveText('req-de-teste-123')
})

test('sem o cabecalho, a tela nao inventa codigo', async ({ page, problems }) => {
  problems.allow(/HTTP 500/)
  await servirErro(page, {})

  await visit(page, '/')

  // Id inventado no frontend manda quem investiga procurar uma linha de log que não
  // existe — é pior que não mostrar nada.
  await expect(page.locator('main')).toContainText(/Couldn't load this/i)
  await expect(page.locator('main')).not.toContainText(/Code to report/i)
  await expect(page.locator('main code')).toHaveCount(0)
})

test('o codigo aparece so na falha, nao na tela que carregou', async ({ page }) => {
  await visit(page, '/')

  // Aviso que aparece sempre é aviso que ninguém lê.
  await expect(page.locator('main')).not.toContainText(/Code to report/i)
})
