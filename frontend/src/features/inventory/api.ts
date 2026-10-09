import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { http } from '../../api/http'
import type { StockAdjustment, StockLevel, StockMovement, Warehouse } from './types'

export const inventoryKeys = {
  all: ['inventory'] as const,
  stock: (warehouseId: number | undefined) => ['inventory', 'stock', { warehouseId }] as const,
  movements: (productId: number | undefined) => ['inventory', 'movements', { productId }] as const,
  warehouses: ['warehouses'] as const,
}

export function useWarehouses() {
  return useQuery({ queryKey: inventoryKeys.warehouses, queryFn: () => http.get<Warehouse[]>('/warehouses') })
}

export function useCreateWarehouse() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: { code: string; name: string }) => http.post<Warehouse>('/warehouses', body),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: inventoryKeys.warehouses }),
  })
}

export function useSetWarehouseActive() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, active }: { id: number; active: boolean }) =>
      http.post<Warehouse>(`/warehouses/${id}/${active ? 'activate' : 'deactivate'}`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: inventoryKeys.warehouses }),
  })
}

export function useStock(warehouseId: number | undefined) {
  return useQuery({
    queryKey: inventoryKeys.stock(warehouseId),
    queryFn: () =>
      http.get<StockLevel[]>(warehouseId === undefined ? '/inventory/stock' : `/inventory/stock?warehouseId=${warehouseId}`),
  })
}

export function useStockMovements(productId: number | undefined) {
  return useQuery({
    queryKey: inventoryKeys.movements(productId),
    queryFn: () =>
      http.get<StockMovement[]>(
        productId === undefined ? '/inventory/movements' : `/inventory/movements?productId=${productId}`,
      ),
  })
}

export function useAdjustStock() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: StockAdjustment) => http.post<StockMovement>('/inventory/adjustments', body),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: inventoryKeys.all }),
  })
}
