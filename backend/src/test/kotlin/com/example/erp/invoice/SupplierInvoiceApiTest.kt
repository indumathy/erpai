package com.example.erp.invoice

import com.example.erp.IntegrationTest
import com.jayway.jsonpath.JsonPath
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

/** PO 100 x BOLT @ 42.00 / 19 %, approved, 100 received. */
@IntegrationTest
class SupplierInvoiceApiTest(@Autowired private val mvc: MockMvc) {

    private var supplierId = 0L
    private var otherSupplierId = 0L
    private var poId = 0L
    private var boltLineId = 0L
    private var otherPoLineId = 0L

    private fun postJson(url: String, body: String) = mvc.post(url) {
        contentType = MediaType.APPLICATION_JSON
        content = body.trimIndent()
    }

    private fun idOf(location: String?) = location!!.substringAfterLast('/').toLong()

    private fun createPo(supplier: Long, productId: Long): String = postJson(
        "/api/purchase-orders",
        """{"supplierId":$supplier,"orderDate":"2026-10-01","items":[
            {"productId":$productId,"quantity":100,"unitPrice":42,"taxRate":19}]}""",
    ).andReturn().response.contentAsString

    @BeforeEach
    fun receivedPurchaseOrder() {
        supplierId = idOf(
            postJson("/api/suppliers", """{"supplierNumber":"S-INV","name":"Acme","currency":"EUR","paymentTermsDays":30}""")
                .andReturn().response.getHeader("Location"),
        )
        otherSupplierId = idOf(
            postJson("/api/suppliers", """{"supplierNumber":"S-OTHER","name":"Other","currency":"EUR","paymentTermsDays":30}""")
                .andReturn().response.getHeader("Location"),
        )
        val boltId = idOf(
            postJson("/api/products", """{"sku":"BOLT-INV","name":"Bolt","unitOfMeasure":"PIECE"}""")
                .andReturn().response.getHeader("Location"),
        )
        val warehouseId = idOf(
            postJson("/api/warehouses", """{"code":"WH-INV","name":"Invoice test"}""").andReturn().response.getHeader("Location"),
        )

        val po = createPo(supplierId, boltId)
        poId = JsonPath.read<Number>(po, "$.id").toLong()
        boltLineId = JsonPath.read<Number>(po, "$.items[0].id").toLong()
        mvc.post("/api/purchase-orders/$poId/approve")
        postJson(
            "/api/goods-receipts",
            """{"purchaseOrderId":$poId,"warehouseId":$warehouseId,"items":[{"purchaseOrderItemId":$boltLineId,"quantity":100}]}""",
        )

        otherPoLineId = JsonPath.read<Number>(createPo(supplierId, boltId), "$.items[0].id").toLong()
    }

    private fun invoice(
        number: String = "INV-1",
        quantity: String = "100",
        price: String = "42",
        tax: String = "19",
        currency: String = "EUR",
        purchaseOrderId: Long? = poId,
        lineRef: Long? = boltLineId,
        supplier: Long = supplierId,
        date: String = "2026-10-05",
    ) = postJson(
        "/api/invoices",
        """{"supplierId":$supplier,"invoiceNumber":"$number","invoiceDate":"$date","currency":"$currency",
            "purchaseOrderId":${purchaseOrderId ?: "null"},"items":[
            {"purchaseOrderItemId":${lineRef ?: "null"},"description":"Bolts","quantity":$quantity,"unitPrice":$price,"taxRate":$tax}]}""",
    )

    @Test
    fun create_returnsInvoiceWithLinesAndTotals() {
        invoice().andExpect {
            status { isCreated() }
            header { exists("Location") }
            jsonPath("$.invoiceNumber") { value("INV-1") }
            jsonPath("$.supplier.name") { value("Acme") }
            jsonPath("$.purchaseOrder.id") { value(poId) }
            jsonPath("$.items[0].lineNumber") { value(1) }
            jsonPath("$.items[0].purchaseOrderLineNumber") { value(1) }
            jsonPath("$.items[0].netAmount") { value(4200.0) }
            jsonPath("$.totals.total") { value(4998.0) }
        }
    }

    @Test
    fun invoiceWithoutPurchaseOrder_isAccepted() {
        invoice(purchaseOrderId = null, lineRef = null).andExpect {
            status { isCreated() }
            jsonPath("$.purchaseOrder") { doesNotExist() }
        }
    }

    @Test
    fun sameInvoiceNumberTwice_isAccepted() {
        invoice().andExpect { status { isCreated() } }
        invoice().andExpect { status { isCreated() } }
    }

