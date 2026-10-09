package com.example.erp.invoice

import com.example.erp.invoice.matching.InvoiceLineSnapshot
import com.example.erp.invoice.matching.InvoiceMatcher
import com.example.erp.invoice.matching.InvoiceSnapshot
import com.example.erp.invoice.matching.MatchInput
import com.example.erp.invoice.matching.PurchaseOrderLineSnapshot
import com.example.erp.invoice.matching.PurchaseOrderSnapshot
import com.example.erp.purchaseorder.PurchaseOrderService
import com.example.erp.shared.error.BusinessRuleViolationException
import com.example.erp.shared.error.NotFoundException
import com.example.erp.shared.money.MoneyRounding
import com.example.erp.supplier.SupplierService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

/**
 * Returns response DTOs (like GoodsReceiptService): an invoice spans supplier, PO and PO lines,
 * and mapping inside the transaction keeps lazy associations loadable with open-in-view disabled.
 */
@Service
@Transactional
class SupplierInvoiceService(
    private val invoices: SupplierInvoiceRepository,
    private val supplierService: SupplierService,
    private val purchaseOrderService: PurchaseOrderService,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun create(request: CreateSupplierInvoiceRequest): SupplierInvoiceResponse {
        val supplier = supplierService.get(request.supplierId)
        val po = request.purchaseOrderId?.let { purchaseOrderService.get(it) }

        val lines = request.items.map { item ->
            val poItem = item.purchaseOrderItemId?.let { poItemId ->
                po?.lines?.find { it.id == poItemId }
                    ?: throw BusinessRuleViolationException(
                        "Purchase order item $poItemId does not belong to " + (po?.poNumber ?: "the invoice (no purchase order given)"),
                    )
            }
            NewInvoiceLine(poItem, item.description, item.quantity, item.unitPrice, item.taxRate)
        }

        val invoice = invoices.save(
            SupplierInvoice(supplier, request.invoiceNumber, request.invoiceDate, request.currency, po, lines),
        )
        log.atInfo()
            .addKeyValue("invoiceId", invoice.id)
            .addKeyValue("supplierId", supplier.id)
            .addKeyValue("purchaseOrderId", po?.id)
            .log("Supplier invoice created")
        return invoice.toResponse()
    }

    @Transactional(readOnly = true)
    fun get(id: Long): SupplierInvoiceResponse = load(id).toResponse()

    @Transactional(readOnly = true)
    fun list(): List<SupplierInvoiceSummaryResponse> =
        invoices.findAllByOrderByIdDesc().map { it.toSummaryResponse(InvoiceMatcher.match(matchInput(it)).size) }

    @Transactional(readOnly = true)
    fun match(id: Long): MatchResultResponse {
        val exceptions = InvoiceMatcher.match(matchInput(load(id)))
        return MatchResultResponse(matched = exceptions.isEmpty(), exceptions = exceptions)
    }

    /** Snapshot of everything the matcher needs. Also the future input of eval datasets and AI tools. */
    internal fun matchInput(invoice: SupplierInvoice): MatchInput {
        val invoiceId = invoice.requireId()
        val po = invoice.purchaseOrder?.let { purchaseOrderService.get(it.requireId()) }

        val poSnapshot = po?.let {
            val poLines = it.lines
            val invoicedBefore = invoices
                .findEarlierInvoicedQuantities(poLines.map { line -> line.requireId() }, invoiceId)
                .associate { q -> q.purchaseOrderItemId to q.quantity }
            PurchaseOrderSnapshot(
                id = it.requireId(),
                poNumber = it.poNumber,
                currency = it.currency,
                lines = poLines.map { line ->
                    PurchaseOrderLineSnapshot(
                        id = line.requireId(),
                        lineNumber = line.lineNumber,
                        sku = line.product.sku,
                        quantity = line.quantity,
                        unitPrice = line.unitPrice,
                        taxRate = line.taxRate,
                        receivedQuantity = line.receivedQuantity,
                        alreadyInvoicedQuantity = MoneyRounding.quantity(invoicedBefore[line.requireId()] ?: BigDecimal.ZERO),
                    )
                },
            )
        }

        return MatchInput(
            invoice = InvoiceSnapshot(
                id = invoiceId,
                invoiceNumber = invoice.invoiceNumber,
                currency = invoice.currency,
                lines = invoice.lines.map {
                    InvoiceLineSnapshot(
                        lineNumber = it.lineNumber,
                        purchaseOrderItemId = it.purchaseOrderItem?.requireId(),
                        description = it.description,
                        quantity = it.quantity,
                        unitPrice = it.unitPrice,
                        taxRate = it.taxRate,
                    )
                },
            ),
            purchaseOrder = poSnapshot,
            duplicateInvoiceIds = invoices.findDuplicateIds(invoice.supplier.requireId(), invoice.invoiceNumber, invoiceId),
        )
    }

    private fun load(id: Long): SupplierInvoice =
        invoices.findWithDetailsById(id) ?: throw NotFoundException("Invoice $id not found")
}
