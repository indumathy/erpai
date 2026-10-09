package com.example.erp.invoice.matching

import java.math.BigDecimal

/**
 * Deterministic three-way match: invoice vs purchase order vs goods receipts.
 * Pure function — no Spring, no database, no clock. Empty result = matched.
 */
object InvoiceMatcher {

    /** Relative price deviation that is still accepted, in percent. Exactly 0.5 % passes. */
    val PRICE_TOLERANCE_PERCENT = BigDecimal("0.5")

    fun match(input: MatchInput): List<MatchException> {
        val exceptions = mutableListOf<MatchException>()
        exceptions += duplicateInvoice(input)

        val po = input.purchaseOrder
        if (po == null) {
            exceptions += missingPo(input.invoice)
            return exceptions.sortedWith(ORDER)
        }

        val currencyMismatch = currencyMismatch(input.invoice, po)
        exceptions += currencyMismatch

        for (line in input.invoice.lines) {
            val poLine = po.lines.find { it.id == line.purchaseOrderItemId }
            if (poLine == null) {
                exceptions += unmatchedLine(line)
                continue
            }
            if (currencyMismatch == null) exceptions += priceMismatch(line, poLine)
            exceptions += quantityMismatch(line, poLine)
            exceptions += taxMismatch(line, poLine)
        }
        return exceptions.sortedWith(ORDER)
    }

    // ---- rules: each returns the exception, or null when the rule passes ------------------------

    private fun duplicateInvoice(input: MatchInput): MatchException? =
        TODO("M1 exercise: DUPLICATE_INVOICE")

    private fun missingPo(invoice: InvoiceSnapshot): MatchException =
        TODO("M1 exercise: MISSING_PO")

    private fun currencyMismatch(invoice: InvoiceSnapshot, po: PurchaseOrderSnapshot): MatchException? =
        TODO("M1 exercise: CURRENCY_MISMATCH")

    private fun unmatchedLine(line: InvoiceLineSnapshot): MatchException =
        TODO("M1 exercise: UNMATCHED_LINE")

    private fun priceMismatch(line: InvoiceLineSnapshot, poLine: PurchaseOrderLineSnapshot): MatchException? =
        TODO("M1 exercise: PRICE_MISMATCH")

    private fun quantityMismatch(line: InvoiceLineSnapshot, poLine: PurchaseOrderLineSnapshot): MatchException? =
        TODO("M1 exercise: QUANTITY_MISMATCH")

    private fun taxMismatch(line: InvoiceLineSnapshot, poLine: PurchaseOrderLineSnapshot): MatchException? =
        TODO("M1 exercise: TAX_MISMATCH")

    // ---- helpers ---------------------------------------------------------------------------------

    /** 42.0000 -> "42", 0.50 -> "0.5": independent of database scale. */
    internal fun BigDecimal.plain(): String = stripTrailingZeros().toPlainString()

    /** Header (lineNumber null -> 0) before lines; within the same line, enum declaration order. */
    private val ORDER = compareBy<MatchException>({ it.lineNumber ?: 0 }, { it.code.ordinal })

    private operator fun MutableList<MatchException>.plusAssign(exception: MatchException?) {
        if (exception != null) add(exception)
    }
}
