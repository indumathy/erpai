package com.example.erp.inventory

import com.example.erp.product.Product
import com.example.erp.product.UnitOfMeasure
import com.example.erp.shared.error.BusinessRuleViolationException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class StockLevelTest {

    private val level = StockLevel(Warehouse("main", "Main warehouse"), Product("BOLT", "Bolt", null, UnitOfMeasure.PIECE))

    @Test
    fun applyAddsAndRemoves() {
        level.apply(BigDecimal("10"))
        level.apply(BigDecimal("-2.5"))
        assertThat(level.quantityOnHand).isEqualTo(BigDecimal("7.500"))
    }

    @Test
    fun stockCannotGoNegative() {
        level.apply(BigDecimal("1"))
        assertThatThrownBy { level.apply(BigDecimal("-1.001")) }
            .isInstanceOf(BusinessRuleViolationException::class.java)
            .hasMessageContaining("Insufficient stock of BOLT in MAIN")
        assertThat(level.quantityOnHand).isEqualByComparingTo("1")
    }
}
