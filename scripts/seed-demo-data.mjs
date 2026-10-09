// Seeds demo data through the public REST API, so every business rule (PO/GR numbering,
// status transitions, stock movements) runs exactly as it would for a real user.
//
// Usage (backend must be running):  node scripts/seed-demo-data.mjs [baseUrl]
// Refuses to run when suppliers already exist, so it is safe to call twice.

const BASE = (process.argv[2] ?? 'http://localhost:8080').replace(/\/$/, '')

async function api(method, path, body) {
  const res = await fetch(`${BASE}/api${path}`, {
    method,
    headers: body ? { 'Content-Type': 'application/json' } : {},
    body: body ? JSON.stringify(body) : undefined,
  })
  const text = await res.text()
  if (!res.ok) throw new Error(`${method} ${path} -> ${res.status}\n${text}`)
  return text ? JSON.parse(text) : null
}

const daysAgo = (n) => new Date(Date.now() - n * 86_400_000).toISOString().slice(0, 10)

// ---- master data ------------------------------------------------------------------------------

const SUPPLIERS = [
  { supplierNumber: 'SUP-1001', name: 'Müller Bürobedarf GmbH', vatId: 'DE123456789', currency: 'EUR', paymentTermsDays: 30 },
  { supplierNumber: 'SUP-1002', name: 'Schneider Elektronik AG', vatId: 'DE987654321', currency: 'EUR', paymentTermsDays: 14 },
  { supplierNumber: 'SUP-1003', name: 'Alpen Logistik & Verpackung GmbH', vatId: 'ATU12345678', currency: 'EUR', paymentTermsDays: 45 },
  { supplierNumber: 'SUP-1004', name: 'Pacific Components Inc.', vatId: null, currency: 'USD', paymentTermsDays: 60 },
  { supplierNumber: 'SUP-1005', name: 'Weber Reinigungsmittel KG', vatId: 'DE555666777', currency: 'EUR', paymentTermsDays: 30 },
]

const PRODUCTS = [
  { sku: 'OFF-PAPER-A4', name: 'Copy paper A4, 80 g/m²', description: 'Box with 5 reams of 500 sheets', unitOfMeasure: 'BOX' },
  { sku: 'OFF-TONER-HP26X', name: 'Toner cartridge HP 26X', description: 'Black, high yield, approx. 9,000 pages', unitOfMeasure: 'PIECE' },
  { sku: 'OFF-PEN-BLUE', name: 'Ballpoint pen, blue', description: 'Box of 50 pens', unitOfMeasure: 'BOX' },
  { sku: 'IT-MON-27', name: 'Monitor 27" QHD', description: 'IPS, 2560x1440, USB-C, height adjustable', unitOfMeasure: 'PIECE' },
  { sku: 'IT-KB-DE', name: 'Keyboard, German layout', description: 'Wired USB keyboard, QWERTZ', unitOfMeasure: 'PIECE' },
  { sku: 'IT-CABLE-CAT6', name: 'Network cable Cat6', description: 'Sold by the meter from a 305 m reel', unitOfMeasure: 'METER' },
  { sku: 'PKG-CARTON-M', name: 'Shipping carton, medium', description: '400x300x250 mm, double wall', unitOfMeasure: 'PIECE' },
  { sku: 'PKG-PALLET-EUR', name: 'EUR pallet', description: '1200x800 mm, EPAL certified', unitOfMeasure: 'PALLET' },
  { sku: 'CLN-DETERG-5L', name: 'All-purpose cleaner', description: 'Concentrate', unitOfMeasure: 'LITER' },
  { sku: 'SRV-IT-SUPPORT', name: 'IT on-site support', description: 'Billed per started hour', unitOfMeasure: 'HOUR' },
]

// ---- seeding ----------------------------------------------------------------------------------

