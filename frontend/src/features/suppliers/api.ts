import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { http } from '../../api/http'
import type { Supplier, SupplierCreate, SupplierUpdate } from './types'

/** Query keys: invalidating ['suppliers'] refreshes every supplier list and detail at once. */
export const supplierKeys = {
  all: ['suppliers'] as const,
  list: (active: boolean | undefined) => ['suppliers', 'list', { active }] as const,
}

export function useSuppliers(active: boolean | undefined) {
  return useQuery({
    queryKey: supplierKeys.list(active),
    queryFn: () => http.get<Supplier[]>(active === undefined ? '/suppliers' : `/suppliers?active=${active}`),
  })
}

export function useCreateSupplier() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: SupplierCreate) => http.post<Supplier>('/suppliers', body),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: supplierKeys.all }),
  })
}

export function useUpdateSupplier() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, body }: { id: number; body: SupplierUpdate }) => http.put<Supplier>(`/suppliers/${id}`, body),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: supplierKeys.all }),
  })
}

export function useSetSupplierActive() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, active }: { id: number; active: boolean }) =>
      http.post<Supplier>(`/suppliers/${id}/${active ? 'activate' : 'deactivate'}`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: supplierKeys.all }),
  })
}
