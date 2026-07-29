import { API_BASE, expect, test, visit } from './support/fixtures'

/**
 * O `ErrorBoundary` ([T3](../../NEXT_STEPS.md)) existe para uma coisa só: **erro de
 * render não pode virar tela branca.**
 *
 * Testar isso exige provocar uma exceção durante o render, e o jeito honesto é o
 * mesmo que aconteceu de verdade: **payload que o app não esperava**. Aqui a
 * resposta é a real da API, com a composição corrompida (`slots: [null]`) — passa
 * pelo normalizador (é um array de verdade) e estoura no cartão que percorre as
 * vagas.
 *
 * ⚠️ Não confunda com `QueryError` (requisição que **falha**), que é testado nas
 * outras varreduras: falha de rede é *estado* e nunca chega ao boundary.
 */

async function primeiroTime(): Promise<number | null> {
  const response = await fetch(`${API_BASE}/api/lists/search?size=1`)
  if (!response.ok) return null
  const page = (await response.json()) as { content: { id: number }[] }
  return page.content[0]?.id ?? null
}

test('erro num cartão secundário derruba só o cartão, não a página (T9)', async ({
  page,
  problems,
}) => {
  // Este teste era o inverso: antes, `slots: [null]` trocava a **página inteira** pela
  // tela de falha. Com o boundary de seção, o time continua legível e só a composição
  // vira aviso — melhor que "algo deu errado" no lugar de tudo.
  const id = await primeiroTime()
  test.skip(id === null, 'Nenhum time ativo no banco de dev — nada para abrir.')

  problems.allow(/Cannot read properties of null/)
  problems.allow(/\[ErrorBoundary\]/)

  const detalhe = (await (await fetch(`${API_BASE}/api/lists/${id}`)).json()) as {
    summary: Record<string, unknown>
  }
  detalhe.summary.slots = [null]
  await page.route(`**/api/lists/${id}`, (route) =>
    route.fulfill({
      status: 200,
      headers: { 'content-type': 'application/json', 'access-control-allow-origin': '*' },
      body: JSON.stringify(detalhe),
    }),
  )

  await page.goto(`/teams/${id}`)

  // O aviso do tamanho do bloco, nomeando o que faltou.
  await expect(page.getByText(/Couldn't show: Team composition/i)).toBeVisible()
  // E o time continua na tela: identidade, mundo e membros.
  await expect(page.locator('main')).toContainText(/World/i)
  await expect(page.locator('main')).toContainText(/members/i)
  // A tela cheia de falha **não** aparece.
  await expect(page.getByText(/Something went wrong on this screen/i)).toHaveCount(0)
})

test('erro fora de qualquer seção mostra a tela de falha inteira', async ({
  page,
  problems,
}) => {
  const id = await primeiroTime()
  test.skip(id === null, 'Nenhum time ativo no banco de dev — nada para abrir.')

  // A exceção é o **objetivo** do teste: o boundary a registra no console (com o
  // componentStack, que é o que localiza a tela) e o React também. O que não pode
  // é a página ficar vazia.
  problems.allow(/Cannot read properties of null/)
  problems.allow(/\[ErrorBoundary\]/)

  // `members` é percorrido **fora** de qualquer boundary de seção (é bloco essencial:
  // sem membros a página do time não tem propósito), então um item inválido ali é o
  // caminho para a tela de falha inteira.
  const detalhe = (await (await fetch(`${API_BASE}/api/lists/${id}`)).json()) as {
    members: unknown[]
  }
  detalhe.members = [null]

  await page.route(`**/api/lists/${id}`, (route) =>
    route.fulfill({
      status: 200,
      headers: { 'content-type': 'application/json', 'access-control-allow-origin': '*' },
      body: JSON.stringify(detalhe),
    }),
  )

  await page.goto(`/teams/${id}`)

  await expect(page.getByText(/Something went wrong on this screen/i)).toBeVisible()
  // As duas saídas: sem elas o usuário fica preso numa tela que só informa.
  await expect(page.getByRole('button', { name: /Reload the page/i })).toBeVisible()
  await expect(page.getByRole('link', { name: /Go to the home page/i })).toBeVisible()
})

test('a saída da tela de falha leva de volta para a home funcionando', async ({
  page,
  problems,
}) => {
  const id = await primeiroTime()
  test.skip(id === null, 'Nenhum time ativo no banco de dev — nada para abrir.')

  problems.allow(/Cannot read properties of null/)
  problems.allow(/\[ErrorBoundary\]/)

  const detalhe = (await (await fetch(`${API_BASE}/api/lists/${id}`)).json()) as {
    members: unknown[]
  }
  detalhe.members = [null]
  await page.route(`**/api/lists/${id}`, (route) =>
    route.fulfill({
      status: 200,
      headers: { 'content-type': 'application/json', 'access-control-allow-origin': '*' },
      body: JSON.stringify(detalhe),
    }),
  )

  await page.goto(`/teams/${id}`)
  await page.getByRole('link', { name: /Go to the home page/i }).click()

  // É navegação de verdade (`<a href>`), não do roteador: um `<Link>` daqui
  // deixaria o boundary em estado de erro e o usuário preso na tela de falha.
  await expect(page.locator('main')).toContainText(/Find your Soul Core team/i)
})

test('página normal não mostra a tela de falha', async ({ page }) => {
  // O contrário do teste acima: boundary que dispara sem motivo esconderia o site
  // inteiro — é o jeito mais fácil de "consertar" tela branca e piorar tudo.
  await visit(page, '/')
  await expect(page.getByText(/Something went wrong on this screen/i)).toHaveCount(0)
})
