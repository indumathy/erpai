package com.example.erp.invoice.matching

import java.math.BigDecimal

/**
 * Everything the matcher needs, as plain data. Built by the service from entities; built by hand in
 * tests; serialisable to JSON for the M2 golden dataset and the M7 AI tools.
 */
data class MatchInput(
    val invoice: InvoiceSnapshot,
    /** Null when the invoice carries no purchase order reference. */
    val purchaseOrder: PurchaseOrderSnapshot?,
    /** Other invoices of the same supplier with the same invoice number (trimmed, case-insensitive). */
    val duplicateInvoiceIds: List<Long>,
)

data class InvoiceSnapshot(
    val id: Long,
    val invoiceNumber: String,
    val currency: String,
    val lines: List<InvoiceLineSnapshot>,
)

data class InvoiceLineSnapshot(
    val lineNumber: Int,
    /** Null when the line is not linked to a PO line (e.g. freight). */
    val purchaseOrderItemId: Long?,
    val description: String,
    val quantity: BigDecimal,
    val unitPrice: BigDecimal,
    val taxRate: BigDecimal,
)

data class PurchaseOrderSnapshot(
    val id: Long,
    val poNumber: String,
    val currency: String,
    val lines: List<PurchaseOrderLineSnapshot>,
)

data class PurchaseOrderLineSnapshot(
    val id: Long,
    val lineNumber: Int,
    val sku: String,
    val quantity: BigDecimal,
    val unitPrice: BigDecimal,
    val taxRate: BigDecimal,
    val receivedQuantity: BigDecimal,
    /** Sum invoiced for this PO line by EARLIER invoices (lower id). */
    val alreadyInvoicedQuantity: BigDecimal,
)

/** Declaration order = order within one line / within the header in the result list. */
enum class MatchExceptionCode {
    // header level
    DUPLICATE_INVOICE,
    MISSING_PO,
    CURRENCY_MISMATCH,

    // line level
    UNMATCHED_LINE,
    PRICE_MISMATCH,
    QUANTITY_MISMATCH,
    TAX_MISMATCH,
}

/**
 * One discrepancy. `expected` / `actual` are plain strings so they serialise identically everywhere
 * (API, eval dataset, LLM prompt).
 */
data class MatchException(
    val code: MatchExceptionCode,
    /** Invoice line number; null for header-level exceptions. */
    val lineNumber: Int?,
    val expected: String?,
    val actual: String?,
    val message: String,
)
