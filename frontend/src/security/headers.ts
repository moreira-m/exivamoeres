/**
 * Cabeçalhos de segurança do site — **fonte única**.
 *
 * Três consumidores leem daqui, e é isso que impede a configuração de divergir:
 *
 * 1. o servidor de desenvolvimento (`vite`) e o de preview (`vite preview`) —
 *    então a CSP vale enquanto se desenvolve, e não só depois do deploy;
 * 2. o arquivo `dist/_headers`, gerado no build e lido pelo **Netlify**;
 * 3. os testes (`headers.test.ts` e `e2e/cabecalhos-de-seguranca.spec.ts`).
 *
 * ⚠️ **Por que não escrever isto direto no `netlify.toml`:** a CSP precisa liberar
 * a origem do backend em `connect-src`, e essa origem é `VITE_API_URL` — uma
 * variável de ambiente que muda entre local e produção. Cabeçalho fixo no
 * `netlify.toml` significaria repetir a URL num segundo lugar e descobrir a
 * divergência quando o chat parasse de conectar em produção.
 */

/** A origem WebSocket correspondente à da API (`https:` → `wss:`). */
function origemWebSocket(apiUrl: URL): string {
  return `${apiUrl.protocol === 'https:' ? 'wss:' : 'ws:'}//${apiUrl.host}`
}

/**
 * Onde os sprites das criaturas moram. Vem da TibiaData no catálogo
 * (`https://static.tibia.com/images/library/…`) e é o **único** host externo de
 * que o site depende: sem esta linha, toda tela com criatura mostra imagem
 * quebrada, e o console enche de violação de CSP.
 */
const HOST_DOS_SPRITES = 'https://static.tibia.com'

export interface OpcoesDeCabecalho {
  /** O mesmo valor de `VITE_API_URL` (sem barra no final). */
  apiUrl: string
  /**
   * `true` para o deploy (https). Só muda o HSTS: forçar https tem que valer em
   * produção e **não** pode aparecer no ambiente local, que roda em http.
   */
  producao?: boolean
}

/**
 * O conjunto completo, pronto para `preview.headers` do Vite ou para o
 * `_headers` do Netlify.
 *
 * Lança se `apiUrl` estiver ausente ou não for URL absoluta: cabeçalho de
 * segurança montado com `undefined` viraria uma CSP que bloqueia a própria API —
 * e o sintoma (site sem dado nenhum) não parece problema de configuração.
 */
export function cabecalhosDeSeguranca({ apiUrl, producao = false }: OpcoesDeCabecalho): Record<string, string> {
  if (!apiUrl) {
    throw new Error(
      'VITE_API_URL não está definida: sem ela a CSP bloquearia a própria API. ' +
        'Defina no .env (local) ou nas variáveis do site (Netlify).',
    )
  }
  let api: URL
  try {
    api = new URL(apiUrl)
  } catch {
    throw new Error(`VITE_API_URL não é uma URL absoluta: "${apiUrl}"`)
  }
  // `new URL('localhost:8080')` **não** falha: o navegador lê "localhost:" como
  // protocolo e "8080" como caminho, e o resultado seria
  // `connect-src 'self' null ws://` — CSP inválida montada sem erro nenhum.
  // Esquecer o `http://` é justamente o erro que se comete ao preencher a
  // variável no painel.
  if (api.protocol !== 'http:' && api.protocol !== 'https:') {
    throw new Error(`VITE_API_URL precisa começar com http:// ou https://: "${apiUrl}"`)
  }

  const csp = [
    // Nada é liberado por omissão: cada diretiva abaixo é uma decisão.
    `default-src 'self'`,
    // Sem 'unsafe-inline' e sem 'unsafe-eval': o build do Vite não gera script
    // inline (o polyfill de modulepreload vai dentro do bundle), então dá para
    // ser estrito aqui — e é aqui que estrito importa, porque a sessão vive em
    // localStorage e script injetado a lê.
    `script-src 'self'`,
    // O Tailwind sai num .css de verdade; o que resta é o React mexendo em
    // `element.style` pelo CSSOM, que a CSP não bloqueia.
    `style-src 'self'`,
    `img-src 'self' ${HOST_DOS_SPRITES} data:`,
    `font-src 'self'`,
    // A API e o WebSocket do chat — o motivo de este arquivo existir.
    `connect-src 'self' ${api.origin} ${origemWebSocket(api)}`,
    // O site não embute nem é embutido: não há iframe, e clickjacking em cima de
    // "encerrar time"/"expulsar membro" é o risco que isto fecha.
    `frame-src 'none'`,
    `frame-ancestors 'none'`,
    `object-src 'none'`,
    // `base-uri` fechado: sem isso, um `<base>` injetado reescreve **todos** os
    // caminhos relativos da página de uma vez.
    `base-uri 'none'`,
    // O login social é navegação (`<a href>`) para o backend, não POST: 'self'
    // basta e impede formulário injetado postar credencial para fora.
    `form-action 'self'`,
  ].join('; ')

  const cabecalhos: Record<string, string> = {
    'Content-Security-Policy': csp,
    // Resposta com content-type errado deixa de ser executada como script.
    'X-Content-Type-Options': 'nosniff',
    // A CSP já tem `frame-ancestors`; isto cobre navegador que a ignora.
    'X-Frame-Options': 'DENY',
    // Não manda o caminho (que carrega id de time) para outro site; para o mesmo
    // site, a URL completa continua disponível.
    'Referrer-Policy': 'strict-origin-when-cross-origin',
    // O site não usa nenhuma destas APIs. Negar explicitamente evita que uma
    // dependência futura peça permissão em nome do usuário.
    'Permissions-Policy': 'geolocation=(), camera=(), microphone=(), payment=()',
    // Sem popup compartilhando contexto com esta janela.
    'Cross-Origin-Opener-Policy': 'same-origin',
  }

  if (producao) {
    // Só no deploy: em http o navegador ignora, mas mandar HSTS de localhost é
    // pedir para alguém depurar por que o :5173 virou https.
    cabecalhos['Strict-Transport-Security'] = 'max-age=31536000; includeSubDomains'
  }
  return cabecalhos
}

/**
 * O formato do `_headers` do Netlify: um caminho, e os cabeçalhos indentados.
 *
 * `/*` cobre tudo — inclusive o `index.html` de qualquer rota da SPA, que é
 * exatamente o documento que precisa da CSP.
 */
export function arquivoHeadersDoNetlify(cabecalhos: Record<string, string>): string {
  const linhas = Object.entries(cabecalhos).map(([nome, valor]) => `  ${nome}: ${valor}`)
  return [
    '# GERADO PELO BUILD — não edite à mão.',
    '# Fonte: frontend/src/security/headers.ts (a CSP depende de VITE_API_URL).',
    '/*',
    ...linhas,
    '',
  ].join('\n')
}
