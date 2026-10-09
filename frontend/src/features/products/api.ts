import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { http } from '../../api/http'
import type { Product, ProductCreate, ProductUpdate } from './types'

export const productKeys = {
  all: ['products'] as const,
  list: (active: boolean | undefined) => ['products', 'list', { active }] as const,
}

export function useProducts(active: boolean | undefined) {
  return useQuery({
    queryKey: productKeys.list(active),
    queryFn: () => http.get<Product[]>(active === undefined ? '/products' : `/products?active=${active}`),
  })
}

export function useCreateProduct() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: ProductCreate) => http.post<Product>('/products', body),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: productKeys.all }),
  })
}

export function useUpdateProduct() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, body }: { id: number; body: ProductUpdate }) => http.put<Product>(`/products/${id}`, body),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: productKeys.all }),
  })
}

export function useSetProductActive() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, active }: { id: number; active: boolean }) =>
      http.post<Product>(`/products/${id}/${active ? 'activate' : 'deactivate'}`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: productKeys.all }),
  })
}
