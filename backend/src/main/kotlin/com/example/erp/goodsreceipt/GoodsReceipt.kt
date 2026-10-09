package com.example.erp.goodsreceipt

import com.example.erp.inventory.Warehouse
import com.example.erp.purchaseorder.PurchaseOrder
import com.example.erp.purchaseorder.PurchaseOrderItem
import com.example.erp.purchaseorder.ReceivedQuantity
import com.example.erp.shared.error.requireRule
import com.example.erp.shared.persistence.BaseEntity
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.LocalDate

/**
 * A posted goods receipt: the record of what physically arrived for a purchase order.
 * Immutable once created. Quantity rules are enforced by [PurchaseOrder.receive] beforehand.
 */
@Entity
@Table(name = "goods_receipt")
class GoodsReceipt(
    grNumber: String,
    purchaseOrder: PurchaseOrder,
    warehouse: Warehouse,
    receiptDate: LocalDate,
    deliveryNoteNumber: String?,
    receipts: List<ReceivedQuantity>,
) : BaseEntity() {

    @Column(name = "gr_number", nullable = false, updatable = false, length = 32)
    var grNumber: String = grNumber
        protected set

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_order_id", nullable = false, updatable = false)
    var purchaseOrder: PurchaseOrder = purchaseOrder
        protected set

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false, updatable = false)
    var warehouse: Warehouse = warehouse
        protected set

    @Column(name = "receipt_date", nullable = false, updatable = false)
    var receiptDate: LocalDate = receiptDate
        protected set

    @Column(name = "delivery_note_number", updatable = false, length = 64)
    var deliveryNoteNumber: String? = deliveryNoteNumber?.trim()?.ifEmpty { null }
        protected set

    @OneToMany(mappedBy = "goodsReceipt", cascade = [CascadeType.ALL])
    protected var items: MutableList<GoodsReceiptItem> = mutableListOf()

    val lines: List<GoodsReceiptItem>
        get() = items.sortedBy { it.purchaseOrderItem.lineNumber }

    val itemCount: Int
        get() = items.size

    init {
        requireRule(receipts.isNotEmpty()) { "A goods receipt needs at least one item" }
        requireRule(warehouse.active) { "Warehouse ${warehouse.code} is inactive" }
        requireRule(!receiptDate.isAfter(LocalDate.now())) { "Receipt date $receiptDate lies in the future" }
        requireRule(!receiptDate.isBefore(purchaseOrder.orderDate)) {
            "Receipt date $receiptDate is before the order date ${purchaseOrder.orderDate}"
        }
        receipts.forEach { items += GoodsReceiptItem(this, it.item, it.quantity) }
    }
}

@Entity
@Table(name = "goods_receipt_item")
class GoodsReceiptItem(
    goodsReceipt: GoodsReceipt,
    purchaseOrderItem: PurchaseOrderItem,
    quantityReceived: BigDecimal,
) : BaseEntity() {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "goods_receipt_id", nullable = false, updatable = false)
    var goodsReceipt: GoodsReceipt = goodsReceipt
        protected set

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_order_item_id", nullable = false, updatable = false)
    var purchaseOrderItem: PurchaseOrderItem = purchaseOrderItem
        protected set

    @Column(name = "quantity_received", nullable = false, updatable = false, precision = 19, scale = 3)
    var quantityReceived: BigDecimal = quantityReceived
        protected set
}
