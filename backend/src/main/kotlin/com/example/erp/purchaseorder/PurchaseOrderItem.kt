package com.example.erp.purchaseorder

import com.example.erp.product.Product
import com.example.erp.shared.money.DocumentTotals
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
 * One ordered product. Created only by [PurchaseOrder]; validated there via [PurchaseOrderLine].
 */
@Entity
@Table(name = "purchase_order_item")
class PurchaseOrderItem(
    purchaseOrder: PurchaseOrder,
    lineNumber: Int,
    product: Product,
    description: String,
    quantity: BigDecimal,
    unitPrice: BigDecimal,
    taxRate: BigDecimal,
) : BaseEntity() {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_order_id", nullable = false, updatable = false)
    var purchaseOrder: PurchaseOrder = purchaseOrder
        protected set

    @Column(name = "line_number", nullable = false)
    var lineNumber: Int = lineNumber
        protected set

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    var product: Product = product
        protected set

    @Column(nullable = false, length = 500)
    var description: String = description
        protected set

    @Column(nullable = false, precision = 19, scale = 3)
    var quantity: BigDecimal = quantity
        protected set

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 4)
    var unitPrice: BigDecimal = unitPrice
        protected set

    /** Percent, e.g. 19.00 for 19 % VAT. */
    @Column(name = "tax_rate", nullable = false, precision = 5, scale = 2)
    var taxRate: BigDecimal = taxRate
        protected set

    /** Sum of all goods receipts posted for this line. Never exceeds [quantity]. */
    @Column(name = "received_quantity", nullable = false, precision = 19, scale = 3)
    var receivedQuantity: BigDecimal = MoneyRounding.quantity(BigDecimal.ZERO)
        protected set

    /** Net line amount (quantity x unit price, rounded to cents). Derived, not stored. */
    val netAmount: BigDecimal
        get() = DocumentTotals.lineNet(quantity, unitPrice)

    /** Still expected from the supplier. */
    val openQuantity: BigDecimal
        get() = quantity - receivedQuantity

    val isFullyReceived: Boolean
        get() = openQuantity.signum() == 0

    /** Called only by [PurchaseOrder.receive], which validates against [openQuantity] first. */
    internal fun addReceived(quantity: BigDecimal) {
        receivedQuantity = MoneyRounding.quantity(receivedQuantity + quantity)
    }
}
