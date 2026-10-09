package com.example.erp.goodsreceipt

import com.example.erp.inventory.InventoryService
import com.example.erp.inventory.MovementType
import com.example.erp.inventory.StockReference
import com.example.erp.purchaseorder.PurchaseOrderService
import com.example.erp.purchaseorder.ReceivedQuantity
import com.example.erp.shared.error.BusinessRuleViolationException
import com.example.erp.shared.error.NotFoundException
import com.example.erp.shared.money.MoneyRounding
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

/**
 * Posting a goods receipt is one atomic business transaction:
 *   lock PO -> PO validates & books received quantities (status update) -> save GR -> stock in.
 * If any step fails (e.g. over-receipt, inactive warehouse) nothing is persisted.
 *
 * Unlike the master-data services, this one returns response DTOs: the GR spans several
 * aggregates (PO, supplier, warehouse, products), and mapping inside the transaction keeps
 * lazy associations loadable while open-in-view is disabled.
 */
@Service
@Transactional
class GoodsReceiptService(
    private val goodsReceipts: GoodsReceiptRepository,
    private val purchaseOrderService: PurchaseOrderService,
    private val inventoryService: InventoryService,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun post(request: CreateGoodsReceiptRequest): GoodsReceiptResponse {
        val po = purchaseOrderService.getForUpdate(request.purchaseOrderId)
        val warehouse = inventoryService.getWarehouse(request.warehouseId)

        val receipts = request.items.map { line ->
            val poItem = po.lines.find { it.id == line.purchaseOrderItemId }
                ?: throw BusinessRuleViolationException(
                    "Purchase order item ${line.purchaseOrderItemId} does not belong to ${po.poNumber}",
                )
            ReceivedQuantity(poItem, MoneyRounding.quantity(line.quantity))
        }

        po.receive(receipts)

        val gr = goodsReceipts.save(
            GoodsReceipt(
                grNumber = "GR-%06d".format(goodsReceipts.nextGrNumber()),
                purchaseOrder = po,
                warehouse = warehouse,
                receiptDate = request.receiptDate ?: LocalDate.now(),
                deliveryNoteNumber = request.deliveryNoteNumber,
                receipts = receipts,
            ),
        )

        val reference = StockReference("GOODS_RECEIPT", gr.requireId(), gr.grNumber)
        receipts.forEach {
            inventoryService.post(warehouse, it.item.product, it.quantity, MovementType.GOODS_RECEIPT, reference)
        }

        log.atInfo()
            .addKeyValue("goodsReceiptId", gr.id)
            .addKeyValue("purchaseOrderId", po.id)
            .addKeyValue("supplierId", po.supplier.id)
            .addKeyValue("purchaseOrderStatus", po.status)
            .log("Goods receipt posted")
        return gr.toResponse()
    }

    @Transactional(readOnly = true)
    fun get(id: Long): GoodsReceiptResponse =
        (goodsReceipts.findWithDetailsById(id) ?: throw NotFoundException("Goods receipt $id not found")).toResponse()

    @Transactional(readOnly = true)
    fun list(purchaseOrderId: Long?): List<GoodsReceiptSummaryResponse> {
        val receipts = if (purchaseOrderId == null) goodsReceipts.findAllByOrderByIdDesc()
        else goodsReceipts.findAllByPurchaseOrderIdOrderByIdDesc(purchaseOrderId)
        return receipts.map { it.toSummaryResponse() }
    }
}
