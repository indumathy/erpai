package com.example.erp.invoice

import com.example.erp.purchaseorder.PurchaseOrder
import com.example.erp.purchaseorder.PurchaseOrderItem
import com.example.erp.shared.error.requireRule
import com.example.erp.shared.money.DocumentTotals
import com.example.erp.shared.money.MoneyRounding
import com.example.erp.shared.money.TaxableLine
import com.example.erp.shared.persistence.BaseEntity
import com.example.erp.supplier.Supplier
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

/** Input for one invoice line; the entity numbers the lines. */
data class NewInvoiceLine(
    val purchaseOrderItem: PurchaseOrderItem?,
    val description: String,
    val quantity: BigDecimal,
    val unitPrice: BigDecimal,
    val taxRate: BigDecimal,
)

/**
 * A supplier invoice as received. Immutable once created; whether it matches the PO and goods
 * receipts is not a property of the invoice but computed by InvoiceMatcher on demand.
 */
@Entity
@Table(name = "supplier_invoice")
class SupplierInvoice(
    supplier: Supplier,
    invoiceNumber: String,
    invoiceDate: LocalDate,
    currency: String,
    purchaseOrder: PurchaseOrder?,
    lines: List<NewInvoiceLine>,
) : BaseEntity() {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id", nullable = false, updatable = false)
    var supplier: Supplier = supplier
        protected set

    @Column(name = "invoice_number", nullable = false, updatable = false, length = 64)
    var invoiceNumber: String = invoiceNumber.trim()
        protected set

    @Column(name = "invoice_date", nullable = false, updatable = false)
    var invoiceDate: LocalDate = invoiceDate
        protected set

    @Column(nullable = false, updatable = false, length = 3)
    var currency: String = currency
        protected set

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_order_id", updatable = false)
    var purchaseOrder: PurchaseOrder? = purchaseOrder
        protected set

    @OneToMany(mappedBy = "invoice", cascade = [CascadeType.ALL])
    protected var items: MutableList<SupplierInvoiceItem> = mutableListOf()

    val lines: List<SupplierInvoiceItem>
        get() = items.sortedBy { it.lineNumber }

    fun totals(): DocumentTotals = DocumentTotals.of(items.map { TaxableLine(it.netAmount, it.taxRate) })

    init {
        requireRule(lines.isNotEmpty()) { "An invoice needs at least one line" }
        requireRule(!invoiceDate.isAfter(LocalDate.now())) { "Invoice date $invoiceDate lies in the future" }
        if (purchaseOrder != null) {
            requireRule(purchaseOrder.supplier.id == supplier.id) {
                "Purchase order ${purchaseOrder.poNumber} belongs to another supplier"
            }
        }
        val referenced = lines.mapNotNull { it.purchaseOrderItem }
        requireRule(referenced.isEmpty() || purchaseOrder != null) {
            "Invoice lines can only reference purchase order lines when the invoice references a purchase order"
        }
        requireRule(referenced.all { it.purchaseOrder.id == purchaseOrder?.id }) {
            "Invoice lines may only reference lines of ${purchaseOrder?.poNumber}"
        }
        requireRule(referenced.map { it.id }.toSet().size == referenced.size) {
            "Each purchase order line may appear only once per invoice"
        }
        lines.forEachIndexed { index, line -> items += SupplierInvoiceItem(this, index + 1, line) }
    }
}

@Entity
@Table(name = "supplier_invoice_item")
class SupplierInvoiceItem(
    invoice: SupplierInvoice,
    lineNumber: Int,
    line: NewInvoiceLine,
) : BaseEntity() {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_invoice_id", nullable = false, updatable = false)
    var invoice: SupplierInvoice = invoice
        protected set

    @Column(name = "line_number", nullable = false, updatable = false)
    var lineNumber: Int = lineNumber
        protected set

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_order_item_id", updatable = false)
    var purchaseOrderItem: PurchaseOrderItem? = line.purchaseOrderItem
        protected set

    @Column(nullable = false, updatable = false, length = 500)
    var description: String = line.description.trim()
        protected set

    @Column(nullable = false, updatable = false, precision = 19, scale = 3)
    var quantity: BigDecimal = MoneyRounding.quantity(line.quantity)
        protected set

    @Column(name = "unit_price", nullable = false, updatable = false, precision = 19, scale = 4)
    var unitPrice: BigDecimal = MoneyRounding.unitPrice(line.unitPrice)
        protected set

    @Column(name = "tax_rate", nullable = false, updatable = false, precision = 5, scale = 2)
    var taxRate: BigDecimal = MoneyRounding.percent(line.taxRate)
        protected set

    val netAmount: BigDecimal
        get() = DocumentTotals.lineNet(quantity, unitPrice)
}
