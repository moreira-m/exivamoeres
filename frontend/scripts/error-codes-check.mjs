#!/usr/bin/env node
/**
 * Confere que o **espelho** dos códigos de erro não mentiu.
 *
 * O `ErrorCode.java` é a fonte; o frontend repete a lista em dois lugares (a união
 * `ErrorCode` de `types/api.ts` e as chaves `errors.codes.*` de cada idioma) porque não há
 * geração de código entre back e front neste projeto.
 *
 * **Por que isto existe.** Espelho copiado à mão é espelho que atrasa: código novo no Java
 * saía sem frase, o `getApiErrorMessage` caía na reserva em português, e ninguém descobria
 * até alguém usando o site em inglês tomar aquela recusa específica. Não havia teste
 * possível — o `it.each` de `apiError.test.ts` varre a lista **do frontend**, então um
 * código que nunca chegou lá também nunca era conferido.
 *
 * As três perguntas, todas reprovando:
 *
 *  1. todo valor do enum Java está na união do TypeScript?
 *  2. todo valor da união tem frase nos **dois** idiomas?
 *  3. existe frase (ou valor na união) para código que **não existe mais** no Java?
 *
 * A terceira é higiene com dente: código removido do backend deixa frase órfã, e frase
 * órfã faz o próximo leitor achar que a recusa ainda existe.
 *
 * Rodar: `npm run check:codes` (entra no `npm run build`, logo no CI).
 */
import { readFileSync } from 'node:fs'
import { join, dirname } from 'node:path'

const AQUI = dirname(new URL(import.meta.url).pathname)
const ENUM_JAVA = join(AQUI, '..', '..', 'backend', 'src', 'main', 'java', 'com',
  'exivamoeres', 'dto', 'error', 'ErrorCode.java')
const TIPOS_TS = join(AQUI, '..', 'src', 'types', 'api.ts')
const LOCALES = join(AQUI, '..', 'src', 'i18n', 'locales')

function ler(caminho) {
  try {
    return readFileSync(caminho, 'utf8')
  } catch (e) {
    // ⚠️ Ausência do arquivo Java **não** reprova: o frontend é publicado sozinho (Netlify),
    // e um build de frontend não pode depender de o backend estar no mesmo checkout. No CI e
    // no repositório inteiro ele está lá — que é onde a checagem importa.
    if (e.code === 'ENOENT' && caminho === ENUM_JAVA) {
      console.warn('codes: ErrorCode.java não encontrado — conferindo só o lado do frontend')
      return null
    }
    throw e
  }
}

/** Os valores do enum: linhas com só um identificador em CAIXA_ALTA. */
function codigosDoJava(fonte) {
  return [...fonte.matchAll(/^\s{4}([A-Z][A-Z0-9_]*)\s*,?\s*$/gm)].map((m) => m[1])
}

/** Os membros da união `export type ErrorCode = | 'A' | 'B'`. */
function codigosDoTypeScript(fonte) {
  const bloco = fonte.match(/export type ErrorCode =([\s\S]*?)\n\n/)
  if (!bloco) {
    console.error("codes: não achei `export type ErrorCode =` em types/api.ts")
    process.exit(1)
  }
  return [...bloco[1].matchAll(/'([A-Z][A-Z0-9_]*)'/g)].map((m) => m[1])
}

const fonteJava = ler(ENUM_JAVA)
const doJava = fonteJava ? codigosDoJava(fonteJava) : null
const doTs = codigosDoTypeScript(ler(TIPOS_TS))

const IDIOMAS = ['pt', 'en']
const frases = Object.fromEntries(
  IDIOMAS.map((i) => [i, JSON.parse(ler(join(LOCALES, `${i}.json`))).errors?.codes ?? {}]),
)

const erros = []

if (doJava) {
  for (const code of doJava) {
    if (!doTs.includes(code)) {
      erros.push(`${code}: está no ErrorCode.java e falta na união de types/api.ts`)
    }
  }
  for (const code of doTs) {
    if (!doJava.includes(code)) {
      erros.push(`${code}: está na união de types/api.ts e não existe no ErrorCode.java`)
    }
  }
}

for (const code of doTs) {
  for (const idioma of IDIOMAS) {
    if (!frases[idioma][code]) {
      erros.push(`${code}: sem frase em ${idioma}.json (errors.codes.${code})`)
    }
  }
}

for (const idioma of IDIOMAS) {
  for (const code of Object.keys(frases[idioma])) {
    if (!doTs.includes(code)) {
      erros.push(`${code}: tem frase em ${idioma}.json e não está na união de types/api.ts`)
    }
  }
}

if (erros.length > 0) {
  console.error(`\ncodes: ${erros.length} problema(s) no espelho dos códigos de erro:`)
  for (const e of erros) console.error(`  - ${e}`)
  console.error(
    '\nA fonte é o `backend/.../dto/error/ErrorCode.java`. Valor novo lá precisa de:\n' +
      '  1. o valor na união `ErrorCode` de `src/types/api.ts`;\n' +
      '  2. a chave `errors.codes.<CODE>` em `pt.json` **e** `en.json`.\n' +
      'Sem os dois, a recusa cai na frase em português — e quem usa o site em inglês\n' +
      'lê português sem ninguém saber.',
  )
  process.exit(1)
}

console.log(`codes: ${doTs.length} códigos espelhados${doJava ? ' (Java, união e 2 idiomas)' : ' (só frontend)'} ok`)
