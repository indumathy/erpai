package com.example.erp.goodsreceipt

import com.example.erp.IntegrationTest
import com.jayway.jsonpath.JsonPath
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

/** End-to-end: PO -> approve -> goods receipts -> PO status + stock levels + stock ledger. */
@IntegrationTest
class GoodsReceiptApiTest(@Autowired private val mvc: MockMvc) {

    private var poId = 0L
    private var boltLineId = 0L
    private var nutLineId = 0L
    private var boltProductId = 0L
    private var warehouseId = 0L

    private fun json(body: String) = body.trimIndent()

    private fun postJson(url: String, body: String) = mvc.post(url) {
        contentType = MediaType.APPLICATION_JSON
        content = json(body)
    }

    private fun idOf(location: String?) = location!!.substringAfterLast('/').toLong()

    @BeforeEach
    fun approvedPurchaseOrder() {
        val supplierId = idOf(
            postJson("/api/suppliers", """{"supplierNumber":"S-1","name":"Acme","currency":"EUR","paymentTermsDays":30}""")
                .andReturn().response.getHeader("Location"),
        )
        boltProductId = idOf(
            postJson("/api/products", """{"sku":"BOLT","name":"Bolt","unitOfMeasure":"PIECE"}""")
                .andReturn().response.getHeader("Location"),
        )
        val nutId = idOf(
            postJson("/api/products", """{"sku":"NUT","name":"Nut","unitOfMeasure":"PIECE"}""")
                .andReturn().response.getHeader("Location"),
        )
        warehouseId = idOf(
            postJson("/api/warehouses", """{"code":"WH-T","name":"Test warehouse"}""").andReturn().response.getHeader("Location"),
        )

        val po = postJson(
            "/api/purchase-orders",
            """{"supplierId":$supplierId,"orderDate":"2026-10-06","items":[
                {"productId":$boltProductId,"quantity":100,"unitPrice":42,"taxRate":19},
                {"productId":$nutId,"quantity":10,"unitPrice":1,"taxRate":19}]}""",
        ).andReturn().response.contentAsString
        poId = JsonPath.read<Number>(po, "$.id").toLong()
        boltLineId = JsonPath.read<Number>(po, "$.items[0].id").toLong()
        nutLineId = JsonPath.read<Number>(po, "$.items[1].id").toLong()

        mvc.post("/api/purchase-orders/$poId/approve")
    }

    private fun receive(vararg lines: Pair<Long, String>) = postJson(
        "/api/goods-receipts",
        """{"purchaseOrderId":$poId,"warehouseId":$warehouseId,"deliveryNoteNumber":"LS-4711","items":[
           ${lines.joinToString(",") { (id, qty) -> """{"purchaseOrderItemId":$id,"quantity":$qty}""" }}]}""",
    )

    @Test
    fun partialReceipt_updatesPoStatusStockAndLedger() {
        receive(boltLineId to "98").andExpect {
            status { isCreated() }
            jsonPath("$.grNumber") { value(org.hamcrest.Matchers.startsWith("GR-")) }
            jsonPath("$.deliveryNoteNumber") { value("LS-4711") }
            jsonPath("$.items[0].quantityReceived") { value(98) }
        }

        mvc.get("/api/purchase-orders/$poId").andExpect {
            jsonPath("$.status") { value("PARTIALLY_RECEIVED") }
            jsonPath("$.items[0].receivedQuantity") { value(98) }
            jsonPath("$.items[0].openQuantity") { value(2) }
        }

        mvc.get("/api/inventory/stock?warehouseId=$warehouseId").andExpect {
            jsonPath("$.length()") { value(1) }
            jsonPath("$[0].product.sku") { value("BOLT") }
            jsonPath("$[0].quantityOnHand") { value(98) }
        }

        mvc.get("/api/inventory/movements?productId=$boltProductId").andExpect {
            jsonPath("$[0].movementType") { value("GOODS_RECEIPT") }
            jsonPath("$[0].referenceNumber") { value(org.hamcrest.Matchers.startsWith("GR-")) }
        }
    }

    @Test
    fun secondReceipt_completesThePurchaseOrder() {
        receive(boltLineId to "60", nutLineId to "10").andExpect { status { isCreated() } }
        receive(boltLineId to "40").andExpect { status { isCreated() } }

        mvc.get("/api/purchase-orders/$poId").andExpect { jsonPath("$.status") { value("RECEIVED") } }
        mvc.get("/api/goods-receipts?purchaseOrderId=$poId").andExpect { jsonPath("$.length()") { value(2) } }
    }

    @Test
    fun overReceipt_returns422_andPostsNothing() {
        receive(boltLineId to "101").andExpect {
            status { isUnprocessableContent() }
            jsonPath("$.detail") { value(org.hamcrest.Matchers.containsString("exceeds the open quantity")) }
        }

        mvc.get("/api/purchase-orders/$poId").andExpect { jsonPath("$.status") { value("APPROVED") } }
        mvc.get("/api/inventory/stock?warehouseId=$warehouseId").andExpect { jsonPath("$.length()") { value(0) } }
    }

    @Test
    fun negativeAdjustmentBeyondStock_returns422() {
        receive(boltLineId to "5")
        postJson(
            "/api/inventory/adjustments",
            """{"warehouseId":$warehouseId,"productId":$boltProductId,"quantity":-6,"reason":"stock count"}""",
        ).andExpect { status { isUnprocessableContent() } }

        postJson(
            "/api/inventory/adjustments",
            """{"warehouseId":$warehouseId,"productId":$boltProductId,"quantity":-2,"reason":"damaged"}""",
        ).andExpect {
            status { isCreated() }
            jsonPath("$.movementType") { value("ADJUSTMENT") }
        }
        mvc.get("/api/inventory/stock?warehouseId=$warehouseId").andExpect { jsonPath("$[0].quantityOnHand") { value(3) } }
    }

    @Test
    fun seededMainWarehouse_exists() {
        mvc.get("/api/warehouses").andExpect { jsonPath("$[?(@.code == 'MAIN')]") { exists() } }
    }
}
