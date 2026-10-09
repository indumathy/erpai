package com.example.erp.inventory

import com.example.erp.product.Product
import com.example.erp.product.ProductService
import com.example.erp.shared.error.ConflictException
import com.example.erp.shared.error.NotFoundException
import com.example.erp.shared.error.requireRule
import com.example.erp.shared.money.MoneyRounding
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Sort
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Service
@Transactional
class InventoryService(
    private val warehouses: WarehouseRepository,
    private val stockLevels: StockLevelRepository,
    private val movements: StockMovementRepository,
    private val productService: ProductService,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    // ---- warehouses -------------------------------------------------------------------------

    fun createWarehouse(request: CreateWarehouseRequest): Warehouse {
        val warehouse = Warehouse(request.code, request.name)
        if (warehouses.existsByCode(warehouse.code)) {
            throw ConflictException("Warehouse ${warehouse.code} already exists")
        }
        return warehouses.save(warehouse)
    }

    @Transactional(readOnly = true)
    fun getWarehouse(id: Long): Warehouse =
        warehouses.findByIdOrNull(id) ?: throw NotFoundException("Warehouse $id not found")

    @Transactional(readOnly = true)
    fun listWarehouses(): List<Warehouse> = warehouses.findAll(Sort.by(Warehouse::code.name))

    fun setWarehouseActive(id: Long, active: Boolean): Warehouse =
        getWarehouse(id).apply { if (active) activate() else deactivate() }

    // ---- stock ------------------------------------------------------------------------------

    /**
     * The single entry point for changing stock: updates the balance and writes the ledger row
     * in the same transaction, so the two can never disagree.
     */
    fun post(
        warehouse: Warehouse,
        product: Product,
        quantity: BigDecimal,
        type: MovementType,
        reference: StockReference? = null,
        note: String? = null,
    ): StockMovement {
        requireRule(warehouse.active) { "Warehouse ${warehouse.code} is inactive" }
        val change = MoneyRounding.quantity(quantity)
        requireRule(change.signum() != 0) { "A stock movement needs a non-zero quantity" }

        val level = stockLevels.findForUpdateByWarehouseIdAndProductId(warehouse.requireId(), product.requireId())
            ?: stockLevels.save(StockLevel(warehouse, product))
        level.apply(change)

        val movement = movements.save(StockMovement(warehouse, product, change, type, reference, note))
        log.atInfo()
            .addKeyValue("warehouseId", warehouse.id)
            .addKeyValue("productId", product.id)
            .addKeyValue("movementType", type)
            .addKeyValue("referenceNumber", reference?.number)
            .log("Stock movement posted")
        return movement
    }

    /** Manual correction, e.g. after a physical stock count. */
    fun adjust(request: StockAdjustmentRequest): StockMovement =
        post(
            warehouse = getWarehouse(request.warehouseId),
            product = productService.get(request.productId),
            quantity = request.quantity,
            type = MovementType.ADJUSTMENT,
            note = request.reason,
        )

    @Transactional(readOnly = true)
    fun stock(warehouseId: Long?): List<StockLevel> {
        val sort = Sort.by("warehouse.code", "product.sku")
        return if (warehouseId == null) stockLevels.findAll(sort) else stockLevels.findAllByWarehouseId(warehouseId, sort)
    }

    @Transactional(readOnly = true)
    fun movements(productId: Long?): List<StockMovement> =
        if (productId == null) movements.findTop200ByOrderByIdDesc()
        else movements.findTop200ByProductIdOrderByIdDesc(productId)
}
