import { useQuery } from '@tanstack/react-query'
import { charactersApi } from '../services/charactersApi'
import { useAuthStore } from '../store/authStore'

export function useMyCharacters() {
  const isAuthenticated = useAuthStore((s) => !!s.user)
  return useQuery({
    queryKey: ['characters', 'mine'],
    queryFn: charactersApi.mine,
    enabled: isAuthenticated,
  })
}
