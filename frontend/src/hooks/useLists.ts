import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { listsApi, type CreateListRequest, type SearchListsParams } from '../services/listsApi'

export function useSearchLists(params: SearchListsParams) {
  return useQuery({
    queryKey: ['lists', 'search', params],
    queryFn: () => listsApi.search(params),
  })
}

export function useListDetail(id: number) {
  return useQuery({ queryKey: ['lists', id], queryFn: () => listsApi.get(id) })
}

export function useMyLists() {
  return useQuery({ queryKey: ['lists', 'mine'], queryFn: listsApi.mine })
}

export function useCreateList() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: CreateListRequest) => listsApi.create(body),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['lists'] }),
  })
}

export function useJoinList(listId: number) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (v: { shareCode: string; characterId: number }) =>
      listsApi.join(v.shareCode, v.characterId),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['lists', listId] }),
  })
}

export function useLeaveList() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => listsApi.leave(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['lists'] }),
  })
}

export function useRenewTeam() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => listsApi.renew(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['lists'] }),
  })
}

export function useKickMember(listId: number) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (membershipId: number) => listsApi.kickMember(listId, membershipId),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['lists', listId] }),
  })
}

export function useDeleteTeam() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => listsApi.deleteTeam(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['lists'] }),
  })
}

export function usePendingRequests(listId: number, enabled: boolean) {
  return useQuery({
    queryKey: ['lists', listId, 'requests'],
    queryFn: () => listsApi.pendingRequests(listId),
    enabled,
  })
}

export function useRequestDecision(listId: number) {
  const qc = useQueryClient()
  const invalidate = () => {
    void qc.invalidateQueries({ queryKey: ['lists', listId] })
    void qc.invalidateQueries({ queryKey: ['lists', listId, 'requests'] })
  }
  const approve = useMutation({
    mutationFn: (membershipId: number) => listsApi.approveRequest(listId, membershipId),
    onSuccess: invalidate,
  })
  const reject = useMutation({
    mutationFn: (membershipId: number) => listsApi.rejectRequest(listId, membershipId),
    onSuccess: invalidate,
  })
  return { approve, reject }
}

export function useSoulcoreBoard(listId: number) {
  return useQuery({
    queryKey: ['lists', listId, 'board'],
    queryFn: () => listsApi.board(listId),
  })
}

export function useSoulcoreActions(listId: number) {
  const qc = useQueryClient()
  const invalidate = () => {
    void qc.invalidateQueries({ queryKey: ['lists', listId, 'board'] })
    void qc.invalidateQueries({ queryKey: ['lists', listId, 'suggestions'] })
  }
  const obtain = useMutation({
    mutationFn: (v: { creatureId: number; characterId: number }) =>
      listsApi.obtainSoulcore(listId, v.creatureId, v.characterId),
    onSuccess: invalidate,
  })
  const unlock = useMutation({
    mutationFn: (v: { creatureId: number; characterId: number }) =>
      listsApi.unlockSoulcore(listId, v.creatureId, v.characterId),
    onSuccess: invalidate,
  })
  return { obtain, unlock }
}

export function useSuggestions(listId: number, enabled: boolean) {
  return useQuery({
    queryKey: ['lists', listId, 'suggestions'],
    queryFn: () => listsApi.suggestions(listId),
    enabled,
  })
}

export function useDismissSuggestion(listId: number) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (suggestionId: number) => listsApi.dismissSuggestion(suggestionId),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['lists', listId, 'suggestions'] }),
  })
}
