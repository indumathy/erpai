/** Mirrors backend `SupplierResponse` / `CreateSupplierRequest` / `UpdateSupplierRequest`. */

export interface Supplier {
  id: number
  supplierNumber: string
  name: string
  vatId: string | null
  currency: string
  paymentTermsDays: number
  active: boolean
  createdAt: string
  updatedAt: string
}

export interface SupplierUpdate {
  name: string
  vatId: string | null
  currency: string
  paymentTermsDays: number
}

export interface SupplierCreate extends SupplierUpdate {
  supplierNumber: string
}