    @Test
    fun getAndList_returnTheInvoice() {
        val id = idOf(invoice().andReturn().response.getHeader("Location"))
        mvc.get("/api/invoices/$id").andExpect {
            status { isOk() }
            jsonPath("$.items.length()") { value(1) }
        }
        mvc.get("/api/invoices").andExpect {
            jsonPath("$[?(@.id == $id)].invoiceNumber") { value("INV-1") }
            jsonPath("$[?(@.id == $id)].total") { value(4998.0) }
        }
    }

    @Test
    fun unknownInvoice_returns404() {
        mvc.get("/api/invoices/999999").andExpect { status { isNotFound() } }
    }

    @Test
    fun purchaseOrderOfAnotherSupplier_returns422() {
        invoice(supplier = otherSupplierId).andExpect { status { isUnprocessableContent() } }
    }

    @Test
    fun lineOfAnotherPurchaseOrder_returns422() {
        invoice(lineRef = otherPoLineId).andExpect { status { isUnprocessableContent() } }
    }

    @Test
    fun lineReferenceWithoutPurchaseOrder_returns422() {
        invoice(purchaseOrderId = null, lineRef = boltLineId).andExpect { status { isUnprocessableContent() } }
    }

    @Test
    fun futureInvoiceDate_returns422() {
        invoice(date = "2999-01-01").andExpect { status { isUnprocessableContent() } }
    }

    @Test
    fun unknownCurrencyCode_returns422() {
        invoice(currency = "XYZ").andExpect { status { isUnprocessableContent() } }
    }

    @Test
    fun invalidFields_return400() {
        invoice(quantity = "0").andExpect { status { isBadRequest() } }
        invoice(currency = "eur").andExpect { status { isBadRequest() } }
    }

    private fun createdId(result: org.springframework.test.web.servlet.ResultActionsDsl) =
        idOf(result.andReturn().response.getHeader("Location"))

    @Test
    fun matchingInvoice_isMatched() {
        val id = createdId(invoice())
        mvc.get("/api/invoices/$id/match").andExpect {
            status { isOk() }
            jsonPath("$.matched") { value(true) }
            jsonPath("$.exceptions.length()") { value(0) }
        }
        mvc.get("/api/invoices").andExpect { jsonPath("$[?(@.id == $id)].exceptionCount") { value(0) } }
    }

    @Test
    fun priceAndTaxDeviation_areReported() {
        val id = createdId(invoice(price = "45", tax = "7"))
        mvc.get("/api/invoices/$id/match").andExpect {
            jsonPath("$.matched") { value(false) }
            jsonPath("$.exceptions[0].code") { value("PRICE_MISMATCH") }
            jsonPath("$.exceptions[0].expected") { value("42") }
            jsonPath("$.exceptions[0].actual") { value("45") }
            jsonPath("$.exceptions[1].code") { value("TAX_MISMATCH") }
        }
        mvc.get("/api/invoices").andExpect { jsonPath("$[?(@.id == $id)].exceptionCount") { value(2) } }
    }

    @Test
    fun quantityRule_countsOnlyEarlierInvoices() {
        val first = createdId(invoice(number = "A", quantity = "60"))
        val second = createdId(invoice(number = "B", quantity = "40"))
        val third = createdId(invoice(number = "C", quantity = "1"))

        mvc.get("/api/invoices/$first/match").andExpect { jsonPath("$.matched") { value(true) } }
        mvc.get("/api/invoices/$second/match").andExpect { jsonPath("$.matched") { value(true) } }
        mvc.get("/api/invoices/$third/match").andExpect {
            jsonPath("$.exceptions[0].code") { value("QUANTITY_MISMATCH") }
            jsonPath("$.exceptions[0].expected") { value("0") }
        }
    }

    @Test
    fun duplicateInvoiceNumber_isDetectedCaseAndSpaceInsensitive() {
        val first = createdId(invoice(number = "SE-RE-1", quantity = "50"))
        val second = createdId(invoice(number = " se-re-1 ", quantity = "50"))
        for (id in listOf(first, second)) {
            mvc.get("/api/invoices/$id/match").andExpect {
                jsonPath("$.exceptions[0].code") { value("DUPLICATE_INVOICE") }
                jsonPath("$.exceptions.length()") { value(1) }
            }
        }
    }

    @Test
    fun invoiceWithoutPo_reportsMissingPo() {
        val id = createdId(invoice(purchaseOrderId = null, lineRef = null))
        mvc.get("/api/invoices/$id/match").andExpect {
            jsonPath("$.exceptions[0].code") { value("MISSING_PO") }
        }
    }

    @Test
    fun matchOfUnknownInvoice_returns404() {
        mvc.get("/api/invoices/999999/match").andExpect { status { isNotFound() } }
    }
}
