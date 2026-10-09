package com.example.erp.purchaseorder

enum class PurchaseOrderStatus {
    /** Being prepared by a buyer; lines can still change. */
    DRAFT,

    /** Committed to the supplier; goods can be received against it. */
    APPROVED,

    /** Some, but not all, ordered quantities have been received (set by goods receipts). */
    PARTIALLY_RECEIVED,

    /** All ordered quantities have been received (set by goods receipts). */
    RECEIVED,

    /** Finished; no further receipts or invoices expected. */
    CLOSED,

    CANCELLED,
}
