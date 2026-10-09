package com.example.erp.goodsreceipt

import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface GoodsReceiptRepository : JpaRepository<GoodsReceipt, Long> {

    @EntityGraph(
        attributePaths = [
            "purchaseOrder", "purchaseOrder.supplier", "warehouse",
            "items", "items.purchaseOrderItem", "items.purchaseOrderItem.product",
        ],
    )
    fun findWithDetailsById(id: Long): GoodsReceipt?

    @EntityGraph(attributePaths = ["purchaseOrder", "purchaseOrder.supplier", "warehouse", "items"])
    fun findAllByOrderByIdDesc(): List<GoodsReceipt>

    @EntityGraph(attributePaths = ["purchaseOrder", "purchaseOrder.supplier", "warehouse", "items"])
    fun findAllByPurchaseOrderIdOrderByIdDesc(purchaseOrderId: Long): List<GoodsReceipt>

    @Query(value = "SELECT nextval('goods_receipt_number_seq')", nativeQuery = true)
    fun nextGrNumber(): Long
}
