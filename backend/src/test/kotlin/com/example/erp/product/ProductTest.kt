package com.example.erp.product

import com.example.erp.shared.error.BusinessRuleViolationException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class ProductTest {

    @Test
    fun newProduct_isActiveAndNormalised() {
        val p = Product(" bolt-m8-40 ", " Hex bolt M8x40 ", "  ", UnitOfMeasure.PIECE)

        assertThat(p.active).isTrue()
        assertThat(p.sku).isEqualTo("BOLT-M8-40")
        assertThat(p.name).isEqualTo("Hex bolt M8x40")
        assertThat(p.description).isNull()
    }

    @Test
    fun blankSku_isRejected() {
        assertThatThrownBy { Product("  ", "Bolt", null, UnitOfMeasure.PIECE) }
            .isInstanceOf(BusinessRuleViolationException::class.java)
    }

    @Test
    fun updateDetails_rejectsBlankName() {
        val p = Product("BOLT", "Bolt", null, UnitOfMeasure.PIECE)
        assertThatThrownBy { p.updateDetails(" ", null, UnitOfMeasure.BOX) }
            .isInstanceOf(BusinessRuleViolationException::class.java)
    }
}
