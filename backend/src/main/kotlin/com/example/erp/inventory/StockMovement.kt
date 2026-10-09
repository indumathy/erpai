package com.example.erp.inventory

import com.example.erp.product.Product
import com.example.erp.shared.persistence.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.math.BigDecimal

enum class MovementType {
    /** Stock in from a posted goods receipt. */
    GOODS_RECEIPT,

    /** Manual correction, e.g. after a stock count. Can be positive or negative. */
    ADJUSTMENT,
}

/** The business document that caused a movement, e.g. ("GOODS_RECEIPT", 7, "GR-000007"). */
data class StockReference(val type: String, val id: Long, val number: String)

/**
 * One line in the stock ledger. Immutable: corrections are new movements, never edits.
 * Positive quantity = stock in, negative = stock out.
 */
@Entity
@Table(name = "stock_movement")
class StockMovement(
    warehouse: Warehouse,
    product: Product,
    quantity: BigDecimal,
    movementType: MovementType,
    reference: StockReference?,
    note: String?,
) : BaseEntity() {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false, updatable = false)
    var warehouse: Warehouse = warehouse
        protected set

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, updatable = false)
    var product: Product = product
        protected set

    @Column(nullable = false, updatable = false, precision = 19, scale = 3)
    var quantity: BigDecimal = quantity
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, updatable = false, length = 32)
    var movementType: MovementType = movementType
        protected set

    @Column(name = "reference_type", updatable = false, length = 32)
    var referenceType: String? = reference?.type
        protected set

    @Column(name = "reference_id", updatable = false)
    var referenceId: Long? = reference?.id
        protected set

    @Column(name = "reference_number", updatable = false, length = 32)
    var referenceNumber: String? = reference?.number
        protected set

    @Column(updatable = false, length = 500)
    var note: String? = note?.trim()?.ifEmpty { null }
        protected set
}
