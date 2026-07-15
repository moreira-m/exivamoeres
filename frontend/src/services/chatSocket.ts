import { Client, type IMessage } from '@stomp/stompjs'
import type { ChatMessageResponse } from '../types/api'

// Deriva a URL do WebSocket a partir da URL HTTP da API (http->ws, https->wss).
function resolveWsUrl(): string {
  const apiUrl = import.meta.env.VITE_API_URL as string
  return apiUrl.replace(/^http/, 'ws') + '/ws'
}

/**
 * Assina o chat de um time em tempo real. O JWT vai no header do CONNECT
 * (validado pelo StompAuthChannelInterceptor no backend). Retorna uma função
 * de cleanup para desconectar.
 */
export function subscribeToChat(
  listId: number,
  accessToken: string,
  onMessage: (message: ChatMessageResponse) => void,
): () => void {
  const client = new Client({
    brokerURL: resolveWsUrl(),
    connectHeaders: { Authorization: `Bearer ${accessToken}` },
    reconnectDelay: 5000,
    onConnect: () => {
      client.subscribe(`/topic/lists/${listId}/chat`, (frame: IMessage) => {
        onMessage(JSON.parse(frame.body) as ChatMessageResponse)
      })
    },
  })
  client.activate()
  return () => {
    void client.deactivate()
  }
}