async function seedMasterDataAndOrders() {
  console.log('Creating warehouses...')
  const warehouses = await api('GET', '/warehouses')
  const main = warehouses.find((w) => w.code === 'MAIN')
  const north = await api('POST', '/warehouses', { code: 'HAM', name: 'Hamburg distribution center' })

  console.log('Creating suppliers...')
  const s = {}
  for (const sup of SUPPLIERS) s[sup.supplierNumber] = await api('POST', '/suppliers', sup)

  console.log('Creating products...')
  const p = {}
  for (const prod of PRODUCTS) p[prod.sku] = await api('POST', '/products', prod)

  // Opening stock, as if counted on go-live day.
  console.log('Booking opening stock...')
  const opening = [
    [main, 'OFF-PAPER-A4', 40], [main, 'OFF-PEN-BLUE', 12], [main, 'IT-KB-DE', 8],
    [main, 'CLN-DETERG-5L', 60], [north, 'PKG-CARTON-M', 500], [north, 'PKG-PALLET-EUR', 24],
  ]
  for (const [wh, sku, qty] of opening) {
    await api('POST', '/inventory/adjustments', {
      warehouseId: wh.id, productId: p[sku].id, quantity: qty, reason: 'Opening balance (inventory count)',
    })
  }

  const line = (sku, quantity, unitPrice, taxRate = 19) => ({ productId: p[sku].id, quantity, unitPrice, taxRate })
  const po = (supplier, orderDate, items) =>
    api('POST', '/purchase-orders', { supplierId: s[supplier].id, orderDate, items })
  const receive = (order, warehouse, receiptDate, deliveryNoteNumber, quantities) =>
    api('POST', '/goods-receipts', {
      purchaseOrderId: order.id,
      warehouseId: warehouse.id,
      receiptDate,
      deliveryNoteNumber,
      items: quantities.map(([lineNumber, quantity]) => ({
        purchaseOrderItemId: order.items.find((i) => i.lineNumber === lineNumber).id,
        quantity,
      })),
    })

  console.log('Creating purchase orders in every status...')

  // RECEIVED: office supplies, delivered in full.
  const po1 = await api('POST', `/purchase-orders/${(await po('SUP-1001', daysAgo(30), [
    line('OFF-PAPER-A4', 50, 21.9), line('OFF-TONER-HP26X', 10, 89.5), line('OFF-PEN-BLUE', 5, 14.25),
  ])).id}/approve`)
  await receive(po1, main, daysAgo(25), 'LS-2026-08812', [[1, 50], [2, 10], [3, 5]])

  // CLOSED: partial delivery, supplier cannot deliver the rest -> short-closed.
  const po2 = await api('POST', `/purchase-orders/${(await po('SUP-1002', daysAgo(21), [
    line('IT-MON-27', 12, 279), line('IT-KB-DE', 20, 24.9),
  ])).id}/approve`)
  await receive(po2, main, daysAgo(14), 'SE-449021', [[1, 10], [2, 20]])
  await api('POST', `/purchase-orders/${po2.id}/close`)

  // PARTIALLY_RECEIVED: packaging, first truck arrived in Hamburg.
  const po3 = await api('POST', `/purchase-orders/${(await po('SUP-1003', daysAgo(10), [
    line('PKG-CARTON-M', 2000, 0.85), line('PKG-PALLET-EUR', 40, 12.5),
  ])).id}/approve`)
  await receive(po3, north, daysAgo(4), 'ALV-77310', [[1, 1200], [2, 40]])

  // APPROVED: import order in USD (non-EU supplier, so 0% tax on the PO), nothing delivered yet.
  const po4 = await po('SUP-1004', daysAgo(6), [line('IT-CABLE-CAT6', 915, 0.62, 0), line('IT-MON-27', 25, 245, 0)])
  await api('POST', `/purchase-orders/${po4.id}/approve`)

  // DRAFT: still being prepared.
  await po('SUP-1005', daysAgo(1), [line('CLN-DETERG-5L', 100, 3.4)])
  await po('SUP-1002', daysAgo(0), [line('SRV-IT-SUPPORT', 16, 95), line('IT-KB-DE', 10, 24.9)])

  // CANCELLED: ordered by mistake.
  const po7 = await po('SUP-1001', daysAgo(8), [line('OFF-TONER-HP26X', 30, 89.5)])
  await api('POST', `/purchase-orders/${po7.id}/cancel`)

  // An inactive supplier, to show the active filter in the UI. Its draft PO above can then
  // not be approved - a handy way to see that business rule in action.
  await api('POST', `/suppliers/${s['SUP-1005'].id}/deactivate`)

  const orders = await api('GET', '/purchase-orders')
  const stock = await api('GET', '/inventory/stock')
  console.log(`Done: ${SUPPLIERS.length} suppliers, ${PRODUCTS.length} products, 2 warehouses, ` +
    `${orders.length} purchase orders, ${stock.length} stock levels.`)
}

// ---- invoices: one or more per matching rule, plus clean ones --------------------------------

