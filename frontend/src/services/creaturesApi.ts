import { apiClient } from './apiClient'
import { arrayOrEmpty } from './apiShapes'
import type { CreatureResponse } from '../types/api'

export const creaturesApi = {
  list: () =>
    apiClient.get<CreatureResponse[]>('/api/creatures').then((r) => arrayOrEmpty(r.data)),
}
