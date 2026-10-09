package com.example.erp.inventory

import jakarta.persistence.LockModeType
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock

interface WarehouseRepository : JpaRepository<Warehouse, Long> {
    fun existsByCode(code: String): Boolean
}

interface StockLevelRepository : JpaRepository<StockLevel, Long> {

    /** Row lock (SELECT ... FOR UPDATE): concurrent postings for the same product/warehouse queue up. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findForUpdateByWarehouseIdAndProductId(warehouseId: Long, productId: Long): StockLevel?

    @EntityGraph(attributePaths = ["warehouse", "product"])
    override fun findAll(sort: Sort): List<StockLevel>

    @EntityGraph(attributePaths = ["warehouse", "product"])
    fun findAllByWarehouseId(warehouseId: Long, sort: Sort): List<StockLevel>
}

interface StockMovementRepository : JpaRepository<StockMovement, Long> {

    @EntityGraph(attributePaths = ["warehouse", "product"])
    fun findTop200ByOrderByIdDesc(): List<StockMovement>

    @EntityGraph(attributePaths = ["warehouse", "product"])
    fun findTop200ByProductIdOrderByIdDesc(productId: Long): List<StockMovement>
}
