package com.example.erp.shared.money

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class DocumentTotalsTest {

    private fun line(net: String, rate: String) = TaxableLine(BigDecimal(net), BigDecimal(rate))

    @Test
    fun lineNet_roundsToCents() {
        // 3 x 0.3333 = 0.9999 -> 1.00
        assertThat(DocumentTotals.lineNet(BigDecimal("3"), BigDecimal("0.3333"))).isEqualTo(BigDecimal("1.00"))
        // the README example: 100 units x 42 EUR
        assertThat(DocumentTotals.lineNet(BigDecimal("100.000"), BigDecimal("42.0000"))).isEqualTo(BigDecimal("4200.00"))
    }

    @Test
    fun mixedTaxRates_areGroupedAndSummed() {
        val totals = DocumentTotals.of(
            listOf(line("4200.00", "19"), line("100.00", "7"), line("50.00", "19.00")),
        )

        assertThat(totals.subtotal).isEqualTo(BigDecimal("4350.00"))
        assertThat(totals.taxBreakdown).containsExactly(
            TaxBreakdown(BigDecimal("19.00"), BigDecimal("4250.00"), BigDecimal("807.50")),
            TaxBreakdown(BigDecimal("7.00"), BigDecimal("100.00"), BigDecimal("7.00")),
        )
        assertThat(totals.taxAmount).isEqualTo(BigDecimal("814.50"))
        assertThat(totals.total).isEqualTo(BigDecimal("5164.50"))
    }

    @Test
    fun taxIsRoundedOncePerGroup_notPerLine() {
        // Three lines of 0.07 at 19 %:
        //   per line : 19 % of 0.07 = 0.0133 -> 0.01, x 3 = 0.03
        //   per group: 19 % of 0.21 = 0.0399 -> 0.04   <- our strategy
        val totals = DocumentTotals.of(List(3) { line("0.07", "19") })

        assertThat(totals.taxAmount).isEqualTo(BigDecimal("0.04"))
    }

    @Test
    fun noLines_giveZeroTotals() {
        val totals = DocumentTotals.of(emptyList())
        assertThat(totals.total).isEqualByComparingTo(BigDecimal.ZERO)
    }
}
