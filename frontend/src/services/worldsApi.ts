import { apiClient } from './apiClient'

export const worldsApi = {
  list: () => apiClient.get<string[]>('/api/worlds').then((r) => r.data),
}
