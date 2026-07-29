import type { Page } from '../types/api'

/**
 * A fronteira entre o que o servidor mandou e o que as telas assumem.
 *
 * `types/api.ts` é promessa de **tempo de compilação**: em execução, a resposta é o
 * que o servidor mandou — e o servidor pode ser **mais velho que o site** (Netlify e
 * Railway sobem separados). Foi assim que a tela do time ficou branca: um campo de
 * lista ausente virou `undefined.length` durante o render.
 *
 * Estas duas funções são a versão mínima da lição, para uso em qualquer domínio.
 * Nada de validar schema inteiro: o que derruba tela é **percorrer o que não é
 * lista**, e é só isso que elas garantem.
 *
 * ⚠️ Elas devolvem o **mesmo objeto** quando o dado já veio certo. Não é
 * micro-otimização: o React Query compara referências, e clonar à toa faria a tela
 * remontar sem motivo.
 */

/** Lista ausente ou de tipo errado vira lista vazia — que é o que "nenhum" significa. */
export function arrayOrEmpty<T>(valor: T[] | null | undefined): T[] {
  return Array.isArray(valor) ? valor : []
}

/**
 * Envelope `Page<T>` do Spring com o `content` garantido.
 *
 * Os números também ganham padrão: sem eles, `number + 1 < totalPages` compara com
 * `undefined` e o "carregar mais" some sem explicação — pior, `{{count}} times
 * encontrados` renderiza `NaN`.
 */
export function pageOrEmpty<T>(pagina: Page<T> | null | undefined): Page<T> {
  const content = arrayOrEmpty(pagina?.content)
  if (pagina && content === pagina.content
      && typeof pagina.totalElements === 'number'
      && typeof pagina.totalPages === 'number'
      && typeof pagina.number === 'number') {
    return pagina
  }
  return {
    content,
    totalElements: typeof pagina?.totalElements === 'number' ? pagina.totalElements : content.length,
    totalPages: typeof pagina?.totalPages === 'number' ? pagina.totalPages : 1,
    number: typeof pagina?.number === 'number' ? pagina.number : 0,
  }
}
