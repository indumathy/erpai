/** Mirrors backend `ProductResponse` / `CreateProductRequest` / `UpdateProductRequest`. */

/** Must match backend enum `UnitOfMeasure` (string-literal union instead of a TS enum: same JSON, no runtime code). */
export const UNITS_OF_MEASURE = ['PIECE', 'BOX', 'PALLET', 'KG', 'LITER', 'METER', 'HOUR'] as const
export type UnitOfMeasure = (typeof UNITS_OF_MEASURE)[number]

export interface Product {
  id: number
  sku: string
  name: string
  description: string | null
  unitOfMeasure: UnitOfMeasure
  active: boolean
  createdAt: string
  updatedAt: string
}

export interface ProductUpdate {
  name: string
  description: string | null
  unitOfMeasure: UnitOfMeasure
}

export interface ProductCreate extends ProductUpdate {
  sku: string
}
