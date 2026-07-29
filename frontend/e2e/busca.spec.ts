import type { Page } from '@playwright/test'
import { expect, test, visit } from './support/fixtures'

/**
 * Os filtros da busca **de verdade**: clicando, e conferindo que a requisição
 * mudou. É o teste que faltava quando o filtro por vocação
 * ([P20](../../new-features/filtro-por-vocacao.md)) foi entregue — na época a
 * verificação foi um script descartável dirigindo o Chrome por CDP.
 */

/**
 * Escolhe uma opção num `Combobox` do projeto. Usa papel ARIA e rótulo — não
 * classe de CSS nem posição — para o teste não quebrar quando o visual mudar.
 */
async function escolherNoFiltro(page: Page, rotulo: RegExp, opcao: RegExp) {
  await page.getByLabel(rotulo).click()
  await page.getByRole('option', { name: opcao }).click()
}

/** Espera a próxima busca sair e devolve a URL pedida — o que o filtro promete. */
async function urlDaProximaBusca(page: Page, acao: () => Promise<void>): Promise<string> {
  const [request] = await Promise.all([
    page.waitForRequest((r) => r.url().includes('/api/lists/search')),
    acao(),
  ])
  return request.url()
}

test('escolher a vocação dispara a busca filtrada e explica o filtro na tela', async ({ page }) => {
  await visit(page, '/')

  const url = await urlDaProximaBusca(page, () =>
    escolherNoFiltro(page, /My vocation/i, /^Druid$/),
  )

  expect(url).toContain('vocation=DRUID')
  // Sem esta linha, o filtro parece "times que exigem Druid" e o primeiro
  // resultado sem composição vira suspeita de bug.
  await expect(page.locator('main')).toContainText(/Showing teams where a Druid fits right now/i)
})

test('voltar para "qualquer vocação" tira o filtro e o aviso', async ({ page }) => {
  await visit(page, '/')
  await urlDaProximaBusca(page, () => escolherNoFiltro(page, /My vocation/i, /^Druid$/))

  const url = await urlDaProximaBusca(page, () =>
    escolherNoFiltro(page, /My vocation/i, /Any vocation/i),
  )

  expect(url).not.toContain('vocation=')
  await expect(page.locator('main')).not.toContainText(/Showing teams where/i)
})

test('o filtro de vagas continua funcionando junto (não quebrou com o quarto filtro)', async ({
  page,
}) => {
  await visit(page, '/')

  const url = await urlDaProximaBusca(page, () =>
    escolherNoFiltro(page, /^Slots$/i, /Only with open slots/i),
  )

  expect(url).toContain('hasOpenSlots=true')
})

test('a contagem de resultados não discorda dos cards na tela', async ({ page }) => {
  await visit(page, '/')
  const total = await page
    .locator('main')
    .innerText()
    .then((texto) => texto.match(/(\d+) teams? found/)?.[1])

  // Ou existe time e a contagem aparece, ou a tela diz que não há nenhum — o que
  // não pode é a lista mostrar cards e a contagem discordar (o bug do P1).
  if (total === undefined) {
    await expect(page.locator('main')).toContainText(/No teams found/i)
    return
  }
  const cards = await page.locator('main a[href^="/teams/"]').count()
  expect(Number(total)).toBeGreaterThanOrEqual(cards)
})
