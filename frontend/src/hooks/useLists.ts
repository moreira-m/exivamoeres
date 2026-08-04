import {
  useInfiniteQuery,
  useMutation,
  useQuery,
  useQueryClient,
} from '@tanstack/react-query'
import {
  listsApi,
  type CreateListRequest,
  type MyListsParams,
  type SearchListsParams,
  type UpdateListRequest,
} from '../services/listsApi'
import type { Vocation } from '../types/api'

/**
 * Busca da home, paginada por "carregar mais". Sem isso a tela mostrava só a
 * primeira página e os times seguintes ficavam invisíveis para sempre.
 */
export function useSearchLists(params: SearchListsParams) {
  return useInfiniteQuery({
    queryKey: ['lists', 'search', params],
    queryFn: ({ pageParam }) => listsApi.search({ ...params, page: pageParam }),
    initialPageParam: 0,
    getNextPageParam: (lastPage) =>
      lastPage.number + 1 < lastPage.totalPages ? lastPage.number + 1 : undefined,
  })
}

/**
 * `enabled = false` para id que não é time (`/teams/abc` → `NaN`): sem isso o app
 * pede `/api/lists/NaN`, o backend responde 500 e a tela fica ~20s em "carregando"
 * (as tentativas do React Query) para terminar num erro de servidor — quando a
 * verdade é simplesmente "este endereço não é um time".
 */
export function useListDetail(id: number, enabled = true) {
  return useQuery({
    queryKey: ['lists', id],
    queryFn: () => listsApi.get(id),
    enabled,
  })
}

/**
 * "Meus times" de uma aba, com "carregar mais" (item P12).
 *
 * ⚠️ A tela pede as **duas** abas de uma vez, e é de propósito: os rótulos mostram a
 * contagem de cada uma, e o aviso do plano free compara os ativos com o limite de 3. Cada
 * uma vem paginada e traz o próprio `totalElements`, então as contagens continuam sendo do
 * total — e não "do que veio nesta página", que é como um contador vira mentira.
 */
export function useMyLists(scope: MyListsParams['scope']) {
  return useInfiniteQuery({
    queryKey: ['lists', 'mine', scope],
    queryFn: ({ pageParam }) => listsApi.mine({ scope, page: pageParam }),
    initialPageParam: 0,
    getNextPageParam: (lastPage) =>
      lastPage.number + 1 < lastPage.totalPages ? lastPage.number + 1 : undefined,
  })
}

/** "Meus pedidos": o lado de quem pediu para entrar e ficava sem informação. */
export function useMyJoinRequests() {
  return useQuery({ queryKey: ['lists', 'mine', 'requests'], queryFn: listsApi.myRequests })
}

export function useCancelMyJoinRequest() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (membershipId: number) => listsApi.cancelMyRequest(membershipId),
    // Invalida ['lists'] inteiro: o pedido sai da lista do solicitante e da do dono.
    onSuccess: () => qc.invalidateQueries({ queryKey: ['lists'] }),
  })
}

export function useCreateList() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: CreateListRequest) => listsApi.create(body),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['lists'] }),
  })
}

/** Edição do time pelo dono (PATCH). Invalida o detalhe e as listagens. */
export function useUpdateList(listId: number) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: UpdateListRequest) => listsApi.update(listId, body),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['lists'] }),
  })
}

/** Composição por vocação (só o dono). Invalida detalhe e busca juntos. */
export function useReplaceSlots(listId: number) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (slots: (Vocation | null)[]) => listsApi.replaceSlots(listId, slots),
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
