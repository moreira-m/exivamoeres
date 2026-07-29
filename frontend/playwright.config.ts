import { defineConfig, devices } from '@playwright/test'

/**
 * Testes de navegação: abrem cada página do site num navegador real e reprovam
 * quando ela quebra em silêncio — erro de console, requisição falhando ou tela
 * sem conteúdo.
 *
 * Rodam contra o **build de produção** (`vite preview`), não o dev server: o dev
 * tem avisos que só existem em desenvolvimento e um overlay de erro que esconde a
 * página. O que interessa é o que o usuário recebe.
 *
 * ⚠️ Porta **5173** e nada mais: o CORS do backend libera uma única origem
 * (`FRONTEND_URL`, que por padrão é `http://localhost:5173`). Em qualquer outra
 * porta, toda requisição falha e os testes reprovam por CORS — foi assim que o
 * P13 apareceu.
 */
export default defineConfig({
  testDir: './e2e',
  // Falha cedo e com instrução quando o backend não está de pé.
  globalSetup: './e2e/support/globalSetup.ts',
  // O backend é compartilhado (banco único de dev): dois testes escrevendo ao
  // mesmo tempo se atrapalham. Navegação é rápida, então serial é barato.
  workers: 1,
  fullyParallel: false,
  // Localmente, teste que falha por acaso tem que falhar na hora; no CI, uma
  // repetição absorve lentidão de runner sem esconder falha real (2 tentativas
  // sempre falhando continuam falhando).
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : [['list']],
  timeout: 30_000,
  expect: { timeout: 10_000 },

  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:5173',
    // Idioma fixado no fixture (localStorage), não aqui: o app lê a preferência
    // salva antes de olhar o navegador.
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },

  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
  ],

  // Sobe o site já compilado. Se algo já está na 5173 (um `npm run dev` seu, por
  // exemplo), reaproveita em vez de brigar pela porta.
  webServer: {
    command: 'npm run build && npm run preview -- --port 5173 --strictPort',
    url: 'http://localhost:5173',
    // Localmente, reaproveita um preview que já esteja na 5173 (iterar fica rápido).
    // No CI, nunca: não existe servidor anterior, e reaproveitar esconderia um
    // build que não aconteceu.
    reuseExistingServer: !process.env.CI,
    timeout: 120_000,
    stdout: 'ignore',
    stderr: 'pipe',
  },
})
