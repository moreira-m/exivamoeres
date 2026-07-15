import { useEffect, useState } from 'react'
import { chatApi } from '../services/chatApi'
import { subscribeToChat } from '../services/chatSocket'
import { useAuthStore } from '../store/authStore'
import type { ChatMessageResponse } from '../types/api'

/**
 * Chat de um time: carrega o histórico por REST e mantém a lista viva via
 * STOMP. Mensagens novas do socket são anexadas ao fim.
 */
export function useChat(listId: number) {
  const accessToken = useAuthStore((s) => s.accessToken)
  const [messages, setMessages] = useState<ChatMessageResponse[]>([])
  const [connected, setConnected] = useState(false)

  useEffect(() => {
    let active = true
    // Histórico vem em ordem decrescente; exibimos em ordem cronológica.
    chatApi.history(listId).then((page) => {
      if (active) setMessages([...page.content].reverse())
    })
    return () => {
      active = false
    }
  }, [listId])

  useEffect(() => {
    if (!accessToken) return
    setConnected(true)
    const unsubscribe = subscribeToChat(listId, accessToken, (message) => {
      setMessages((prev) =>
        prev.some((m) => m.id === message.id) ? prev : [...prev, message],
      )
    })
    return () => {
      setConnected(false)
      unsubscribe()
    }
  }, [listId, accessToken])

  const send = (characterId: number, content: string) =>
    chatApi.send(listId, characterId, content)

  return { messages, send, connected }
}
