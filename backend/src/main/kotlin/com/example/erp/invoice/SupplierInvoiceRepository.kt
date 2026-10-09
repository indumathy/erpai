package com.example.erp.invoice

import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.math.BigDecimal

/** Quantity already invoiced for one PO line (Spring Data interface projection). */
interface InvoicedQuantity {
    val purchaseOrderItemId: Long
    val quantity: BigDecimal
}

interface SupplierInvoiceRepository : JpaRepository<SupplierInvoice, Long> {

    @EntityGraph(attributePaths = ["supplier", "purchaseOrder", "items", "items.purchaseOrderItem"])
    fun findWithDetailsById(id: Long): SupplierInvoice?

    @EntityGraph(attributePaths = ["supplier", "purchaseOrder", "items"])
    fun findAllByOrderByIdDesc(): List<SupplierInvoice>

    /** Other invoices of the same supplier with the same number, ignoring case and surrounding spaces. */
    @Query(
        """SELECT i.id FROM SupplierInvoice i
           WHERE i.supplier.id = :supplierId AND i.id <> :invoiceId
             AND lower(trim(i.invoiceNumber)) = lower(trim(:invoiceNumber))
           ORDER BY i.id""",
    )
    fun findDuplicateIds(supplierId: Long, invoiceNumber: String, invoiceId: Long): List<Long>

    /** Per PO line: quantity billed by invoices created BEFORE the given one (lower id). */
    @Query(
        """SELECT it.purchaseOrderItem.id AS purchaseOrderItemId, SUM(it.quantity) AS quantity
           FROM SupplierInvoiceItem it
           WHERE it.purchaseOrderItem.id IN :purchaseOrderItemIds AND it.invoice.id < :invoiceId
           GROUP BY it.purchaseOrderItem.id""",
    )
    fun findEarlierInvoicedQuantities(purchaseOrderItemIds: Collection<Long>, invoiceId: Long): List<InvoicedQuantity>
}
