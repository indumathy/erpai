package com.example.erp.purchaseorder

import jakarta.persistence.LockModeType
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface PurchaseOrderRepository : JpaRepository<PurchaseOrder, Long> {

    /** Loads everything the detail view needs in one query (open-in-view is off, so no lazy loading later). */
    @EntityGraph(attributePaths = ["supplier", "items", "items.product"])
    fun findWithDetailsById(id: Long): PurchaseOrder?

    /** List view: supplier for the name, items for the totals. */
    @EntityGraph(attributePaths = ["supplier", "items"])
    override fun findAll(sort: Sort): List<PurchaseOrder>

    @EntityGraph(attributePaths = ["supplier", "items"])
    fun findAllByStatus(status: PurchaseOrderStatus, sort: Sort): List<PurchaseOrder>

    /**
     * SELECT ... FOR UPDATE on the order row: goods receipts for the same PO are posted one after
     * another, so two parallel receipts cannot both "see" the same open quantity.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT po FROM PurchaseOrder po WHERE po.id = :id")
    fun findForUpdateById(id: Long): PurchaseOrder?

    @Query(value = "SELECT nextval('purchase_order_number_seq')", nativeQuery = true)
    fun nextPoNumber(): Long
}
