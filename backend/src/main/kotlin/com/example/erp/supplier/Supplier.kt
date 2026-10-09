package com.example.erp.supplier

import com.example.erp.shared.error.requireRule
import com.example.erp.shared.money.CurrencyCodes
import com.example.erp.shared.persistence.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

/**
 * A vendor we buy from. Master data: never hard-deleted, only deactivated,
 * because historic purchase orders and invoices keep referencing it.
 */
@Entity
@Table(name = "supplier")
class Supplier(
    supplierNumber: String,
    name: String,
    vatId: String?,
    currency: String,
    paymentTermsDays: Int,
) : BaseEntity() {

    /** Business key, e.g. "S-1000". Immutable once created. */
    @Column(name = "supplier_number", nullable = false, updatable = false, length = 32)
    var supplierNumber: String = supplierNumber.trim()
        protected set

    @Column(nullable = false, length = 200)
    var name: String = name.trim()
        protected set

    @Column(name = "vat_id", length = 32)
    var vatId: String? = normalizeVatId(vatId)
        protected set

    /** Default ISO 4217 currency for this supplier's purchase orders and invoices. */
    @Column(nullable = false, length = 3)
    var currency: String = currency
        protected set

    /** Net payment terms in days (e.g. 30 = "net 30"). */
    @Column(name = "payment_terms_days", nullable = false)
    var paymentTermsDays: Int = paymentTermsDays
        protected set

    @Column(nullable = false)
    var active: Boolean = true
        protected set

    init {
        requireRule(this.supplierNumber.isNotEmpty()) { "Supplier number must not be blank" }
        validateDetails()
    }

    fun updateDetails(name: String, vatId: String?, currency: String, paymentTermsDays: Int) {
        this.name = name.trim()
        this.vatId = normalizeVatId(vatId)
        this.currency = currency
        this.paymentTermsDays = paymentTermsDays
        validateDetails()
    }

    fun activate() {
        active = true
    }

    /** Inactive suppliers keep their history but cannot receive new purchase orders. */
    fun deactivate() {
        active = false
    }

    private fun validateDetails() {
        requireRule(name.isNotEmpty()) { "Supplier name must not be blank" }
        requireRule(CurrencyCodes.isValid(currency)) { "Unknown ISO 4217 currency: $currency" }
        requireRule(paymentTermsDays in 0..365) { "Payment terms must be between 0 and 365 days" }
    }

    private companion object {
        /** "de 123 456 789" -> "DE123456789"; blank -> null. */
        fun normalizeVatId(raw: String?): String? =
            raw?.filterNot { it.isWhitespace() }?.uppercase()?.ifEmpty { null }
    }
}
