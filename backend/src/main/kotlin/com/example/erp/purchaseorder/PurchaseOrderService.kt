package com.example.erp.purchaseorder

import com.example.erp.product.ProductService
import com.example.erp.shared.error.BusinessRuleViolationException
import com.example.erp.shared.error.NotFoundException
import com.example.erp.supplier.SupplierService
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
@Transactional
class PurchaseOrderService(
    private val purchaseOrders: PurchaseOrderRepository,
    private val supplierService: SupplierService,
    private val productService: ProductService,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun create(request: CreatePurchaseOrderRequest): PurchaseOrder {
        val supplier = supplierService.get(request.supplierId)
        val po = purchaseOrders.save(
            PurchaseOrder(
                poNumber = "PO-%06d".format(purchaseOrders.nextPoNumber()),
                supplier = supplier,
                orderDate = request.orderDate ?: LocalDate.now(),
                currency = request.currency ?: supplier.currency,
                lines = toLines(request.items),
            ),
        )
        log.atInfo()
            .addKeyValue("purchaseOrderId", po.id)
            .addKeyValue("supplierId", supplier.id)
            .addKeyValue("itemCount", po.lines.size)
            .log("Purchase order created")
        return po
    }

    @Transactional(readOnly = true)
    fun get(id: Long): PurchaseOrder =
        purchaseOrders.findWithDetailsById(id) ?: throw NotFoundException("Purchase order $id not found")

    @Transactional(readOnly = true)
    fun list(status: PurchaseOrderStatus?): List<PurchaseOrder> {
        val newestFirst = Sort.by(Sort.Direction.DESC, "id")
        return if (status == null) purchaseOrders.findAll(newestFirst)
        else purchaseOrders.findAllByStatus(status, newestFirst)
    }

    fun update(id: Long, request: UpdatePurchaseOrderRequest): PurchaseOrder =
        get(id).apply {
            updateDraft(
                orderDate = request.orderDate,
                currency = request.currency,
                lines = toLines(request.items),
            )
        }

    fun approve(id: Long): PurchaseOrder =
        get(id).apply {
            approve()
            logStatusChange(this)
        }

    fun cancel(id: Long): PurchaseOrder =
        get(id).apply {
            cancel()
            logStatusChange(this)
        }

    fun close(id: Long): PurchaseOrder =
        get(id).apply {
            close()
            logStatusChange(this)
        }

    /** Loads the PO with a row lock; used when posting goods receipts (must run inside the caller's transaction). */
    @Transactional(propagation = Propagation.MANDATORY)
    fun getForUpdate(id: Long): PurchaseOrder =
        purchaseOrders.findForUpdateById(id) ?: throw NotFoundException("Purchase order $id not found")

    private fun toLines(items: List<PurchaseOrderItemRequest>): List<PurchaseOrderLine> {
        val duplicates = items.groupBy { it.productId }.filterValues { it.size > 1 }.keys
        if (duplicates.isNotEmpty()) {
            throw BusinessRuleViolationException("Each product may appear only once per purchase order (product ids $duplicates)")
        }
        return items.map {
            PurchaseOrderLine(
                product = productService.get(it.productId),
                description = it.description,
                quantity = it.quantity,
                unitPrice = it.unitPrice,
                taxRate = it.taxRate,
            )
        }
    }

    private fun logStatusChange(po: PurchaseOrder) {
        log.atInfo()
            .addKeyValue("purchaseOrderId", po.id)
            .addKeyValue("supplierId", po.supplier.id)
            .addKeyValue("status", po.status)
            .log("Purchase order status changed")
    }
}
