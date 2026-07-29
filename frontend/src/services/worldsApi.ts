import { apiClient } from './apiClient'
import { arrayOrEmpty } from './apiShapes'

export const worldsApi = {
  list: () => apiClient.get<string[]>('/api/worlds').then((r) => arrayOrEmpty(r.data)),
}
