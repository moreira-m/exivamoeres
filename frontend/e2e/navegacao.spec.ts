import type { Locator, Page } from '@playwright/test'
import { LOGGED_ROUTES, PUBLIC_ROUTES, type AppRoute } from './routes'
import { expect, test, visit } from './support/fixtures'

/**
 * A varredura: abre **cada** página do site e reprova quando ela quebra em
 * silêncio. Três perguntas por página, na ordem de importância:
 *
 * 1. **Tem conteúdo?** É o bug que motivou o suíte: tela em branco, ou só o
 *    cabeçalho, sem nada dentro do `<main>`.
 * 2. **Reclamou no console ou na rede?** Vem de graça do fixture `problems`, que
 *    roda em todo teste (ver `support/fixtures.ts`).
 * 3. **É a página certa?** Cada rota declara em `routes.ts` um texto só dela;
 *    sem isso, um `<main>` preso em "carregando" passaria.
 */

/**
 * O defeito que se procura é **tela vazia**, não tela enxuta: "NOTIFICATIONS / No
 * notifications." (31 caracteres) é uma página correta. Quem garante que veio a
 * página certa, e não um esqueleto qualquer, é o `mustShow` de cada rota.
 */
const MINIMO_DE_TEXTO = 10

async function assertTemConteudo(main: Locator) {
  const texto = (await main.innerText()).trim()
  expect(
    texto.length,
    `O <main> tem ${texto.length} caractere(s): a página está vazia. ` +
      `Conteúdo: ${JSON.stringify(texto)}`,
  ).toBeGreaterThan(MINIMO_DE_TEXTO)
}

async function assertPaginaSaudavel(page: Page, route: AppRoute) {
  await visit(page, route.path)
  const main = page.locator('main')

  await assertTemConteudo(main)
  await expect(main).toContainText(route.mustShow)

  // O card de erro do P13 é conteúdo honesto (a página não mentiu), mas numa
  // varredura significa que ela não conseguiu carregar o que precisava.
  await expect(page.getByText(/Couldn't load this/i)).toHaveCount(0)
}

test.describe('páginas públicas', () => {
  for (const route of PUBLIC_ROUTES) {
    test(`${route.name} — ${route.path}`, async ({ page }) => {
      await assertPaginaSaudavel(page, route)
    })
  }
})

test.describe('páginas da área logada', () => {
  for (const route of LOGGED_ROUTES) {
    test(`${route.name} — ${route.path}`, async ({ loggedPage }) => {
      await assertPaginaSaudavel(loggedPage, route)
    })
  }
})

test.describe('proteção das rotas', () => {
  for (const route of LOGGED_ROUTES) {
    test(`${route.path} sem sessão manda para o login (e não fica em branco)`, async ({ page }) => {
      await page.goto(route.path)
      await expect(page).toHaveURL(/\/login$/)
      await expect(page.locator('main')).toContainText(/Continue as guest/i)
    })
  }
})
