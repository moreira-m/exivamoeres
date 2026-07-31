import type { Page } from '@playwright/test'
import { expect, test, visit } from './support/fixtures'

/**
 * Os filtros da busca vivem na **URL** (item P22).
 *
 * As três consequências que o item nomeia, na ordem em que doem: **"me manda o link"** não
 * existia, o **botão voltar** perdia o filtro, e **recarregar** zerava tudo. Os três são o
 * mesmo defeito — filtro em `useState` não é endereço — e por isso os três testes abaixo
 * exercitam o mesmo mecanismo por caminhos diferentes.
 */

/** Escolhe uma opção num `Combobox`, por papel e rótulo (não por classe de CSS). */
async function escolher(page: Page, rotulo: RegExp, opcao: RegExp) {
  await page.getByLabel(rotulo).click()
  await page.getByRole('option', { name: opcao }).click()
}

test('escolher um filtro coloca o filtro no endereço', async ({ page }) => {
  await visit(page, '/')

  await escolher(page, /My vocation/i, /^Druid$/)

  // O que está na tela está na URL: é o que torna a busca compartilhável.
  await expect(page).toHaveURL(/vocation=DRUID/)
})

test('abrir o link recebido já mostra a busca filtrada', async ({ page }) => {
  // O caso de uso do item: alguém copiou a URL e mandou.
  await visit(page, '/?vocation=DRUID&slots=open')

  await expect(page.getByLabel(/My vocation/i)).toHaveText(/Druid/)
  await expect(page.getByLabel(/^Slots$/i)).toHaveText(/open slots/i)
  // E a dica que explica o filtro de vocação acompanha.
  await expect(page.locator('main')).toContainText(/Showing teams where a Druid fits/i)
})

test('recarregar mantém o filtro', async ({ page }) => {
  await visit(page, '/')
  await escolher(page, /My vocation/i, /^Knight$/)

  await page.reload()

  await expect(page.getByLabel(/My vocation/i)).toHaveText(/Knight/)
})

test('voltar de um time devolve a busca como estava', async ({ page }) => {
  await visit(page, '/?vocation=DRUID')
  const antes = page.url()

  // Entra num time (qualquer um da lista) e volta.
  const primeiro = page.locator('main a[href^="/teams/"]').first()
  if ((await primeiro.count()) === 0) {
    test.skip(true, 'sem time na busca: rode o ops/ci/seed-e2e.sql')
  }
  await primeiro.click()
  await expect(page).toHaveURL(/\/teams\/\d+/)
  await page.goBack()

  // Era aqui que a lista voltava inteira, do começo.
  await expect(page).toHaveURL(antes)
  await expect(page.getByLabel(/My vocation/i)).toHaveText(/Druid/)
})

test('parâmetro inválido na URL não vira requisição nem frase quebrada', async ({ page }) => {
  const buscas: string[] = []
  page.on('request', (r) => {
    if (r.url().includes('/api/lists/search')) buscas.push(r.url())
  })

  // Entrada de fora: alguém editou a URL, ou um link velho apontou para um valor que
  // deixou de existir.
  await visit(page, '/?vocation=XPTO&creature=abc')

  expect(buscas.join(' ')).not.toContain('vocation=')
  expect(buscas.join(' ')).not.toContain('creatureId=')
  await expect(page.locator('main')).not.toContainText(/XPTO/i)
})
