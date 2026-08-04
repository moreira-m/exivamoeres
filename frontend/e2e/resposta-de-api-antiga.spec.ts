import type { Page } from '@playwright/test'
import { expect, test, visit } from './support/fixtures'

/**
 * Um domínio por teste: **API mais velha que o site não derruba a tela.**
 *
 * Site e API sobem separados (Netlify × Railway), então existe sempre uma janela em
 * que a resposta chega sem um campo que a tela já usa. Foi assim que a página do time
 * ficou branca em 28/07/2026, e a correção de lá cobria **só o domínio de times** —
 * este é o [T10](../../NEXT_STEPS.md), que estende a garantia aos outros.
 *
 * A simulação é a mesma dos outros testes do gênero: pega a resposta **real**, tira a
 * lista, e serve isso à página.
 *
 * ⚠️ **Nem todos os quatro dependem do `arrayOrEmpty`/`pageOrEmpty` hoje.** Neutralizei
 * os dois helpers de propósito para ver quem reprova:
 *
 * | Teste | Sem o helper |
 * |---|---|
 * | catálogo (worlds/criaturas) e busca | **reprova** — a home morre no render e o `ErrorBoundary` assume |
 * | personagens e notificações | **passa** — essas telas já tinham `?? []` no consumo |
 *
 * Os dois que passam **continuam valendo**: eles afirmam a propriedade visível para o
 * usuário (a tela fica de pé), não a implementação que a garante. E é exatamente o
 * argumento do T10 — proteção que depende de cada tela lembrar do `?? []` é proteção
 * que a próxima tela esquece.
 */

/** Serve a resposta de `rota` sem a lista que a tela percorre. */
async function servirSemLista(page: Page, rota: string, mutilar: (corpo: unknown) => unknown) {
  await page.route(rota, async (route) => {
    const original = await route.fetch()
    const corpo = await original.json()
    await route.fulfill({
      status: 200,
      // Resposta de outra origem precisa do CORS, senão o navegador a descarta e o
      // teste reprova por rede em vez de pelo que veio verificar.
      headers: { 'content-type': 'application/json', 'access-control-allow-origin': '*' },
      body: JSON.stringify(mutilar(corpo)),
    })
  })
}

test('personagens: resposta sem a lista não derruba a tela', async ({ loggedPage }) => {
  // `/api/characters/mine` e `/api/claims` alimentam a mesma página: as duas somem.
  await servirSemLista(loggedPage, '**/api/characters/mine', () => ({}))
  await servirSemLista(loggedPage, '**/api/claims', () => ({}))

  await visit(loggedPage, '/account/characters')

  await expect(loggedPage.locator('main')).toContainText(/My characters/i)
  await expect(loggedPage.getByText(/Couldn't load this/i)).toHaveCount(0)
})

test('meus times: envelope sem `content` não derruba a tela', async ({ loggedPage }) => {
  // `/api/lists/mine` passou a ser um envelope `Page<T>` (item P12), então entrou na mesma
  // família de risco das notificações — e ganhou a mesma proteção central
  // (`pageOrEmpty`), não um `?? []` espalhado pela tela.
  //
  // ⚠️ A tela pede **duas** abas, então o `?*` do padrão importa: sem ele a rota com
  // `?scope=ACTIVE&page=0` não casa e o teste passaria sem interceptar nada.
  await servirSemLista(loggedPage, '**/api/lists/mine?*', () => ({}))

  await visit(loggedPage, '/account/teams')

  await expect(loggedPage.locator('main')).toContainText(/My teams/i)
  // Sem os números do envelope, os contadores das abas viram `NaN` — e o aviso do plano
  // free passa a mentir sobre quantos times ativos a pessoa tem.
  await expect(loggedPage.locator('main')).not.toContainText(/NaN/)
  await expect(loggedPage.getByText(/Couldn't load this/i)).toHaveCount(0)
})

test('notificações: envelope sem `content` não derruba a tela', async ({ loggedPage }) => {
  // Aqui o campo que falta está **dentro** do envelope `Page<T>` do Spring — e os
  // números também: sem eles, "carregar mais" e as contagens viram `NaN`.
  await servirSemLista(loggedPage, '**/api/notifications?*', () => ({}))

  await visit(loggedPage, '/account/notifications')

  await expect(loggedPage.locator('main')).toContainText(/Notifications/i)
  await expect(loggedPage.locator('main')).not.toContainText(/NaN/)
})

test('catálogo: worlds e criaturas sem lista não derrubam a home', async ({ page }) => {
  // Estes dois alimentam os filtros da busca. A tela tem que abrir com o filtro
  // vazio — que é a degradação já decidida no P13 — em vez de sumir.
  await servirSemLista(page, '**/api/worlds', () => ({}))
  await servirSemLista(page, '**/api/creatures', () => ({}))

  await visit(page, '/')

  await expect(page.locator('main')).toContainText(/Find your Soul Core team/i)
  await expect(page.getByLabel(/^World$/i)).toBeVisible()
})

test('busca: página sem `content` nem totais não quebra a contagem', async ({ page }) => {
  await servirSemLista(page, '**/api/lists/search*', () => ({}))

  await visit(page, '/')

  // Sem `totalElements`, o texto "N teams found" renderizaria `NaN teams found`.
  await expect(page.locator('main')).not.toContainText(/NaN/)
  await expect(page.locator('main')).toContainText(/No teams found/i)
})
