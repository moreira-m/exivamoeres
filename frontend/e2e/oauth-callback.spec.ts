import { expect, test } from './support/fixtures'

/**
 * `/oauth/callback` é a rota que mais depende do roteador: ela **navega por
 * código** (`useNavigate(..., { replace: true })`) a partir de um `useEffect`,
 * lendo o token do **fragmento** da URL — que nunca chega ao servidor.
 *
 * Por isso ela ganhou teste junto com o upgrade do `react-router` 6 → 7
 * ([S10 passo 2](../../NEXT_STEPS.md#s10--dependências-do-frontend-com-vulnerabilidade-conhecida)):
 * era o ponto que o item mandava revalidar na mão, e "navegar por código de dentro
 * de um efeito" é exatamente o comportamento que um major de roteador pode mudar.
 *
 * Os dois caminhos de **recusa** ficam aqui. O caminho feliz depende de um
 * `refresh_token` válido, e usá-lo o **rotaciona** (o backend invalida o antigo —
 * regra do S1), o que queimaria a sessão em cache do suíte a cada execução. Ele
 * segue no checklist manual do `TESTS.md`, junto do login social de verdade.
 */

test('sem fragmento nenhum, cai no login (e não numa tela em branco)', async ({ page }) => {
  await page.goto('/oauth/callback')

  await expect(page).toHaveURL(/\/login$/)
  await expect(page.locator('main')).toContainText(/Continue as guest/i)
})

test('com refresh token inválido, cai no login', async ({ page, problems }) => {
  // Token forjado é recusado pelo backend — é o que o `.catch` da página trata.
  // ⚠️ A recusa vem como **422**, não 401: `/api/auth/refresh` trata token
  // inválido como violação de regra de negócio (a convenção de 422 do projeto).
  // Foi este teste que mostrou isso; a divergência ficou registrada como S12.
  problems.allow(/HTTP 422 — POST .*\/api\/auth\/refresh/)

  await page.goto('/oauth/callback#refresh_token=nao-e-um-token-de-verdade')

  await expect(page).toHaveURL(/\/login$/)
  await expect(page.locator('main')).toContainText(/Continue as guest/i)
})

test('o histórico não guarda o callback (o botão voltar não volta para ele)', async ({ page }) => {
  // `replace: true` existe para isso: sem ele, voltar reabriria a callback com o
  // token já usado e o usuário cairia no login sem entender.
  await page.goto('/')
  await page.goto('/oauth/callback')
  await expect(page).toHaveURL(/\/login$/)

  await page.goBack()

  await expect(page).toHaveURL(/localhost:5173\/$/)
})
