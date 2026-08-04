import { apiClient } from './apiClient'
import {
  normalizeDetail,
  normalizeSummaryPage,
} from './listsNormalizer'
import type {
  JoinPolicy,
  Vocation,
  ListDetailResponse,
  ListSummaryResponse,
  MembershipResponse,
  MyJoinRequestResponse,
  Page,
  SuggestionResponse,
} from '../types/api'

/** Parâmetros de `/api/lists/mine` — uma aba por vez (item P12). */
export interface MyListsParams {
  scope?: 'ACTIVE' | 'HISTORY'
  page?: number
  size?: number
}

export interface CreateListRequest {
  world: string
  targetCreatureId: number
  joinPolicy: JoinPolicy
  characterId: number
  minimumLevel?: number | null
  pricePerSlot?: number | null
  description?: string | null
  huntSchedule?: string | null
  contact?: string | null
  // Composição por vocação, na ordem das vagas. `null` numa posição = vaga livre;
  // lista só com nulos = time sem composição (o backend normaliza igual).
  slots?: (Vocation | null)[] | null
}

/**
 * Campos editáveis de um time. Manda-se o conjunto COMPLETO: campo nulo/vazio
 * limpa o valor no backend (não é "não mexer"). World, criatura e política de
 * entrada não são editáveis de propósito.
 */
export interface UpdateListRequest {
  name?: string | null
  minimumLevel?: number | null
  pricePerSlot?: number | null
  description?: string | null
  huntSchedule?: string | null
  contact?: string | null
  // ⚠️ Composição NÃO entra aqui: é o `PUT /api/lists/{id}/slots`, porque a regra é
  // outra (vaga ocupada não muda) e uma edição de descrição não pode falhar por isso.
}

export interface SearchListsParams {
  world?: string
  creatureId?: number
  hasOpenSlots?: boolean
  /**
   * Filtra por "onde um personagem desta vocação cabe AGORA" — não por quem
   * *exige* esta vocação. Time sem composição entra (aceita qualquer vocação) e
   * vaga livre também; vaga da vocação já ocupada, não.
   */
  vocation?: Vocation
  page?: number
  size?: number
}

// Client HTTP do domínio de times/listas. Componentes nunca chamam axios direto.
export const listsApi = {
  search: (params: SearchListsParams) =>
    apiClient
      .get<Page<ListSummaryResponse>>('/api/lists/search', { params })
      .then((r) => normalizeSummaryPage(r.data)),

  get: (id: number) =>
    apiClient.get<ListDetailResponse>(`/api/lists/${id}`).then((r) => normalizeDetail(r.data)),

  create: (body: CreateListRequest) =>
    apiClient.post<ListDetailResponse>('/api/lists', body).then((r) => normalizeDetail(r.data)),

  /**
   * "Meus times" de uma aba, paginado (item P12).
   *
   * ⚠️ Era um array com **todos** os status e sem teto — numa conta antiga, a maior parte
   * dele é histórico que a tela nem mostra ao abrir. O `totalElements` da página é o que
   * alimenta os contadores das abas e o aviso do limite do plano; sem ele, o contador
   * passaria a dizer "quantos vieram nesta página", e o aviso do plano free mentiria sobre
   * quantos times ativos a pessoa tem.
   */
  mine: (params: MyListsParams = {}) =>
    apiClient
      .get<Page<ListSummaryResponse>>('/api/lists/mine', { params })
      .then((r) => normalizeSummaryPage(r.data)),

  /** Pedidos de entrada do próprio usuário (pendentes e recusados). */
  myRequests: () =>
    apiClient.get<MyJoinRequestResponse[]>('/api/lists/mine/requests').then((r) => r.data),

  /** O solicitante desiste do próprio pedido. */
  cancelMyRequest: (membershipId: number) =>
    apiClient.delete<void>(`/api/lists/mine/requests/${membershipId}`).then(() => undefined),

  /**
   * Substitui a composição por vocação (só o dono). Endpoint separado do PATCH: a
   * regra é outra — vaga ocupada não muda, e lista vazia remove a composição.
   */
  replaceSlots: (id: number, slots: (Vocation | null)[]) =>
    apiClient
      .put<ListDetailResponse>(`/api/lists/${id}/slots`, { slots })
      .then((r) => normalizeDetail(r.data)),

  update: (id: number, body: UpdateListRequest) =>
    apiClient
      .patch<ListDetailResponse>(`/api/lists/${id}`, body)
      .then((r) => normalizeDetail(r.data)),

  join: (shareCode: string, characterId: number) =>
    apiClient
      .post<ListDetailResponse>(`/api/lists/${shareCode}/join`, { characterId })
      .then((r) => normalizeDetail(r.data)),

  leave: (id: number) => apiClient.post<void>(`/api/lists/${id}/leave`).then(() => undefined),

  renew: (id: number) =>
    apiClient
      .post<ListDetailResponse>(`/api/lists/${id}/renew`)
      .then((r) => normalizeDetail(r.data)),

  kickMember: (id: number, membershipId: number) =>
    apiClient.delete<void>(`/api/lists/${id}/members/${membershipId}`).then(() => undefined),

  deleteTeam: (id: number) => apiClient.delete<void>(`/api/lists/${id}`).then(() => undefined),

  pendingRequests: (id: number) =>
    apiClient.get<MembershipResponse[]>(`/api/lists/${id}/requests`).then((r) => r.data),

  approveRequest: (id: number, membershipId: number) =>
    apiClient
      .post<void>(`/api/lists/${id}/requests/${membershipId}/approve`)
      .then(() => undefined),

  rejectRequest: (id: number, membershipId: number) =>
    apiClient
      .post<void>(`/api/lists/${id}/requests/${membershipId}/reject`)
      .then(() => undefined),

  suggestions: (id: number) =>
    apiClient.get<SuggestionResponse[]>(`/api/lists/${id}/suggestions`).then((r) => r.data),

  dismissSuggestion: (suggestionId: number) =>
    apiClient.post<void>(`/api/suggestions/${suggestionId}/dismiss`).then(() => undefined),
}
