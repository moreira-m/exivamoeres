import type { Locator, Page } from '@playwright/test'
import { expect, test, visit } from './support/fixtures'

/**
 * "Sign in" aparece três vezes na tela (link do cabeçalho, aba de login/registro e o
 * submit), então o seletor é escopado ao formulário — e não à ordem em que aparecem,
 * que muda com o layout.
 */
function botaoDeEntrar(page: Page): Locator {
  return page.locator('form').getByRole('button', { name: /^Sign in$/i })
}

async function preencherEEnviar(page: Page) {
  await page.getByLabel(/^Email$/i).fill('nao-existe@exemplo.local')
  await page.getByLabel(/^Password$/i).fill('senha-errada')
  await botaoDeEntrar(page).click()
}

/**
 * A tela de login é a única superfície visível da mudança de contrato do
 * [S12](../../NEXT_STEPS.md): senha errada passou a responder **401** em vez de 422.
 *
 * O interceptor do `apiClient` ignora `/api/auth/*` de propósito (para não tentar
 * refresh do refresh), então 401 aqui **não** pode disparar renovação de sessão nem
 * limpar nada — tem que virar mensagem na tela, e nada mais. Este teste existe para
 * essa garantia não depender de leitura de código.
 */

test('senha errada mostra a mensagem e não quebra a tela', async ({ page, problems }) => {
  // O 401 é a resposta **correta** aqui — é o que o teste veio verificar.
  problems.allow(/HTTP 401 — POST .*\/api\/auth\/login/)

  await visit(page, '/login')
  await preencherEEnviar(page)

  // A mensagem é a do backend, e ela vem em **português** numa interface em inglês:
  // é a dívida do T2 (códigos de erro em vez de frases), visível aqui de propósito.
  await expect(page.getByText('Email ou senha incorretos')).toBeVisible()
  // Continua na tela de login, com o formulário utilizável — não virou tela branca
  // nem redirecionou para lugar nenhum.
  await expect(page).toHaveURL(/\/login$/)
  await expect(botaoDeEntrar(page)).toBeEnabled()
})

test('o 401 do login não mexe na sessão guardada', async ({ page, problems }) => {
  problems.allow(/HTTP 401 — POST .*\/api\/auth\/login/)

  await visit(page, '/login')
  await page.evaluate(() => localStorage.setItem('marcador-de-teste', 'intacto'))

  await preencherEEnviar(page)
  await expect(page.getByText('Email ou senha incorretos')).toBeVisible()

  // Se o interceptor tratasse este 401 como "token expirado", ele chamaria refresh e
  // limparia o `localStorage` inteiro. O marcador provar que ninguém mexeu ali é mais
  // direto que inspecionar o store do Zustand.
  expect(await page.evaluate(() => localStorage.getItem('marcador-de-teste')))
    .toBe('intacto')
})
