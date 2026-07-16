import { apiClient } from './apiClient'
import type { NotificationResponse, Page } from '../types/api'

export const notificationsApi = {
  list: (page = 0, size = 20) =>
    apiClient
      .get<Page<NotificationResponse>>('/api/notifications', { params: { page, size } })
      .then((r) => r.data),

  unreadCount: () =>
    apiClient.get<{ count: number }>('/api/notifications/unread-count').then((r) => r.data.count),

  markRead: (id: number) =>
    apiClient.post<void>(`/api/notifications/${id}/read`).then(() => undefined),

  markAllRead: () => apiClient.post<void>('/api/notifications/read-all').then(() => undefined),
}
