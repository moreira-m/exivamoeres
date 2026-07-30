import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  test: {
    // jsdom porque estes são testes de **componente**: renderizam de verdade e
    // consultam a árvore. Navegador de verdade é a suíte de `e2e/` — as duas
    // respondem perguntas diferentes (ver TESTS.md §2).
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    // Só `src/`: `e2e/` é Playwright e tem runner próprio. Sem isto, o Vitest
    // tenta rodar os `.spec.ts` do Playwright e falha em import.
    include: ['src/**/*.test.{ts,tsx}'],
    css: false,
    // Cada teste começa com os mocks **zerados** — implementação e histórico de
    // chamadas. Aqui isto não é higiene abstrata: com o histórico vazando,
    // `mock.calls[0]` de um teste é a chamada de um teste **anterior**, e a
    // asserção passa lendo o dado errado (foi exatamente o que aconteceu ao
    // escrever esta suíte).
    mockReset: true,
  },
})
