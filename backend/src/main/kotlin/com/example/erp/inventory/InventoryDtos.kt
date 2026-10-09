package com.example.erp.inventory

import com.example.erp.product.Product
import com.example.erp.product.UnitOfMeasure
import jakarta.validation.constraints.Digits
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.Instant

data class CreateWarehouseRequest(
    @field:NotBlank
    @field:Size(max = 16)
    @field:Pattern(regexp = "[A-Za-z0-9_-]+", message = "may only contain letters, digits, '_' and '-'")
    val code: String,

    @field:NotBlank
    @field:Size(max = 200)
    val name: String,
)

data class StockAdjustmentRequest(
    val warehouseId: Long,
    val productId: Long,

    /** Signed: +5 adds stock, -2 removes stock. */
    @field:Digits(integer = 16, fraction = 3)
    val quantity: BigDecimal,

    @field:NotBlank
    @field:Size(max = 500)
    val reason: String,
)

data class WarehouseResponse(val id: Long, val code: String, val name: String, val active: Boolean)

data class ProductRef(val id: Long, val sku: String, val name: String, val unitOfMeasure: UnitOfMeasure)

data class WarehouseRef(val id: Long, val code: String, val name: String)

data class StockLevelResponse(
    val warehouse: WarehouseRef,
    val product: ProductRef,
    val quantityOnHand: BigDecimal,
    val updatedAt: Instant?,
)

data class StockMovementResponse(
    val id: Long,
    val warehouse: WarehouseRef,
    val product: ProductRef,
    val quantity: BigDecimal,
    val movementType: MovementType,
    val referenceType: String?,
    val referenceId: Long?,
    val referenceNumber: String?,
    val note: String?,
    val createdAt: Instant?,
)

fun Warehouse.toResponse() = WarehouseResponse(requireId(), code, name, active)

fun Warehouse.toRef() = WarehouseRef(requireId(), code, name)

fun Product.toRef() = ProductRef(requireId(), sku, name, unitOfMeasure)

fun StockLevel.toResponse() = StockLevelResponse(warehouse.toRef(), product.toRef(), quantityOnHand, updatedAt)

fun StockMovement.toResponse() = StockMovementResponse(
    id = requireId(),
    warehouse = warehouse.toRef(),
    product = product.toRef(),
    quantity = quantity,
    movementType = movementType,
    referenceType = referenceType,
    referenceId = referenceId,
    referenceNumber = referenceNumber,
    note = note,
    createdAt = createdAt,
)