async function seedInvoices() {
  console.log('Creating supplier invoices...')
  const suppliers = await api('GET', '/suppliers')
  const supplierId = (number) => suppliers.find((s) => s.supplierNumber === number).id
  const summaries = await api('GET', '/purchase-orders')
  const poOf = async (supplierNumber, status) => {
    const summary = summaries.find((o) => o.supplier.supplierNumber === supplierNumber && o.status === status)
    if (!summary) throw new Error(`Demo PO ${supplierNumber}/${status} not found - reseed the database`)
    return api('GET', `/purchase-orders/${summary.id}`)
  }
  const lineOf = (po, sku) => po.items.find((i) => i.sku === sku)

  const invoice = (supplierNumber, invoiceNumber, invoiceDate, currency, po, items) =>
    api('POST', '/invoices', {
      supplierId: supplierId(supplierNumber), invoiceNumber, invoiceDate, currency,
      purchaseOrderId: po?.id ?? null, items,
    })
  // Bill a PO line; overrides change what the supplier "printed".
  const bill = (po, sku, quantity, overrides = {}) => {
    const l = lineOf(po, sku)
    return {
      purchaseOrderItemId: l.id, description: l.description, quantity,
      unitPrice: l.unitPrice, taxRate: l.taxRate, ...overrides,
    }
  }

  const office = await poOf('SUP-1001', 'RECEIVED')
  const electronics = await poOf('SUP-1002', 'CLOSED')
  const packaging = await poOf('SUP-1003', 'PARTIALLY_RECEIVED')
  const imports = await poOf('SUP-1004', 'APPROVED')

  // Clean: everything delivered, billed at PO prices.
  await invoice('SUP-1001', 'MB-2026-0815', daysAgo(24), 'EUR', office, [
    bill(office, 'OFF-PAPER-A4', 50), bill(office, 'OFF-TONER-HP26X', 10), bill(office, 'OFF-PEN-BLUE', 5),
  ])
  // Clean: first truck of cartons.
  await invoice('SUP-1003', 'ALV-R-30117', daysAgo(3), 'EUR', packaging, [bill(packaging, 'PKG-CARTON-M', 1200)])
  // PRICE_MISMATCH: monitors billed 7 % above the PO price.
  await invoice('SUP-1002', 'SE-RE-77821', daysAgo(12), 'EUR', electronics, [
    bill(electronics, 'IT-MON-27', 10, { unitPrice: 298.53 }),
  ])
  // QUANTITY_MISMATCH: second carton invoice for goods that have not arrived yet.
  await invoice('SUP-1003', 'ALV-R-30188', daysAgo(2), 'EUR', packaging, [bill(packaging, 'PKG-CARTON-M', 800)])
  // TAX_MISMATCH: keyboards invoiced with 7 % instead of 19 % VAT.
  await invoice('SUP-1002', 'SE-RE-77840', daysAgo(11), 'EUR', electronics, [
    bill(electronics, 'IT-KB-DE', 20, { taxRate: 7 }),
  ])
  // UNMATCHED_LINE: pallets plus a freight charge that was never ordered.
  await invoice('SUP-1003', 'ALV-R-30120', daysAgo(3), 'EUR', packaging, [
    bill(packaging, 'PKG-PALLET-EUR', 40),
    { purchaseOrderItemId: null, description: 'Freight / Fracht', quantity: 1, unitPrice: 85, taxRate: 19 },
  ])
  // CURRENCY_MISMATCH + QUANTITY_MISMATCH: USD order invoiced in EUR before anything shipped.
  await invoice('SUP-1004', 'PC-INV-5521', daysAgo(1), 'EUR', imports, [
    bill(imports, 'IT-CABLE-CAT6', 915), bill(imports, 'IT-MON-27', 25),
  ])
  // MISSING_PO + DUPLICATE_INVOICE: cleaning supplies without PO, sent twice.
  const cleaner = { purchaseOrderItemId: null, description: 'All-purpose cleaner', quantity: 100, unitPrice: 3.4, taxRate: 19 }
  await invoice('SUP-1005', 'WR-2026-114', daysAgo(5), 'EUR', null, [cleaner])
  await invoice('SUP-1005', 'WR-2026-114', daysAgo(1), 'EUR', null, [cleaner])

  const invoices = await api('GET', '/invoices')
  const flagged = invoices.filter((i) => i.exceptionCount > 0).length
  console.log(`Done: ${invoices.length} invoices, ${invoices.length - flagged} matched, ${flagged} with exceptions.`)
}

async function main() {
  if ((await api('GET', '/suppliers')).length === 0) await seedMasterDataAndOrders()
  else console.log('Suppliers exist - master data and purchase orders already seeded.')

  if ((await api('GET', '/invoices')).length === 0) await seedInvoices()
  else console.log('Invoices exist - nothing to do.')
}

main().catch((err) => {
  console.error(err.message)
  process.exit(1)
})
