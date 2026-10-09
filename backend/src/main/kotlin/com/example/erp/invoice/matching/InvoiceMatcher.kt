package com.example.erp.invoice.matching

import com.example.erp.shared.money.sameValueAs
import java.math.BigDecimal
import java.math.RoundingMode

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

    private fun duplicateInvoice(input: MatchInput): MatchException? {
        if (input.duplicateInvoiceIds.isEmpty()) return null
        return MatchException(
            MatchExceptionCode.DUPLICATE_INVOICE, null, null, input.duplicateInvoiceIds.joinToString(", "),
            "Invoice number ${input.invoice.invoiceNumber} was already used by this supplier " +
                "(invoice id ${input.duplicateInvoiceIds.joinToString(", ")})",
        )
    }

    private fun missingPo(invoice: InvoiceSnapshot): MatchException =
        MatchException(
            MatchExceptionCode.MISSING_PO, null, null, null,
            "Invoice ${invoice.invoiceNumber} does not reference a purchase order",
        )

    private fun currencyMismatch(invoice: InvoiceSnapshot, po: PurchaseOrderSnapshot): MatchException? {
        if (invoice.currency == po.currency) return null
        return MatchException(
            MatchExceptionCode.CURRENCY_MISMATCH, null, po.currency, invoice.currency,
            "Invoice currency ${invoice.currency} differs from ${po.poNumber} currency ${po.currency}",
        )
    }

    private fun unmatchedLine(line: InvoiceLineSnapshot): MatchException =
        MatchException(
            MatchExceptionCode.UNMATCHED_LINE, line.lineNumber, null, null,
            "Line ${line.lineNumber} (${line.description}) is not linked to a purchase order line",
        )

    private fun priceMismatch(line: InvoiceLineSnapshot, poLine: PurchaseOrderLineSnapshot): MatchException? {
        val poPrice = poLine.unitPrice
        val mismatch = if (poPrice.signum() == 0) line.unitPrice.signum() != 0
        else (line.unitPrice - poPrice).abs().multiply(BigDecimal(100))
            .divide(poPrice, 6, RoundingMode.HALF_UP) > PRICE_TOLERANCE_PERCENT
        if (!mismatch) return null
        return MatchException(
            MatchExceptionCode.PRICE_MISMATCH, line.lineNumber, poPrice.plain(), line.unitPrice.plain(),
            "Line ${line.lineNumber}: unit price ${line.unitPrice.plain()} differs from PO price ${poPrice.plain()} " +
                "by more than $PRICE_TOLERANCE_PERCENT %",
        )
    }

    private fun quantityMismatch(line: InvoiceLineSnapshot, poLine: PurchaseOrderLineSnapshot): MatchException? {
        val invoiceable = poLine.receivedQuantity - poLine.alreadyInvoicedQuantity
        if (line.quantity <= invoiceable) return null
        return MatchException(
            MatchExceptionCode.QUANTITY_MISMATCH, line.lineNumber, invoiceable.plain(), line.quantity.plain(),
            "Line ${line.lineNumber}: invoiced ${line.quantity.plain()} but only ${invoiceable.plain()} can be billed " +
                "(received ${poLine.receivedQuantity.plain()}, already invoiced ${poLine.alreadyInvoicedQuantity.plain()})",
        )
    }

    private fun taxMismatch(line: InvoiceLineSnapshot, poLine: PurchaseOrderLineSnapshot): MatchException? {
        if (line.taxRate sameValueAs poLine.taxRate) return null
        return MatchException(
            MatchExceptionCode.TAX_MISMATCH, line.lineNumber, poLine.taxRate.plain(), line.taxRate.plain(),
            "Line ${line.lineNumber}: tax rate ${line.taxRate.plain()} % differs from PO tax rate ${poLine.taxRate.plain()} %",
        )
    }

    // ---- helpers ---------------------------------------------------------------------------------

    /** 42.0000 -> "42", 0.50 -> "0.5": independent of database scale. */
    internal fun BigDecimal.plain(): String = stripTrailingZeros().toPlainString()

    /** Header (lineNumber null -> 0) before lines; within the same line, enum declaration order. */
    private val ORDER = compareBy<MatchException>({ it.lineNumber ?: 0 }, { it.code.ordinal })

    private operator fun MutableList<MatchException>.plusAssign(exception: MatchException?) {
        if (exception != null) add(exception)
    }
}
