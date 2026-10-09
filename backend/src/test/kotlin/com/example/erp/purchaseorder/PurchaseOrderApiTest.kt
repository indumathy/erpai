package com.example.erp.purchaseorder

import com.example.erp.IntegrationTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put

@IntegrationTest
class PurchaseOrderApiTest(@Autowired private val mvc: MockMvc) {

    private var supplierId = 0L
    private var boltId = 0L
    private var nutId = 0L

    private fun idOf(location: String?) = location!!.substringAfterLast('/').toLong()

    @BeforeEach
    fun masterData() {
        supplierId = idOf(
            mvc.post("/api/suppliers") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"supplierNumber":"S-1000","name":"Acme","currency":"EUR","paymentTermsDays":30}"""
            }.andReturn().response.getHeader("Location"),
        )
        boltId = createProduct("BOLT-M8")
        nutId = createProduct("NUT-M8")
    }

    private fun createProduct(sku: String) = idOf(
        mvc.post("/api/products") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"sku":"$sku","name":"$sku","unitOfMeasure":"PIECE"}"""
        }.andReturn().response.getHeader("Location"),
    )

    private fun createPo(items: String) = mvc.post("/api/purchase-orders") {
        contentType = MediaType.APPLICATION_JSON
        content = """{"supplierId":$supplierId,"orderDate":"2026-10-06","items":[$items]}"""
    }

    private fun item(productId: Long, quantity: String, unitPrice: String, taxRate: String = "19") =
        """{"productId":$productId,"quantity":$quantity,"unitPrice":$unitPrice,"taxRate":$taxRate}"""

    @Test
    fun create_calculatesTotalsOnTheServer() {
        createPo(item(boltId, "100", "42") + "," + item(nutId, "10", "0.5", "7")).andExpect {
            status { isCreated() }
            jsonPath("$.poNumber") { value(org.hamcrest.Matchers.startsWith("PO-")) }
            jsonPath("$.status") { value("DRAFT") }
            jsonPath("$.currency") { value("EUR") } // defaulted from the supplier
            jsonPath("$.items.length()") { value(2) }
            jsonPath("$.items[0].netAmount") { value(4200.00) }
            jsonPath("$.totals.subtotal") { value(4205.00) }
            jsonPath("$.totals.taxAmount") { value(798.35) }
            jsonPath("$.totals.total") { value(5003.35) }
        }
    }

    @Test
    fun clientSuppliedTotals_areIgnored() {
        mvc.post("/api/purchase-orders") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"supplierId":$supplierId,"total":1.00,"items":[${item(boltId, "1", "10")}]}"""
        }.andExpect {
            status { isCreated() }
            jsonPath("$.totals.total") { value(11.90) }
        }
    }

    @Test
    fun emptyItems_returns400() {
        createPo("").andExpect { status { isBadRequest() } }
    }

    @Test
    fun negativeQuantity_returns400WithIndexedFieldName() {
        createPo(item(boltId, "-1", "42")).andExpect {
            status { isBadRequest() }
            jsonPath("$.errors[0].field") { value("items[0].quantity") }
        }
    }

    @Test
    fun duplicateProduct_returns422() {
        createPo(item(boltId, "1", "1") + "," + item(boltId, "2", "1")).andExpect { status { isUnprocessableContent() } }
    }

    @Test
    fun inactiveSupplier_returns422() {
        mvc.post("/api/suppliers/$supplierId/deactivate")
        createPo(item(boltId, "1", "1")).andExpect { status { isUnprocessableContent() } }
    }

    @Test
    fun approveFlow_andEditingAfterApproval_isRejected() {
        val id = idOf(createPo(item(boltId, "100", "42")).andReturn().response.getHeader("Location"))

        mvc.put("/api/purchase-orders/$id") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"orderDate":"2026-10-07","currency":"EUR","items":[${item(nutId, "5", "2")}]}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.items[0].sku") { value("NUT-M8") }
        }

        mvc.post("/api/purchase-orders/$id/approve").andExpect { jsonPath("$.status") { value("APPROVED") } }

        mvc.put("/api/purchase-orders/$id") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"orderDate":"2026-10-07","currency":"EUR","items":[${item(boltId, "1", "1")}]}"""
        }.andExpect { status { isUnprocessableContent() } }

        mvc.get("/api/purchase-orders?status=APPROVED").andExpect { jsonPath("$.length()") { value(1) } }
    }

    @Test
    fun cancel() {
        val id = idOf(createPo(item(boltId, "1", "1")).andReturn().response.getHeader("Location"))
        mvc.post("/api/purchase-orders/$id/cancel").andExpect { jsonPath("$.status") { value("CANCELLED") } }
        mvc.post("/api/purchase-orders/$id/approve").andExpect { status { isUnprocessableContent() } }
    }
}
