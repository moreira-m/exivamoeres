import { ALL_ROUTES } from './routes'
import { expect, test, visit } from './support/fixtures'

/**
 * O bug que motivou este suíte: **endereço que não existe renderizava nada.**
 *
 * O `<Routes>` do React Router não tinha rota `*`, então qualquer URL fora da
 * lista (link antigo, digitação, página removida — `/account/soulcores` é o caso
 * real que apareceu) não casava nenhuma rota e o React renderizava vazio: tela
 * branca, sem cabeçalho, sem erro no console e com **200** na rede. Nenhuma
 * ferramenta de monitoramento vê isso; só um humano abrindo a página.
 *
 * Estes testes existem para essa classe de falha não voltar em silêncio.
 */

const ENDERECOS_QUE_NAO_EXISTEM = [
  '/account/soulcores', // o relato original
  '/soulcores',
  '/account/teams/9999999999', // rota logada com sufixo que ninguém tratou
  '/pagina-que-nunca-existiu',
]

for (const path of ENDERECOS_QUE_NAO_EXISTEM) {
  test(`${path} mostra "página não encontrada" em vez de tela branca`, async ({ page }) => {
    await visit(page, path)

    // A prova de que não é tela branca: o cabeçalho e o conteúdo estão lá.
    await expect(page.locator('main')).toContainText(/Page not found/i)
    // E a saída existe — beco sem saída também é defeito.
    await expect(page.getByRole('link', { name: /Go to the home page/i })).toBeVisible()
  })
}

test('a página de erro repete o endereço pedido (para o usuário saber o que errou)', async ({
  page,
}) => {
  await visit(page, '/account/soulcores')
  await expect(page.locator('main')).toContainText('/account/soulcores')
})

test('nenhuma rota conhecida cai na página de "não encontrado"', async ({ page }) => {
  // O contrário do teste acima: a rota `*` não pode engolir rota legítima —
  // é o jeito mais fácil de "consertar" a tela branca e quebrar o site inteiro.
  for (const route of ALL_ROUTES) {
    await page.goto(route.path)
    await expect(page.locator('main'), `${route.path} caiu na página de não encontrado`).not.toContainText(
      /Page not found/i,
    )
  }
})
