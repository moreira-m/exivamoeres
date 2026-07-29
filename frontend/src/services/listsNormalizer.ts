import { arrayOrEmpty, pageOrEmpty } from './apiShapes'
import type { ListDetailResponse, ListSummaryResponse, Page } from '../types/api'

/**
 * Fronteira entre o que o servidor mandou e o que as telas assumem.
 *
 * Site e API sobem **separados** (Netlify × Railway): existe uma janela em que o
 * site já é o novo e a API ainda é a de antes da composição por vocação. Nessa
 * janela a resposta vem sem `slots`, e um `team.slots.length` numa tela derruba a
 * página inteira do time (tela branca, `undefined is not an object`) — por um
 * campo que só serve para um cartão secundário.
 *
 * Este módulo é o **único** lugar que sabe disso: quem consome a API recebe
 * sempre o formato que os tipos prometem, e nenhuma tela precisa desconfiar do
 * que recebeu. Campo ausente vira composição vazia, que é exatamente o que um
 * time sem composição significa.
 */
export function normalizeSummary(summary: ListSummaryResponse): ListSummaryResponse {
  // `arrayOrEmpty` devolve o mesmo array quando já veio certo, então a comparação
  // por referência abaixo é o que preserva a identidade do objeto inteiro (o React
  // Query compara referências: clonar à toa remontaria a tela sem motivo).
  const slots = arrayOrEmpty(summary.slots)
  return slots === summary.slots ? summary : { ...summary, slots }
}

export function normalizeDetail(detail: ListDetailResponse): ListDetailResponse {
  const summary = normalizeSummary(detail.summary)
  // `members` nunca faltou em API nenhuma até hoje, e é justamente por isso que
  // entra aqui: a tela do time faz `members.find(...)` sem guarda, e a promessa
  // deste módulo é o **contrato inteiro** — garantia parcial é a que engana.
  const members = arrayOrEmpty(detail.members)
  if (summary === detail.summary && members === detail.members) return detail
  return { ...detail, summary, members }
}

export function normalizeSummaries(summaries: ListSummaryResponse[]): ListSummaryResponse[] {
  return summaries.map(normalizeSummary)
}

export function normalizeSummaryPage(page: Page<ListSummaryResponse>): Page<ListSummaryResponse> {
  const envelope = pageOrEmpty(page)
  return { ...envelope, content: normalizeSummaries(envelope.content) }
}
