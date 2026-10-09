package com.example.erp.goodsreceipt

import com.example.erp.inventory.WarehouseRef
import com.example.erp.inventory.toRef
import com.example.erp.product.UnitOfMeasure
import com.example.erp.purchaseorder.SupplierRef
import jakarta.validation.Valid
import jakarta.validation.constraints.Digits
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

data class GoodsReceiptItemRequest(
    val purchaseOrderItemId: Long,

    @field:Positive
    @field:Digits(integer = 16, fraction = 3)
    val quantity: BigDecimal,
)

data class CreateGoodsReceiptRequest(
    val purchaseOrderId: Long,
    val warehouseId: Long,

    /** Defaults to today. */
    val receiptDate: LocalDate? = null,

    @field:Size(max = 64)
    val deliveryNoteNumber: String? = null,

    @field:NotEmpty
    @field:Valid
    val items: List<GoodsReceiptItemRequest>,
)

data class PurchaseOrderRef(val id: Long, val poNumber: String)

data class GoodsReceiptItemResponse(
    val id: Long,
    val purchaseOrderItemId: Long,
    val lineNumber: Int,
    val sku: String,
    val description: String,
    val unitOfMeasure: UnitOfMeasure,
    val quantityReceived: BigDecimal,
)

data class GoodsReceiptResponse(
    val id: Long,
    val grNumber: String,
    val purchaseOrder: PurchaseOrderRef,
    val supplier: SupplierRef,
    val warehouse: WarehouseRef,
    val receiptDate: LocalDate,
    val deliveryNoteNumber: String?,
    val items: List<GoodsReceiptItemResponse>,
    val createdAt: Instant?,
)

data class GoodsReceiptSummaryResponse(
    val id: Long,
    val grNumber: String,
    val purchaseOrder: PurchaseOrderRef,
    val supplier: SupplierRef,
    val warehouse: WarehouseRef,
    val receiptDate: LocalDate,
    val deliveryNoteNumber: String?,
    val itemCount: Int,
)

private fun GoodsReceipt.poRef() = PurchaseOrderRef(purchaseOrder.requireId(), purchaseOrder.poNumber)

private fun GoodsReceipt.supplierRef() =
    purchaseOrder.supplier.let { SupplierRef(it.requireId(), it.supplierNumber, it.name) }

fun GoodsReceipt.toResponse() = GoodsReceiptResponse(
    id = requireId(),
    grNumber = grNumber,
    purchaseOrder = poRef(),
    supplier = supplierRef(),
    warehouse = warehouse.toRef(),
    receiptDate = receiptDate,
    deliveryNoteNumber = deliveryNoteNumber,
    items = lines.map {
        val poItem = it.purchaseOrderItem
        GoodsReceiptItemResponse(
            id = it.requireId(),
            purchaseOrderItemId = poItem.requireId(),
            lineNumber = poItem.lineNumber,
            sku = poItem.product.sku,
            description = poItem.description,
            unitOfMeasure = poItem.product.unitOfMeasure,
            quantityReceived = it.quantityReceived,
        )
    },
    createdAt = createdAt,
)

fun GoodsReceipt.toSummaryResponse() = GoodsReceiptSummaryResponse(
    id = requireId(),
    grNumber = grNumber,
    purchaseOrder = poRef(),
    supplier = supplierRef(),
    warehouse = warehouse.toRef(),
    receiptDate = receiptDate,
    deliveryNoteNumber = deliveryNoteNumber,
    itemCount = itemCount,
)
