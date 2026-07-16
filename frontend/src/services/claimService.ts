import { apiClient } from './apiClient'
import type { ClaimResponse } from '../types/api'

export const claimService = {
  create: (characterName: string) =>
    apiClient.post<ClaimResponse>('/api/claims', { characterName }).then((r) => r.data),

  list: () => apiClient.get<ClaimResponse[]>('/api/claims').then((r) => r.data),

  get: (id: number) => apiClient.get<ClaimResponse>(`/api/claims/${id}`).then((r) => r.data),
}
