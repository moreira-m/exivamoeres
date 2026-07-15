import { apiClient } from './apiClient'
import type { CreatureResponse } from '../types/api'

export const creaturesApi = {
  list: () => apiClient.get<CreatureResponse[]>('/api/creatures').then((r) => r.data),
}
