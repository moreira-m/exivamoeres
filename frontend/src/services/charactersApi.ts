import { apiClient } from './apiClient'
import type { CharacterSoulcoreResponse, CharacterSummaryResponse } from '../types/api'

export const charactersApi = {
  mine: () =>
    apiClient.get<CharacterSummaryResponse[]>('/api/characters/mine').then((r) => r.data),

  soulcores: (characterId: number) =>
    apiClient
      .get<CharacterSoulcoreResponse[]>(`/api/characters/${characterId}/soulcores`)
      .then((r) => r.data),
}
