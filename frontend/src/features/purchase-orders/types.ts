import type { UnitOfMeasure } from '../products/types'

/** Mirrors backend `PurchaseOrderStatus`. */
export const PURCHASE_ORDER_STATUSES = [
  'DRAFT',
  'APPROVED',
  'PARTIALLY_RECEIVED',
  'RECEIVED',
  'CLOSED',
  'CANCELLED',
] as const
export type PurchaseOrderStatus = (typeof PURCHASE_ORDER_STATUSES)[number]

export interface SupplierRef {
  id: number
  supplierNumber: string
  name: string
}

export interface PurchaseOrderItem {
  id: number
  lineNumber: number
  productId: number
  sku: string
  unitOfMeasure: UnitOfMeasure
  description: string
  quantity: number
  unitPrice: number
  taxRate: number
  netAmount: number
  receivedQuantity: number
  openQuantity: number
}

export interface TaxBreakdown {
  taxRate: number
  netAmount: number
  taxAmount: number
}

export interface Totals {
  subtotal: number
  taxAmount: number
  total: number
  taxBreakdown: TaxBreakdown[]
}

export interface PurchaseOrder {
  id: number
  poNumber: string
  status: PurchaseOrderStatus
  supplier: SupplierRef
  orderDate: string
  currency: string
  items: PurchaseOrderItem[]
  totals: Totals
  version: number
  createdAt: string
  updatedAt: string
}

export interface PurchaseOrderSummary {
  id: number
  poNumber: string
  status: PurchaseOrderStatus
  supplier: SupplierRef
  orderDate: string
  currency: string
  itemCount: number
  total: number
}

/** Request line. Note: no amounts. The backend calculates every total. */
export interface PurchaseOrderItemInput {
  productId: number
  description: string | null
  quantity: number
  unitPrice: number
  taxRate: number
}

export interface PurchaseOrderUpdate {
  orderDate: string
  currency: string
  items: PurchaseOrderItemInput[]
}

export interface PurchaseOrderCreate extends PurchaseOrderUpdate {
  supplierId: number
}
