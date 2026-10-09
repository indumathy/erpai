package com.example.erp.invoice

import com.example.erp.goodsreceipt.PurchaseOrderRef
import com.example.erp.purchaseorder.SupplierRef
import com.example.erp.purchaseorder.TotalsResponse
import com.example.erp.purchaseorder.toResponse
import com.example.erp.invoice.matching.MatchException
import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Digits
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

// ---- requests ---------------------------------------------------------------------------------

data class SupplierInvoiceItemRequest(
    /** Null for lines without a PO line, e.g. freight. */
    val purchaseOrderItemId: Long? = null,
    @field:NotBlank
    @field:Size(max = 500)
    val description: String,
    @field:Positive
    @field:Digits(integer = 16, fraction = 3)
    val quantity: BigDecimal,
    @field:PositiveOrZero
    @field:Digits(integer = 15, fraction = 4)
    val unitPrice: BigDecimal,
    @field:DecimalMin("0")
    @field:DecimalMax("100")
    @field:Digits(integer = 3, fraction = 2)
    val taxRate: BigDecimal,
)

data class CreateSupplierInvoiceRequest(
    val supplierId: Long,
    @field:NotBlank
    @field:Size(max = 64)
    val invoiceNumber: String,
    val invoiceDate: LocalDate,
    @field:Pattern(regexp = "[A-Z]{3}", message = "must be an ISO 4217 code such as EUR")
    val currency: String,
    /** Null when the supplier's invoice carries no PO number. */
    val purchaseOrderId: Long? = null,
    @field:NotEmpty
    @field:Valid
    val items: List<SupplierInvoiceItemRequest>,
)

// ---- responses --------------------------------------------------------------------------------

data class SupplierInvoiceItemResponse(
    val id: Long,
    val lineNumber: Int,
    val purchaseOrderItemId: Long?,
    val purchaseOrderLineNumber: Int?,
    val description: String,
    val quantity: BigDecimal,
    val unitPrice: BigDecimal,
    val taxRate: BigDecimal,
    val netAmount: BigDecimal,
)

data class SupplierInvoiceResponse(
    val id: Long,
    val invoiceNumber: String,
    val supplier: SupplierRef,
    val purchaseOrder: PurchaseOrderRef?,
    val invoiceDate: LocalDate,
    val currency: String,
    val items: List<SupplierInvoiceItemResponse>,
    val totals: TotalsResponse,
    val createdAt: Instant?,
)

data class SupplierInvoiceSummaryResponse(
    val id: Long,
    val invoiceNumber: String,
    val supplier: SupplierRef,
    val purchaseOrder: PurchaseOrderRef?,
    val invoiceDate: LocalDate,
    val currency: String,
    val total: BigDecimal,
    /** Number of match exceptions; 0 = matched. */
    val exceptionCount: Int,
)

data class MatchResultResponse(val matched: Boolean, val exceptions: List<MatchException>)

// ---- mapping ----------------------------------------------------------------------------------

private fun SupplierInvoice.supplierRef() = SupplierRef(supplier.requireId(), supplier.supplierNumber, supplier.name)

private fun SupplierInvoice.poRef() = purchaseOrder?.let { PurchaseOrderRef(it.requireId(), it.poNumber) }

fun SupplierInvoice.toResponse() = SupplierInvoiceResponse(
    id = requireId(),
    invoiceNumber = invoiceNumber,
    supplier = supplierRef(),
    purchaseOrder = poRef(),
    invoiceDate = invoiceDate,
    currency = currency,
    items = lines.map {
        SupplierInvoiceItemResponse(
            id = it.requireId(),
            lineNumber = it.lineNumber,
            purchaseOrderItemId = it.purchaseOrderItem?.requireId(),
            purchaseOrderLineNumber = it.purchaseOrderItem?.lineNumber,
            description = it.description,
            quantity = it.quantity,
            unitPrice = it.unitPrice,
            taxRate = it.taxRate,
            netAmount = it.netAmount,
        )
    },
    totals = totals().toResponse(),
    createdAt = createdAt,
)

fun SupplierInvoice.toSummaryResponse(exceptionCount: Int) = SupplierInvoiceSummaryResponse(
    id = requireId(),
    invoiceNumber = invoiceNumber,
    supplier = supplierRef(),
    purchaseOrder = poRef(),
    invoiceDate = invoiceDate,
    currency = currency,
    total = totals().total,
    exceptionCount = exceptionCount,
)
