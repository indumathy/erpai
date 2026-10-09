import { createBrowserRouter } from 'react-router'
import { HomePage } from './layout/HomePage'
import { AppLayout } from './layout/AppLayout'
import { NotFoundPage } from './layout/NotFoundPage'
import { GoodsReceiptCreatePage } from './features/goods-receipts/GoodsReceiptCreatePage'
import { GoodsReceiptDetailPage } from './features/goods-receipts/GoodsReceiptDetailPage'
import { GoodsReceiptsPage } from './features/goods-receipts/GoodsReceiptsPage'
import { InvoiceDetailPage } from './features/invoices/InvoiceDetailPage'
import { InvoicesPage } from './features/invoices/InvoicesPage'
import { StockMovementsPage } from './features/inventory/StockMovementsPage'
import { StockPage } from './features/inventory/StockPage'
import { WarehousesPage } from './features/inventory/WarehousesPage'
import { ProductsPage } from './features/products/ProductsPage'
import { PurchaseOrderDetailPage } from './features/purchase-orders/PurchaseOrderDetailPage'
import { PurchaseOrderCreatePage, PurchaseOrderEditPage } from './features/purchase-orders/PurchaseOrderFormPage'
import { PurchaseOrdersPage } from './features/purchase-orders/PurchaseOrdersPage'
import { SuppliersPage } from './features/suppliers/SuppliersPage'

export const router = createBrowserRouter([
  {
    path: '/',
    Component: AppLayout,
    children: [
      { index: true, Component: HomePage },
      { path: 'suppliers', Component: SuppliersPage },
      { path: 'products', Component: ProductsPage },
      { path: 'purchase-orders', Component: PurchaseOrdersPage },
      { path: 'purchase-orders/new', Component: PurchaseOrderCreatePage },
      { path: 'purchase-orders/:id', Component: PurchaseOrderDetailPage },
      { path: 'purchase-orders/:id/edit', Component: PurchaseOrderEditPage },
      { path: 'goods-receipts', Component: GoodsReceiptsPage },
      { path: 'goods-receipts/new', Component: GoodsReceiptCreatePage },
      { path: 'goods-receipts/:id', Component: GoodsReceiptDetailPage },
      { path: 'inventory/stock', Component: StockPage },
      { path: 'inventory/movements', Component: StockMovementsPage },
      { path: 'inventory/warehouses', Component: WarehousesPage },
      { path: 'invoices', Component: InvoicesPage },
      { path: 'invoices/:id', Component: InvoiceDetailPage },
      { path: '*', Component: NotFoundPage },
    ],
  },
])
