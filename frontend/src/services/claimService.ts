import { apiClient } from './apiClient'
import { arrayOrEmpty } from './apiShapes'
import type { ClaimResponse } from '../types/api'

export const claimService = {
  create: (characterName: string) =>
    apiClient.post<ClaimResponse>('/api/claims', { characterName }).then((r) => r.data),

  list: () => apiClient.get<ClaimResponse[]>('/api/claims').then((r) => arrayOrEmpty(r.data)),

  get: (id: number) => apiClient.get<ClaimResponse>(`/api/claims/${id}`).then((r) => r.data),
}
