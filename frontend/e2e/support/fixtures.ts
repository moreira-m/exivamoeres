import { test as base, expect, type Page } from '@playwright/test'
import {
  describeProblems,
  findBrokenImages,
  watchForProblems,
  type ProblemWatcher,
} from './pageProblems'
import { API_BASE, ensureSession, type Session } from './session'

/**
 * Fixtures do suíte de navegação. Duas coisas valem para **todo** teste, sem ele
 * pedir:
 *
 * - `problems` fica ligado e, no fim do teste, reprova se algo apareceu. Assim
 *   nenhum teste "esquece" de conferir o console — o silêncio é verificado.
 * - o idioma é fixado em inglês, porque as asserções olham texto e o app cai no
 *   idioma do navegador quando não há preferência salva.
 */
interface Fixtures {
  problems: ProblemWatcher
  /** Página já com sessão válida — para as rotas de `/account/*`. */
  loggedPage: Page
  session: Session
}

export const test = base.extend<Fixtures>({
  problems: [
    async ({ page }, use) => {
      const watcher = watchForProblems(page)
      await page.addInitScript(() => localStorage.setItem('exivamoeres-lang', 'en'))
      await use(watcher)

      // Imagem quebrada não é evento: tem que ser **perguntado** à página no fim, e
      // só faz sentido se alguma foi aberta (teste de unidade de rota não abre).
      const imagensQuebradas = page.url().startsWith('http')
        ? await findBrokenImages(page)
        : []
      const encontrados = [...watcher.list(), ...imagensQuebradas]
      expect(
        encontrados,
        `A página acusou ${encontrados.length} problema(s) que o usuário não vê:\n` +
          describeProblems(encontrados),
      ).toEqual([])
    },
    { auto: true },
  ],

  session: async ({}, use) => {
    await use(await ensureSession())
  },

  loggedPage: async ({ page, session }, use) => {
    // Injeta a sessão no formato do `persist` do Zustand (store `authStore`).
    // Fazer login pela tela em cada teste seria mais fiel e muito mais lento — e
    // o que está sob teste aqui é a página, não o formulário de login.
    await page.addInitScript((state) => {
      localStorage.setItem('exivamoeres-auth', JSON.stringify({ state, version: 0 }))
    }, session)
    await use(page)
  },
})

export { expect, API_BASE }

/**
 * Abre a rota e espera a tela **assentar**: o React Query resolve as queries
 * iniciais e o spinner sai. Sem isso, um teste rápido leria a tela no meio do
 * carregamento e chamaria de "sem conteúdo".
 */
export async function visit(page: Page, path: string) {
  await page.goto(path)
  await expect(
    page.locator('main'),
    `${path} não renderizou nem o <main>: é a tela em branco (rota que não casa ` +
      `nenhum <Route>, ou erro de render antes do primeiro pixel).`,
  ).toBeVisible()
  // O spinner tem role="status": esperar ele sair evita julgar "sem conteúdo"
  // uma página que só estava carregando.
  await expect(page.getByRole('status')).toHaveCount(0)
}
