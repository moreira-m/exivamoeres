import { apiClient } from './apiClient'
import type { ChatMessageResponse, Page } from '../types/api'

export const chatApi = {
  history: (listId: number, page = 0, size = 30) =>
    apiClient
      .get<Page<ChatMessageResponse>>(`/api/lists/${listId}/chat`, { params: { page, size } })
      .then((r) => r.data),

  // Enviar por REST também dispara o broadcast STOMP no backend; útil como
  // fallback quando o socket ainda não conectou.
  send: (listId: number, characterId: number, content: string) =>
    apiClient
      .post<ChatMessageResponse>(`/api/lists/${listId}/chat`, { characterId, content })
      .then((r) => r.data),
}
