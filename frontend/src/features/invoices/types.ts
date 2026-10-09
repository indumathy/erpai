import type { PurchaseOrderRef } from '../goods-receipts/types'
import type { SupplierRef } from '../purchase-orders/types'

/** Mirrors backend supplier invoice DTOs. */

export interface InvoiceItem {
  id: number
  lineNumber: number
  purchaseOrderItemId: number | null
  purchaseOrderLineNumber: number | null
  description: string
  quantity: number
  unitPrice: number
  taxRate: number
  netAmount: number
}

export interface Invoice {
  id: number
  invoiceNumber: string
  supplier: SupplierRef
  purchaseOrder: PurchaseOrderRef | null
  invoiceDate: string
  currency: string
  items: InvoiceItem[]
  totals: { subtotal: number; taxAmount: number; total: number }
  createdAt: string
}

export interface InvoiceSummary {
  id: number
  invoiceNumber: string
  supplier: SupplierRef
  purchaseOrder: PurchaseOrderRef | null
  invoiceDate: string
  currency: string
  total: number
  exceptionCount: number
}

export type MatchExceptionCode =
  | 'DUPLICATE_INVOICE'
  | 'MISSING_PO'
  | 'CURRENCY_MISMATCH'
  | 'UNMATCHED_LINE'
  | 'PRICE_MISMATCH'
  | 'QUANTITY_MISMATCH'
  | 'TAX_MISMATCH'

export interface MatchException {
  code: MatchExceptionCode
  lineNumber: number | null
  expected: string | null
  actual: string | null
  message: string
}

export interface MatchResult {
  matched: boolean
  exceptions: MatchException[]
}
