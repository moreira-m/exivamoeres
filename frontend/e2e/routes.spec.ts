import { readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { expect, test } from './support/fixtures'
import { ALL_ROUTES, ROUTES_OUT_OF_SCOPE } from './routes'

/**
 * Paridade entre o `App.tsx` e o inventário de `routes.ts`.
 *
 * Sem isto, o suíte envelhece do pior jeito possível: rota nova entra no app,
 * ninguém a acrescenta aqui, e a varredura continua **verde** cobrindo menos.
 * É o mesmo raciocínio do `TESTS.md` — o que não está inventariado não é
 * verificado.
 */

const AQUI = dirname(new URL(import.meta.url).pathname)

function rotasDeclaradasNoApp(): string[] {
  const app = readFileSync(join(AQUI, '..', 'src', 'App.tsx'), 'utf8')
  return [...app.matchAll(/path="([^"]+)"/g)].map((m) => m[1])
}

test('toda rota do App.tsx está no inventário (ou justificada como fora de escopo)', () => {
  const cobertas = new Set(ALL_ROUTES.map((r) => r.path))
  const justificadas = new Set(Object.keys(ROUTES_OUT_OF_SCOPE))
  // A rota `*` é a página de "não encontrado", coberta por rota-desconhecida.spec.ts.
  const naoVerificadas = rotasDeclaradasNoApp().filter(
    (path) => path !== '*' && !cobertas.has(path) && !justificadas.has(path),
  )

  expect(
    naoVerificadas,
    'Rotas no App.tsx sem teste de navegação. Acrescente em e2e/routes.ts ' +
      '(ou em ROUTES_OUT_OF_SCOPE, com o motivo).',
  ).toEqual([])
})

test('o App.tsx tem a rota de "não encontrado"', () => {
  // A ausência dela é o bug da tela em branco. Um `git revert` distraído a
  // remove sem quebrar nenhuma outra asserção — esta existe para isso.
  expect(rotasDeclaradasNoApp()).toContain('*')
})

test('nenhuma rota do inventário aponta para o mesmo caminho duas vezes', () => {
  const paths = ALL_ROUTES.map((r) => r.path)
  expect(new Set(paths).size).toBe(paths.length)
})
