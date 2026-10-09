package com.example.erp.purchaseorder

import com.example.erp.product.Product
import com.example.erp.shared.error.requireRule
import com.example.erp.shared.money.CurrencyCodes
import com.example.erp.shared.money.DocumentTotals
import com.example.erp.shared.money.MoneyRounding
import com.example.erp.shared.money.TaxableLine
import com.example.erp.shared.persistence.BaseEntity
import com.example.erp.supplier.Supplier
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.OrderBy
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.math.BigDecimal
import java.time.LocalDate

/** Input for one PO line, before it becomes a [PurchaseOrderItem]. */
data class PurchaseOrderLine(
    val product: Product,
    val description: String?,
    val quantity: BigDecimal,
    val unitPrice: BigDecimal,
    val taxRate: BigDecimal,
)

/** Quantity arriving for one PO line in a goods receipt. */
data class ReceivedQuantity(val item: PurchaseOrderItem, val quantity: BigDecimal)

/**
 * Aggregate root: the order and its items are created, changed and validated together.
 * All state changes go through methods that enforce the business rules and the status flow.
 */
@Entity
@Table(name = "purchase_order")
class PurchaseOrder(
    poNumber: String,
    supplier: Supplier,
    orderDate: LocalDate,
    currency: String,
    lines: List<PurchaseOrderLine>,
) : BaseEntity() {

    @Column(name = "po_number", nullable = false, updatable = false, length = 32)
    var poNumber: String = poNumber
        protected set

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id", nullable = false, updatable = false)
    var supplier: Supplier = supplier
        protected set

    @Column(name = "order_date", nullable = false)
    var orderDate: LocalDate = orderDate
        protected set

    @Column(nullable = false, length = 3)
    var currency: String = currency
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    var status: PurchaseOrderStatus = PurchaseOrderStatus.DRAFT
        protected set

    /** Optimistic locking: concurrent modifications fail instead of overwriting each other. */
    @Version
    @Column(nullable = false)
    var version: Long = 0
        protected set

    @OneToMany(mappedBy = "purchaseOrder", cascade = [CascadeType.ALL], orphanRemoval = true)
    @OrderBy("lineNumber")
    protected var items: MutableList<PurchaseOrderItem> = mutableListOf()

    /** Read-only view; items change only through [updateDraft]. */
    val lines: List<PurchaseOrderItem>
        get() = items.toList()

    init {
        requireRule(supplier.active) { "Supplier ${supplier.supplierNumber} is inactive" }
        validateHeader()
        replaceItems(lines)
    }

    fun totals(): DocumentTotals = DocumentTotals.of(items.map { TaxableLine(it.netAmount, it.taxRate) })

    fun updateDraft(orderDate: LocalDate, currency: String, lines: List<PurchaseOrderLine>) {
        requireStatus(PurchaseOrderStatus.DRAFT) { "Only draft purchase orders can be edited" }
        this.orderDate = orderDate
        this.currency = currency
        validateHeader()
        replaceItems(lines)
    }

    /** Commits the order to the supplier. From now on the lines are frozen. */
    fun approve() {
        requireStatus(PurchaseOrderStatus.DRAFT) { "Only draft purchase orders can be approved" }
        requireRule(supplier.active) { "Supplier ${supplier.supplierNumber} is inactive" }
        status = PurchaseOrderStatus.APPROVED
    }

    fun cancel() {
        requireRule(status == PurchaseOrderStatus.DRAFT || status == PurchaseOrderStatus.APPROVED) {
            "A purchase order in status $status cannot be cancelled"
        }
        status = PurchaseOrderStatus.CANCELLED
    }

    /**
     * Books arriving goods against the order. The PO is the authority on how much is still open,
     * so the over-receipt rule lives here, not in the goods receipt.
     */
    fun receive(receipts: List<ReceivedQuantity>) {
        requireRule(status == PurchaseOrderStatus.APPROVED || status == PurchaseOrderStatus.PARTIALLY_RECEIVED) {
            "Goods can only be received for approved purchase orders (current status: $status)"
        }
        requireRule(receipts.isNotEmpty()) { "A goods receipt needs at least one item" }
        requireRule(receipts.map { it.item }.toSet().size == receipts.size) {
            "Each purchase order line may appear only once per goods receipt"
        }
        receipts.forEach { (item, quantity) ->
            requireRule(item in items) { "Line ${item.lineNumber} does not belong to $poNumber" }
            requireRule(quantity.signum() > 0) { "Received quantity for line ${item.lineNumber} must be positive" }
            requireRule(quantity <= item.openQuantity) {
                "Line ${item.lineNumber} (${item.product.sku}): receiving $quantity exceeds the open quantity " +
                    "${item.openQuantity} (ordered ${item.quantity}, already received ${item.receivedQuantity})"
            }
        }

        receipts.forEach { (item, quantity) -> item.addReceived(quantity) }
        status = if (items.all { it.isFullyReceived }) PurchaseOrderStatus.RECEIVED
        else PurchaseOrderStatus.PARTIALLY_RECEIVED
    }

    /** Short-close: no further deliveries expected, e.g. the supplier cannot deliver the rest. */
    fun close() {
        requireRule(status == PurchaseOrderStatus.PARTIALLY_RECEIVED || status == PurchaseOrderStatus.RECEIVED) {
            "Only (partially) received purchase orders can be closed (current status: $status)"
        }
        status = PurchaseOrderStatus.CLOSED
    }

    private fun validateHeader() {
        requireRule(CurrencyCodes.isValid(currency)) { "Unknown ISO 4217 currency: $currency" }
    }

    private fun replaceItems(newLines: List<PurchaseOrderLine>) {
        requireRule(newLines.isNotEmpty()) { "A purchase order needs at least one item" }
        requireRule(newLines.map { it.product }.toSet().size == newLines.size) {
            "Each product may appear only once per purchase order"
        }
        newLines.forEach { line ->
            val sku = line.product.sku
            requireRule(line.product.active) { "Product $sku is inactive" }
            requireRule(line.quantity.signum() > 0) { "Quantity for $sku must be positive" }
            requireRule(line.unitPrice.signum() >= 0) { "Unit price for $sku must not be negative" }
            requireRule(line.taxRate.signum() >= 0 && line.taxRate <= MAX_TAX_RATE) {
                "Tax rate for $sku must be between 0 and 100 percent"
            }
        }

        items.clear()
        newLines.forEachIndexed { index, line ->
            items += PurchaseOrderItem(
                purchaseOrder = this,
                lineNumber = index + 1,
                product = line.product,
                description = line.description?.trim()?.ifEmpty { null } ?: line.product.name,
                quantity = MoneyRounding.quantity(line.quantity),
                unitPrice = MoneyRounding.unitPrice(line.unitPrice),
                taxRate = MoneyRounding.percent(line.taxRate),
            )
        }
    }

    private inline fun requireStatus(expected: PurchaseOrderStatus, message: () -> String) =
        requireRule(status == expected) { "${message()} (current status: $status)" }

    private companion object {
        val MAX_TAX_RATE = BigDecimal(100)
    }
}
