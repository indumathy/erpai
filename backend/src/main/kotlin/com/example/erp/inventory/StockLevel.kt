package com.example.erp.inventory

import com.example.erp.product.Product
import com.example.erp.shared.error.requireRule
import com.example.erp.shared.money.MoneyRounding
import com.example.erp.shared.persistence.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.math.BigDecimal

/**
 * Current quantity of one product in one warehouse.
 * Only [InventoryService] changes it, always together with a [StockMovement].
 */
@Entity
@Table(name = "stock_level")
class StockLevel(warehouse: Warehouse, product: Product) : BaseEntity() {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false, updatable = false)
    var warehouse: Warehouse = warehouse
        protected set

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, updatable = false)
    var product: Product = product
        protected set

    @Column(name = "quantity_on_hand", nullable = false, precision = 19, scale = 3)
    var quantityOnHand: BigDecimal = MoneyRounding.quantity(BigDecimal.ZERO)
        protected set

    /** Adds a signed quantity. Stock can never go below zero. */
    fun apply(change: BigDecimal) {
        val newQuantity = MoneyRounding.quantity(quantityOnHand + change)
        requireRule(newQuantity.signum() >= 0) {
            "Insufficient stock of ${product.sku} in ${warehouse.code}: on hand $quantityOnHand, change $change"
        }
        quantityOnHand = newQuantity
    }
}
