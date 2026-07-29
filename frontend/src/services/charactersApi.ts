import { apiClient } from './apiClient'
import { arrayOrEmpty } from './apiShapes'
import type { CharacterSoulcoreResponse, CharacterSummaryResponse } from '../types/api'

export const charactersApi = {
  mine: () =>
    apiClient
      .get<CharacterSummaryResponse[]>('/api/characters/mine')
      .then((r) => arrayOrEmpty(r.data)),

  soulcores: (characterId: number) =>
    apiClient
      .get<CharacterSoulcoreResponse[]>(`/api/characters/${characterId}/soulcores`)
      .then((r) => arrayOrEmpty(r.data)),
}
