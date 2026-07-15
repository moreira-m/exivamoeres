import { useQuery } from '@tanstack/react-query'
import { worldsApi } from '../services/worldsApi'
import { creaturesApi } from '../services/creaturesApi'

// Dados de referência (worlds, criaturas): mudam raramente, cache longo.
const HOUR = 1000 * 60 * 60

export function useWorlds() {
  return useQuery({ queryKey: ['worlds'], queryFn: worldsApi.list, staleTime: HOUR })
}

export function useCreatures() {
  return useQuery({ queryKey: ['creatures'], queryFn: creaturesApi.list, staleTime: HOUR })
}
