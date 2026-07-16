import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { claimService } from '../services/claimService'

const CLAIMS_KEY = ['claims']

export function useClaims() {
  return useQuery({ queryKey: CLAIMS_KEY, queryFn: claimService.list })
}

export function useCreateClaim() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: claimService.create,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: CLAIMS_KEY }),
  })
}
