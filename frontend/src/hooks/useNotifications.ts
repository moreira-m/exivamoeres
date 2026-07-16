import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { notificationsApi } from '../services/notificationsApi'
import { useAuthStore } from '../store/authStore'

const KEY = ['notifications']

/**
 * Contador de não-lidas para o badge do sino. Usa polling leve (30s) em vez de
 * WebSocket: para o volume atual, é bem mais simples e suficiente — o chat já
 * tem STOMP, mas notificações não exigem latência de tempo real.
 */
export function useUnreadCount() {
  const isAuthenticated = useAuthStore((s) => !!s.user)
  return useQuery({
    queryKey: [...KEY, 'unread'],
    queryFn: notificationsApi.unreadCount,
    enabled: isAuthenticated,
    refetchInterval: 30_000,
    refetchOnWindowFocus: true,
  })
}

export function useNotifications() {
  return useQuery({ queryKey: [...KEY, 'list'], queryFn: () => notificationsApi.list() })
}

export function useMarkNotificationsRead() {
  const qc = useQueryClient()
  const invalidate = () => qc.invalidateQueries({ queryKey: KEY })
  const markOne = useMutation({ mutationFn: notificationsApi.markRead, onSuccess: invalidate })
  const markAll = useMutation({ mutationFn: notificationsApi.markAllRead, onSuccess: invalidate })
  return { markOne, markAll }
}
