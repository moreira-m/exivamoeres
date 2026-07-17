import { apiClient } from './apiClient'
import type {
  JoinPolicy,
  ListDetailResponse,
  ListSummaryResponse,
  MembershipResponse,
  Page,
  SuggestionResponse,
} from '../types/api'

export interface CreateListRequest {
  world: string
  targetCreatureId: number
  joinPolicy: JoinPolicy
  characterId: number
  minimumLevel?: number | null
  pricePerSlot?: number | null
}

export interface SearchListsParams {
  world?: string
  creatureId?: number
  hasOpenSlots?: boolean
  page?: number
  size?: number
}

// Client HTTP do domínio de times/listas. Componentes nunca chamam axios direto.
export const listsApi = {
  search: (params: SearchListsParams) =>
    apiClient
      .get<Page<ListSummaryResponse>>('/api/lists/search', { params })
      .then((r) => r.data),

  get: (id: number) =>
    apiClient.get<ListDetailResponse>(`/api/lists/${id}`).then((r) => r.data),

  create: (body: CreateListRequest) =>
    apiClient.post<ListDetailResponse>('/api/lists', body).then((r) => r.data),

  mine: () => apiClient.get<ListSummaryResponse[]>('/api/lists/mine').then((r) => r.data),

  join: (shareCode: string, characterId: number) =>
    apiClient
      .post<ListDetailResponse>(`/api/lists/${shareCode}/join`, { characterId })
      .then((r) => r.data),

  leave: (id: number) => apiClient.post<void>(`/api/lists/${id}/leave`).then(() => undefined),

  renew: (id: number) =>
    apiClient.post<ListDetailResponse>(`/api/lists/${id}/renew`).then((r) => r.data),

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
