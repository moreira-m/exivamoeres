import { describe, expect, it } from 'vitest'
import { arquivoHeadersDoNetlify, cabecalhosDeSeguranca } from './headers'

/** A diretiva pedida, de dentro do valor do `Content-Security-Policy`. */
function diretiva(csp: string, nome: string): string {
  const encontrada = csp.split('; ').find((d) => d.startsWith(`${nome} `) || d === nome)
  if (!encontrada) throw new Error(`diretiva ausente: ${nome} (csp: ${csp})`)
  return encontrada
}

const PROD = { apiUrl: 'https://api.exivamoeres.app' }
const LOCAL = { apiUrl: 'http://localhost:8080' }

describe('cabecalhosDeSeguranca', () => {
  it('libera a API e o WebSocket do chat em connect-src', () => {
    const { 'Content-Security-Policy': csp } = cabecalhosDeSeguranca(PROD)

    // As duas juntas: a API é http(s) e o chat é wss no **mesmo** host. Esquecer o
    // wss deixa o site inteiro funcionando e só o chat morto — o modo de falha
    // mais fácil de não notar em produção.
    expect(diretiva(csp, 'connect-src')).toBe(
      "connect-src 'self' https://api.exivamoeres.app wss://api.exivamoeres.app",
    )
  })

  it('em ambiente local, o WebSocket e ws:// e a porta e preservada', () => {
    const { 'Content-Security-Policy': csp } = cabecalhosDeSeguranca(LOCAL)

    expect(diretiva(csp, 'connect-src')).toBe(
      "connect-src 'self' http://localhost:8080 ws://localhost:8080",
    )
  })

  it('libera os sprites da TibiaData, e nenhum outro host de imagem', () => {
    const { 'Content-Security-Policy': csp } = cabecalhosDeSeguranca(PROD)

    // `img-src` é a única diretiva com host externo: o catálogo de criaturas
    // aponta para static.tibia.com.
    expect(diretiva(csp, 'img-src')).toBe("img-src 'self' https://static.tibia.com data:")
  })

  it('nao permite script inline nem eval', () => {
    const { 'Content-Security-Policy': csp } = cabecalhosDeSeguranca(PROD)

    // É a diretiva que justifica a entrega: a sessão vive em `localStorage`, então
    // script injetado que executa é conta roubada. Afrouxar aqui devolve o furo.
    expect(diretiva(csp, 'script-src')).toBe("script-src 'self'")
    expect(csp).not.toContain('unsafe-inline')
    expect(csp).not.toContain('unsafe-eval')
  })

  it('fecha embutir e ser embutido, base-uri e objetos', () => {
    const { 'Content-Security-Policy': csp, 'X-Frame-Options': frame } =
      cabecalhosDeSeguranca(PROD)

    expect(diretiva(csp, 'frame-ancestors')).toBe("frame-ancestors 'none'")
    expect(diretiva(csp, 'base-uri')).toBe("base-uri 'none'")
    expect(diretiva(csp, 'object-src')).toBe("object-src 'none'")
    // Navegador que ignora `frame-ancestors` ainda respeita este.
    expect(frame).toBe('DENY')
  })

  it('HSTS so em producao', () => {
    // Em http o navegador ignora o header, mas mandá-lo de localhost é dar a
    // alguém um problema que não existe.
    expect(cabecalhosDeSeguranca(LOCAL)['Strict-Transport-Security']).toBeUndefined()
    expect(cabecalhosDeSeguranca({ ...PROD, producao: true })['Strict-Transport-Security'])
      .toBe('max-age=31536000; includeSubDomains')
  })

  it('sem VITE_API_URL, falha na hora em vez de gerar CSP quebrada', () => {
    // O modo de falha que isto evita: `connect-src 'self' undefined` — site que
    // sobe, não carrega dado nenhum, e não parece problema de configuração.
    expect(() => cabecalhosDeSeguranca({ apiUrl: '' })).toThrow(/VITE_API_URL/)
    expect(() => cabecalhosDeSeguranca({ apiUrl: 'nao é url' })).toThrow(/URL absoluta/)
    // Sem o `http://` — o erro que se comete preenchendo o painel. O `new URL`
    // aceita isto em silêncio (protocolo "localhost:"), então a checagem de
    // protocolo é o que segura.
    expect(() => cabecalhosDeSeguranca({ apiUrl: 'localhost:8080' })).toThrow(/http:\/\//)
  })
})

describe('arquivoHeadersDoNetlify', () => {
  it('gera o formato do Netlify: caminho e cabecalhos indentados', () => {
    const arquivo = arquivoHeadersDoNetlify(cabecalhosDeSeguranca({ ...PROD, producao: true }))

    expect(arquivo).toContain('/*\n')
    // Indentação não é estética: cabeçalho sem os dois espaços o Netlify ignora
    // em silêncio, e o deploy sobe sem proteção nenhuma.
    for (const linha of arquivo.split('\n').filter((l) => l && !l.startsWith('#') && l !== '/*')) {
      expect(linha.startsWith('  ')).toBe(true)
    }
    expect(arquivo).toContain('  Content-Security-Policy: default-src')
    expect(arquivo).toContain('  Strict-Transport-Security: ')
  })

  it('avisa que e gerado, e por quem', () => {
    const arquivo = arquivoHeadersDoNetlify(cabecalhosDeSeguranca(PROD))

    // Arquivo gerado sem aviso é arquivo que alguém edita à mão e perde no build
    // seguinte.
    expect(arquivo).toMatch(/GERADO PELO BUILD/)
    expect(arquivo).toContain('src/security/headers.ts')
  })
})
