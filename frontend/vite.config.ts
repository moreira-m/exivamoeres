import { writeFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { loadEnv, type Plugin } from 'vite'
import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'
import { arquivoHeadersDoNetlify, cabecalhosDeSeguranca } from './src/security/headers'

/**
 * Escreve o `dist/_headers` que o Netlify lê, a partir da mesma função que
 * configura o dev e o preview (`src/security/headers.ts`).
 *
 * Por que um plugin e não um script separado no `npm run build`: assim o arquivo
 * sai em **todo** `vite build`, inclusive quando alguém roda o build direto. Passo
 * extra em script é passo que se esquece de copiar.
 */
function headersDoNetlify(apiUrl: string): Plugin {
  return {
    name: 'exivamoeres:headers-do-netlify',
    apply: 'build',
    writeBundle(options) {
      const destino = resolve(options.dir ?? 'dist', '_headers')
      writeFileSync(destino, arquivoHeadersDoNetlify(
        cabecalhosDeSeguranca({ apiUrl, producao: true }),
      ))
    },
  }
}

export default defineConfig(({ mode }) => {
  // `loadEnv` lê o .env como o app lê: a CSP é montada com a **mesma** origem de
  // API que o cliente vai chamar.
  const env = loadEnv(mode, process.cwd(), 'VITE_')
  const apiUrl = env.VITE_API_URL
  const cabecalhosLocais = cabecalhosDeSeguranca({ apiUrl })

  return {
    plugins: [react(), headersDoNetlify(apiUrl)],
    // Os mesmos cabeçalhos em dev e em preview. É o que faz a CSP ser testável:
    // a suíte de navegação roda contra o preview e **reprova em erro de console**,
    // então uma diretiva apertada demais aparece como teste vermelho aqui, não
    // como tela quebrada em produção.
    server: { headers: cabecalhosLocais },
    preview: { headers: cabecalhosLocais },
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
  }
})
