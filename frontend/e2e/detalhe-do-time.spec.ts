import { API_BASE, expect, test, visit } from './support/fixtures'

/**
 * A página do time (`/teams/:id`) não entra na varredura de `routes.ts` porque
 * depende de um time existir. Aqui o id vem da própria API, e os dois casos que
 * o [P13](../../new-features/estado-de-erro-nas-telas.md) separou são checados:
 * time que existe × id que não existe (que **não** é falha e tem texto próprio).
 */

async function primeiroTimeDaBusca(): Promise<number | null> {
  const response = await fetch(`${API_BASE}/api/lists/search?size=1`)
  if (!response.ok) return null
  const page = (await response.json()) as { content: { id: number }[] }
  return page.content[0]?.id ?? null
}

test('o time da busca abre com conteúdo, sem erro de console nem de rede', async ({ page }) => {
  const id = await primeiroTimeDaBusca()
  test.skip(id === null, 'Nenhum time ativo no banco de dev — nada para abrir.')

  await visit(page, `/teams/${id}`)

  const main = page.locator('main')
  await expect(main).toContainText(/World/i)
  await expect(main).toContainText(/members/i)
  await expect(page.getByText(/Couldn't load this/i)).toHaveCount(0)
})

test('id que não existe diz "time não encontrado" (e não "não deu para carregar")', async ({
  page,
  problems,
}) => {
  // Aqui o 404 é a resposta **correta** da API, não defeito da página — sem este
  // `allow` o teste reprovaria justamente pelo que veio verificar.
  problems.allow(/HTTP 404 — GET .*\/api\/lists\/99999999/)

  await visit(page, '/teams/99999999')
  await expect(page.locator('main')).toContainText(/Team not found/i)
})

test('id que não é número não vira requisição nem "erro do servidor"', async ({ page }) => {
  // `/teams/abc` chegava ao backend como `/api/lists/NaN`, que responde 500: a
  // tela ficava ~20s em "carregando" (tentativas do React Query) e terminava
  // exibindo "Erro interno inesperado" para um endereço que simplesmente não é
  // um time. O `visit` já reprova o "carregando" eterno; aqui garantimos também
  // que nenhuma requisição de time saiu.
  const pedidosDeTime: string[] = []
  page.on('request', (request) => {
    if (request.url().includes('/api/lists/')) pedidosDeTime.push(request.url())
  })

  await visit(page, '/teams/abc')

  await expect(page.locator('main')).toContainText(/Team not found/i)
  expect(pedidosDeTime, 'o app pediu um time com id inválido').toEqual([])
})
