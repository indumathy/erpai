package com.example.erp.inventory

import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.net.URI

@RestController
class InventoryController(private val service: InventoryService) {

    @PostMapping("/api/warehouses")
    fun createWarehouse(@Valid @RequestBody request: CreateWarehouseRequest): ResponseEntity<WarehouseResponse> {
        val warehouse = service.createWarehouse(request).toResponse()
        return ResponseEntity.created(URI("/api/warehouses/${warehouse.id}")).body(warehouse)
    }

    @GetMapping("/api/warehouses")
    fun listWarehouses(): List<WarehouseResponse> = service.listWarehouses().map { it.toResponse() }

    @GetMapping("/api/warehouses/{id}")
    fun getWarehouse(@PathVariable id: Long): WarehouseResponse = service.getWarehouse(id).toResponse()

    @PostMapping("/api/warehouses/{id}/activate")
    fun activateWarehouse(@PathVariable id: Long): WarehouseResponse = service.setWarehouseActive(id, true).toResponse()

    @PostMapping("/api/warehouses/{id}/deactivate")
    fun deactivateWarehouse(@PathVariable id: Long): WarehouseResponse =
        service.setWarehouseActive(id, false).toResponse()

    @GetMapping("/api/inventory/stock")
    fun stock(@RequestParam(required = false) warehouseId: Long?): List<StockLevelResponse> =
        service.stock(warehouseId).map { it.toResponse() }

    @GetMapping("/api/inventory/movements")
    fun movements(@RequestParam(required = false) productId: Long?): List<StockMovementResponse> =
        service.movements(productId).map { it.toResponse() }

    @PostMapping("/api/inventory/adjustments")
    @ResponseStatus(HttpStatus.CREATED)
    fun adjust(@Valid @RequestBody request: StockAdjustmentRequest): StockMovementResponse =
        service.adjust(request).toResponse()
}
