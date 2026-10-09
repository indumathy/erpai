import type { UnitOfMeasure } from '../products/types'
import type { WarehouseRef } from '../inventory/types'
import type { SupplierRef } from '../purchase-orders/types'

/** Mirrors backend goods receipt DTOs. */

export interface PurchaseOrderRef {
  id: number
  poNumber: string
}

export interface GoodsReceiptItem {
  id: number
  purchaseOrderItemId: number
  lineNumber: number
  sku: string
  description: string
  unitOfMeasure: UnitOfMeasure
  quantityReceived: number
}

export interface GoodsReceipt {
  id: number
  grNumber: string
  purchaseOrder: PurchaseOrderRef
  supplier: SupplierRef
  warehouse: WarehouseRef
  receiptDate: string
  deliveryNoteNumber: string | null
  items: GoodsReceiptItem[]
  createdAt: string
}

export interface GoodsReceiptSummary {
  id: number
  grNumber: string
  purchaseOrder: PurchaseOrderRef
  supplier: SupplierRef
  warehouse: WarehouseRef
  receiptDate: string
  deliveryNoteNumber: string | null
  itemCount: number
}

export interface GoodsReceiptCreate {
  purchaseOrderId: number
  warehouseId: number
  receiptDate: string
  deliveryNoteNumber: string | null
  items: { purchaseOrderItemId: number; quantity: number }[]
}
