import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { http } from '../../api/http'
import type {
  PurchaseOrder,
  PurchaseOrderCreate,
  PurchaseOrderStatus,
  PurchaseOrderSummary,
  PurchaseOrderUpdate,
} from './types'

export const purchaseOrderKeys = {
  all: ['purchase-orders'] as const,
  list: (status: PurchaseOrderStatus | undefined) => ['purchase-orders', 'list', { status }] as const,
  detail: (id: number) => ['purchase-orders', 'detail', id] as const,
}

export function usePurchaseOrders(status: PurchaseOrderStatus | undefined) {
  return useQuery({
    queryKey: purchaseOrderKeys.list(status),
    queryFn: () =>
      http.get<PurchaseOrderSummary[]>(status ? `/purchase-orders?status=${status}` : '/purchase-orders'),
  })
}

export function usePurchaseOrder(id: number) {
  return useQuery({
    queryKey: purchaseOrderKeys.detail(id),
    queryFn: () => http.get<PurchaseOrder>(`/purchase-orders/${id}`),
  })
}

/**
 * Every mutation returns the updated purchase order: we put it straight into the detail cache
 * (no extra GET) and mark all lists stale.
 */
function useStoreResult() {
  const queryClient = useQueryClient()
  return (po: PurchaseOrder) => {
    queryClient.setQueryData(purchaseOrderKeys.detail(po.id), po)
    return queryClient.invalidateQueries({ queryKey: ['purchase-orders', 'list'] })
  }
}

export function useCreatePurchaseOrder() {
  const store = useStoreResult()
  return useMutation({
    mutationFn: (body: PurchaseOrderCreate) => http.post<PurchaseOrder>('/purchase-orders', body),
    onSuccess: store,
  })
}

export function useUpdatePurchaseOrder() {
  const store = useStoreResult()
  return useMutation({
    mutationFn: ({ id, body }: { id: number; body: PurchaseOrderUpdate }) =>
      http.put<PurchaseOrder>(`/purchase-orders/${id}`, body),
    onSuccess: store,
  })
}

export function usePurchaseOrderAction() {
  const store = useStoreResult()
  return useMutation({
    mutationFn: ({ id, action }: { id: number; action: 'approve' | 'cancel' | 'close' }) =>
      http.post<PurchaseOrder>(`/purchase-orders/${id}/${action}`),
    onSuccess: store,
  })
}
