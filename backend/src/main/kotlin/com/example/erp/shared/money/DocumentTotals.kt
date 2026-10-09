package com.example.erp.shared.money

import java.math.BigDecimal

/** One line of a commercial document, reduced to what tax calculation needs. */
data class TaxableLine(val netAmount: BigDecimal, val taxRate: BigDecimal)

/** Net amount and tax for all lines sharing one tax rate (e.g. everything at 19 %). */
data class TaxBreakdown(val taxRate: BigDecimal, val netAmount: BigDecimal, val taxAmount: BigDecimal)

/**
 * Totals of a purchase order or invoice. Shared by both, so the three-way match later
 * compares documents that were calculated with exactly the same arithmetic.
 *
 * Rounding strategy (common EU invoicing practice):
 *  1. each line net = quantity x unit price, rounded to cents   -> [lineNet]
 *  2. lines are grouped by tax rate; tax is calculated ONCE per group on the summed net
 *  3. total = subtotal + sum of group taxes
 * Rounding tax per line instead can differ by a few cents on documents with many lines.
 */
data class DocumentTotals(
    val subtotal: BigDecimal,
    val taxAmount: BigDecimal,
    val total: BigDecimal,
    val taxBreakdown: List<TaxBreakdown>,
) {
    companion object {
        fun lineNet(quantity: BigDecimal, unitPrice: BigDecimal): BigDecimal =
            MoneyRounding.amount(quantity.multiply(unitPrice))

        fun of(lines: List<TaxableLine>): DocumentTotals {
            val breakdown = lines
                .groupBy { MoneyRounding.percent(it.taxRate) }
                .map { (rate, group) ->
                    val net = group.sumOf { it.netAmount }
                    TaxBreakdown(rate, MoneyRounding.amount(net), MoneyRounding.percentOf(net, rate))
                }
                .sortedByDescending { it.taxRate }

            val subtotal = MoneyRounding.amount(breakdown.sumOf { it.netAmount })
            val tax = MoneyRounding.amount(breakdown.sumOf { it.taxAmount })
            return DocumentTotals(subtotal, tax, subtotal + tax, breakdown)
        }
    }
}
