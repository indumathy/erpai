import type { UnitOfMeasure } from '../products/types'

/** Mirrors backend inventory DTOs. */

export interface Warehouse {
  id: number
  code: string
  name: string
  active: boolean
}

export interface WarehouseRef {
  id: number
  code: string
  name: string
}

export interface ProductRef {
  id: number
  sku: string
  name: string
  unitOfMeasure: UnitOfMeasure
}

export interface StockLevel {
  warehouse: WarehouseRef
  product: ProductRef
  quantityOnHand: number
  updatedAt: string
}

export type MovementType = 'GOODS_RECEIPT' | 'ADJUSTMENT'

export interface StockMovement {
  id: number
  warehouse: WarehouseRef
  product: ProductRef
  quantity: number
  movementType: MovementType
  referenceType: string | null
  referenceId: number | null
  referenceNumber: string | null
  note: string | null
  createdAt: string
}

export interface StockAdjustment {
  warehouseId: number
  productId: number
  /** Signed: +5 adds, -2 removes. */
  quantity: number
  reason: string
}
