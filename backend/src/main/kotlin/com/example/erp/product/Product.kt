package com.example.erp.product

import com.example.erp.shared.error.requireRule
import com.example.erp.shared.persistence.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table

/**
 * Something we buy. Prices are deliberately NOT stored here: in procurement the price
 * is agreed per purchase order (and later per supplier contract), not per product.
 */
@Entity
@Table(name = "product")
class Product(
    sku: String,
    name: String,
    description: String?,
    unitOfMeasure: UnitOfMeasure,
) : BaseEntity() {

    /** Stock keeping unit: the immutable business key, e.g. "BOLT-M8-40". */
    @Column(nullable = false, updatable = false, length = 64)
    var sku: String = sku.trim().uppercase()
        protected set

    @Column(nullable = false, length = 200)
    var name: String = name.trim()
        protected set

    @Column(length = 2000)
    var description: String? = description?.trim()?.ifEmpty { null }
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "unit_of_measure", nullable = false, length = 16)
    var unitOfMeasure: UnitOfMeasure = unitOfMeasure
        protected set

    @Column(nullable = false)
    var active: Boolean = true
        protected set

    init {
        requireRule(this.sku.isNotEmpty()) { "SKU must not be blank" }
        validateDetails()
    }

    fun updateDetails(name: String, description: String?, unitOfMeasure: UnitOfMeasure) {
        this.name = name.trim()
        this.description = description?.trim()?.ifEmpty { null }
        this.unitOfMeasure = unitOfMeasure
        validateDetails()
    }

    fun activate() {
        active = true
    }

    /** Inactive products stay on historic documents but cannot be ordered anymore. */
    fun deactivate() {
        active = false
    }

    private fun validateDetails() {
        requireRule(name.isNotEmpty()) { "Product name must not be blank" }
    }
}
