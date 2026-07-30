#!/usr/bin/env node
/**
 * Confere os arquivos de tradução. Duas perguntas, com pesos diferentes:
 *
 *  1. **Paridade** (`erro`, reprova): toda chave existe nos dois idiomas?
 *     Isto é o que o usuário vê. O `i18next` está com `fallbackLng: 'pt'`, então
 *     chave que falta no `en` mostra **português numa interface inglesa**, e chave
 *     que falta no `pt` mostra a **chave crua** (`teamDetail.suggestions`) na tela.
 *
 *  2. **Chave órfã** (`aviso`, não reprova): existe na tradução e ninguém usa.
 *     É higiene, não bug: string morta faz o próximo leitor achar que a feature
 *     existe. Não reprova porque a heurística de "usada" nunca é perfeita — ver
 *     abaixo — e um aviso que reprova por engano é um aviso que alguém desliga.
 *
 * Rodar: `npm run check:i18n` (entra no `npm run build` e no CI).
 */
import { readFileSync, readdirSync, statSync } from 'node:fs'
import { join, dirname } from 'node:path'

const AQUI = dirname(new URL(import.meta.url).pathname)
const LOCALES = join(AQUI, '..', 'src', 'i18n', 'locales')
const SRC = join(AQUI, '..', 'src')

/** Achata `{a: {b: 1}}` em `['a.b']`. */
function chaves(objeto, prefixo = '') {
  return Object.entries(objeto).flatMap(([k, v]) =>
    v && typeof v === 'object' ? chaves(v, `${prefixo}${k}.`) : [`${prefixo}${k}`],
  )
}

function arquivosDe(dir) {
  return readdirSync(dir).flatMap((nome) => {
    const caminho = join(dir, nome)
    return statSync(caminho).isDirectory() ? arquivosDe(caminho) : [caminho]
  })
}

const codigo = arquivosDe(SRC)
  .filter((f) => /\.(ts|tsx)$/.test(f))
  .map((f) => readFileSync(f, 'utf8'))
  .join('\n')

// Qualquer string com forma de chave (`a.b`) escrita no código conta como uso —
// **não** só a que aparece logo depois de `t(`. O motivo é concreto: o `ThemeToggle`
// faz `t(cond ? 'nav.themeLight' : 'nav.themeDark')`, e uma regra ancorada em `t(`
// marcaria as duas como órfãs. Chave também aparece em `Trans`, em constante e em
// ternário — a forma da string é o sinal mais confiável que dá para ler sem AST.
//
// O preço: chave citada num comentário conta como usada. Para um **aviso** de higiene,
// errar para "quieto demais" é melhor que errar para "grita errado".
const literais = new Set(
  [...codigo.matchAll(/['"`](\w+(?:\.\w+)+)['"`]/g)].map((m) => m[1]),
)
// Chaves montadas: `a.b.${x}` → tudo sob `a.b.` conta como usado. Sem isto, as
// vocações, os tipos de notificação e os status seriam todos "órfãos".
const prefixos = [...codigo.matchAll(/[`']([\w.]+\.)\$\{/g)].map((m) => m[1])

/** Sufixos de plural do i18next: a chave usada é a base. */
const PLURAL = /_(zero|one|two|few|many|other)$/

function usada(chave) {
  const base = chave.replace(PLURAL, '')
  return (
    literais.has(chave) ||
    literais.has(base) ||
    prefixos.some((p) => chave.startsWith(p))
  )
}

const idiomas = Object.fromEntries(
  readdirSync(LOCALES)
    .filter((f) => f.endsWith('.json'))
    .map((f) => [f.replace('.json', ''), JSON.parse(readFileSync(join(LOCALES, f), 'utf8'))]),
)
const nomes = Object.keys(idiomas)
if (nomes.length < 2) {
  console.error(`i18n: esperava pelo menos dois idiomas em ${LOCALES}, achei ${nomes}`)
  process.exit(1)
}

const conjuntos = Object.fromEntries(nomes.map((n) => [n, new Set(chaves(idiomas[n]))]))
const todas = new Set(nomes.flatMap((n) => [...conjuntos[n]]))

// ----- 1. paridade -----
const faltando = []
for (const chave of [...todas].sort()) {
  const ausentes = nomes.filter((n) => !conjuntos[n].has(chave))
  if (ausentes.length > 0) faltando.push(`${chave} — falta em: ${ausentes.join(', ')}`)
}

// ----- 2. órfãs -----
const orfas = [...todas].sort().filter((chave) => !usada(chave))

const total = todas.size
console.log(`i18n: ${total} chaves · idiomas: ${nomes.join(', ')}`)

if (orfas.length > 0) {
  console.warn(`\ni18n: ${orfas.length} chave(s) sem uso em src/ (aviso, não reprova):`)
  for (const o of orfas) console.warn(`  - ${o}`)
  console.warn(
    '  A heurística lê a forma da string no código-fonte. Se a chave é montada de um\n' +
      '  jeito que ela não enxerga, escreva o prefixo literal — `prefixo.${x}` — que é\n' +
      '  o que ela sabe ler.\n' +
      '  Se a chave existe para uma tela que nunca foi ligada, registre isso no\n' +
      '  NEXT_STEPS — aviso sem explicação é aviso que alguém apaga sem pensar.',
  )
}

if (faltando.length > 0) {
  console.error(`\ni18n: ${faltando.length} chave(s) fora de paridade:`)
  for (const f of faltando) console.error(`  - ${f}`)
  console.error(
    '\nChave só em um idioma vira texto no idioma errado (ou a chave crua) na tela.\n' +
      'Acrescente nos dois, ou apague dos dois.',
  )
  process.exit(1)
}

console.log('i18n: paridade ok')
