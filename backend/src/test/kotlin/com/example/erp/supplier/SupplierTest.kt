package com.example.erp.supplier

import com.example.erp.shared.error.BusinessRuleViolationException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class SupplierTest {

    private fun supplier(
        number: String = "S-1000",
        name: String = "Acme Industrial GmbH",
        vatId: String? = "DE123456789",
        currency: String = "EUR",
        paymentTermsDays: Int = 30,
    ) = Supplier(number, name, vatId, currency, paymentTermsDays)

    @Test
    fun newSupplier_isActiveAndNormalised() {
        val s = supplier(number = " S-1000 ", name = "  Acme  ", vatId = "de 123 456 789")

        assertThat(s.active).isTrue()
        assertThat(s.supplierNumber).isEqualTo("S-1000")
        assertThat(s.name).isEqualTo("Acme")
        assertThat(s.vatId).isEqualTo("DE123456789")
    }

    @Test
    fun blankVatId_isStoredAsNull() {
        assertThat(supplier(vatId = "   ").vatId).isNull()
    }

    @Test
    fun unknownCurrency_isRejected() {
        assertThatThrownBy { supplier(currency = "ABC") }
            .isInstanceOf(BusinessRuleViolationException::class.java)
            .hasMessageContaining("ABC")
    }

    @Test
    fun negativePaymentTerms_isRejected() {
        assertThatThrownBy { supplier(paymentTermsDays = -1) }
            .isInstanceOf(BusinessRuleViolationException::class.java)
    }

    @Test
    fun blankName_isRejected() {
        assertThatThrownBy { supplier(name = "  ") }
            .isInstanceOf(BusinessRuleViolationException::class.java)
    }

    @Test
    fun updateDetails_revalidates() {
        val s = supplier()
        assertThatThrownBy { s.updateDetails("Acme", null, "XYZ", 30) }
            .isInstanceOf(BusinessRuleViolationException::class.java)
    }

    @Test
    fun deactivateAndActivate() {
        val s = supplier()
        s.deactivate()
        assertThat(s.active).isFalse()
        s.activate()
        assertThat(s.active).isTrue()
    }
}
