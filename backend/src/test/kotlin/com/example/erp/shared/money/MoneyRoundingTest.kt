package com.example.erp.shared.money

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class MoneyRoundingTest {

    @Test
    fun amount_roundsHalfUpToCents() {
        assertThat(MoneyRounding.amount(BigDecimal("10.005"))).isEqualTo(BigDecimal("10.01"))
        assertThat(MoneyRounding.amount(BigDecimal("10.004"))).isEqualTo(BigDecimal("10.00"))
    }

    @Test
    fun percentOf_computesTaxAmount() {
        // 19 % of 4,200.00 EUR
        assertThat(MoneyRounding.percentOf(BigDecimal("4200.00"), BigDecimal("19.00")))
            .isEqualTo(BigDecimal("798.00"))
        // 7 % of 0.15 = 0.0105 -> 0.01
        assertThat(MoneyRounding.percentOf(BigDecimal("0.15"), BigDecimal("7")))
            .isEqualTo(BigDecimal("0.01"))
    }

    @Test
    fun sameValueAs_ignoresScale() {
        assertThat(BigDecimal("42.0") == BigDecimal("42.00")).isFalse() // equals() compares scale!
        assertThat(BigDecimal("42.0") sameValueAs BigDecimal("42.00")).isTrue()
    }
}
