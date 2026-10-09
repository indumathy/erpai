import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { http } from '../../api/http'
import { inventoryKeys } from '../inventory/api'
import { purchaseOrderKeys } from '../purchase-orders/api'
import type { GoodsReceipt, GoodsReceiptCreate, GoodsReceiptSummary } from './types'

export const goodsReceiptKeys = {
  all: ['goods-receipts'] as const,
  list: (purchaseOrderId: number | undefined) => ['goods-receipts', 'list', { purchaseOrderId }] as const,
  detail: (id: number) => ['goods-receipts', 'detail', id] as const,
}

export function useGoodsReceipts(purchaseOrderId: number | undefined) {
  return useQuery({
    queryKey: goodsReceiptKeys.list(purchaseOrderId),
    queryFn: () =>
      http.get<GoodsReceiptSummary[]>(
        purchaseOrderId === undefined ? '/goods-receipts' : `/goods-receipts?purchaseOrderId=${purchaseOrderId}`,
      ),
  })
}

export function useGoodsReceipt(id: number) {
  return useQuery({
    queryKey: goodsReceiptKeys.detail(id),
    queryFn: () => http.get<GoodsReceipt>(`/goods-receipts/${id}`),
  })
}

/**
 * Posting a goods receipt changes three modules on the server (PO status/quantities, stock,
 * receipts), so all three caches are invalidated.
 */
export function usePostGoodsReceipt() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: GoodsReceiptCreate) => http.post<GoodsReceipt>('/goods-receipts', body),
    onSuccess: (gr) => {
      queryClient.setQueryData(goodsReceiptKeys.detail(gr.id), gr)
      return Promise.all([
        queryClient.invalidateQueries({ queryKey: ['goods-receipts', 'list'] }),
        queryClient.invalidateQueries({ queryKey: purchaseOrderKeys.all }),
        queryClient.invalidateQueries({ queryKey: inventoryKeys.all }),
      ])
    },
  })
}
