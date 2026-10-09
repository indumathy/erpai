package com.example.erp.purchaseorder

import com.example.erp.product.UnitOfMeasure
import com.example.erp.shared.money.DocumentTotals
import com.example.erp.shared.money.TaxBreakdown
import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Digits
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

// ---- requests ---------------------------------------------------------------------------------
// Note: there are deliberately no total/amount fields. The backend calculates all totals.

data class PurchaseOrderItemRequest(
    val productId: Long,

    @field:Size(max = 500)
    val description: String? = null,

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

data class CreatePurchaseOrderRequest(
    val supplierId: Long,

    /** Defaults to today. */
    val orderDate: LocalDate? = null,

    /** Defaults to the supplier's currency. */
    @field:Pattern(regexp = "[A-Z]{3}", message = "must be an ISO 4217 code such as EUR")
    val currency: String? = null,

    @field:NotEmpty
    @field:Valid
    val items: List<PurchaseOrderItemRequest>,
)

/** Replaces header fields and all lines of a DRAFT purchase order. Supplier cannot change. */
data class UpdatePurchaseOrderRequest(
    val orderDate: LocalDate,

    @field:Pattern(regexp = "[A-Z]{3}", message = "must be an ISO 4217 code such as EUR")
    val currency: String,

    @field:NotEmpty
    @field:Valid
    val items: List<PurchaseOrderItemRequest>,
)

// ---- responses --------------------------------------------------------------------------------

data class SupplierRef(val id: Long, val supplierNumber: String, val name: String)

data class PurchaseOrderItemResponse(
    val id: Long,
    val lineNumber: Int,
    val productId: Long,
    val sku: String,
    val unitOfMeasure: UnitOfMeasure,
    val description: String,
    val quantity: BigDecimal,
    val unitPrice: BigDecimal,
    val taxRate: BigDecimal,
    val netAmount: BigDecimal,
    val receivedQuantity: BigDecimal,
    val openQuantity: BigDecimal,
)

data class TotalsResponse(
    val subtotal: BigDecimal,
    val taxAmount: BigDecimal,
    val total: BigDecimal,
    val taxBreakdown: List<TaxBreakdown>,
)

data class PurchaseOrderResponse(
    val id: Long,
    val poNumber: String,
    val status: PurchaseOrderStatus,
    val supplier: SupplierRef,
    val orderDate: LocalDate,
    val currency: String,
    val items: List<PurchaseOrderItemResponse>,
    val totals: TotalsResponse,
    val version: Long,
    val createdAt: Instant?,
    val updatedAt: Instant?,
)

/** Lighter list row: no lines, just what an overview table shows. */
data class PurchaseOrderSummaryResponse(
    val id: Long,
    val poNumber: String,
    val status: PurchaseOrderStatus,
    val supplier: SupplierRef,
    val orderDate: LocalDate,
    val currency: String,
    val itemCount: Int,
    val total: BigDecimal,
)

// ---- mapping ----------------------------------------------------------------------------------

private fun PurchaseOrder.supplierRef() =
    SupplierRef(supplier.requireId(), supplier.supplierNumber, supplier.name)

fun DocumentTotals.toResponse() = TotalsResponse(subtotal, taxAmount, total, taxBreakdown)

fun PurchaseOrder.toResponse() = PurchaseOrderResponse(
    id = requireId(),
    poNumber = poNumber,
    status = status,
    supplier = supplierRef(),
    orderDate = orderDate,
    currency = currency,
    items = lines.map {
        PurchaseOrderItemResponse(
            id = it.requireId(),
            lineNumber = it.lineNumber,
            productId = it.product.requireId(),
            sku = it.product.sku,
            unitOfMeasure = it.product.unitOfMeasure,
            description = it.description,
            quantity = it.quantity,
            unitPrice = it.unitPrice,
            taxRate = it.taxRate,
            netAmount = it.netAmount,
            receivedQuantity = it.receivedQuantity,
            openQuantity = it.openQuantity,
        )
    },
    totals = totals().toResponse(),
    version = version,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun PurchaseOrder.toSummaryResponse() = PurchaseOrderSummaryResponse(
    id = requireId(),
    poNumber = poNumber,
    status = status,
    supplier = supplierRef(),
    orderDate = orderDate,
    currency = currency,
    itemCount = lines.size,
    total = totals().total,
)
