package com.example.erp.purchaseorder

import com.example.erp.product.Product
import com.example.erp.product.UnitOfMeasure
import com.example.erp.shared.error.BusinessRuleViolationException
import com.example.erp.supplier.Supplier
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

class PurchaseOrderTest {

    private val supplier = Supplier("S-1000", "Acme Industrial GmbH", null, "EUR", 30)
    private val bolt = Product("BOLT-M8", "Hex bolt M8", null, UnitOfMeasure.PIECE)
    private val nut = Product("NUT-M8", "Hex nut M8", null, UnitOfMeasure.PIECE)

    private fun line(
        product: Product = bolt,
        quantity: String = "100",
        unitPrice: String = "42",
        taxRate: String = "19",
    ) = PurchaseOrderLine(product, null, BigDecimal(quantity), BigDecimal(unitPrice), BigDecimal(taxRate))

    private fun po(vararg lines: PurchaseOrderLine = arrayOf(line()), supplier: Supplier = this.supplier) =
        PurchaseOrder("PO-000001", supplier, LocalDate.of(2026, 10, 6), "EUR", lines.toList())

    @Test
    fun newPurchaseOrder_isDraftWithNumberedLinesAndTotals() {
        val po = po(line(bolt, "100", "42"), line(nut, "10", "0.5", "7"))

        assertThat(po.status).isEqualTo(PurchaseOrderStatus.DRAFT)
        assertThat(po.lines.map { it.lineNumber }).containsExactly(1, 2)
        assertThat(po.lines[0].description).isEqualTo("Hex bolt M8") // defaults to product name
        assertThat(po.lines[0].unitPrice).isEqualTo(BigDecimal("42.0000")) // normalised scale

        val totals = po.totals()
        assertThat(totals.subtotal).isEqualTo(BigDecimal("4205.00"))
        assertThat(totals.taxAmount).isEqualTo(BigDecimal("798.35")) // 798.00 + 0.35
        assertThat(totals.total).isEqualTo(BigDecimal("5003.35"))
    }

    @Test
    fun noItems_isRejected() {
        assertThatThrownBy { po(*emptyArray()) }
            .isInstanceOf(BusinessRuleViolationException::class.java)
            .hasMessageContaining("at least one item")
    }

    @Test
    fun nonPositiveQuantity_isRejected() {
        assertThatThrownBy { po(line(quantity = "0")) }.isInstanceOf(BusinessRuleViolationException::class.java)
        assertThatThrownBy { po(line(quantity = "-1")) }.isInstanceOf(BusinessRuleViolationException::class.java)
    }

    @Test
    fun negativePrice_isRejected_butZeroPriceIsAllowed() {
        assertThatThrownBy { po(line(unitPrice = "-0.01")) }.isInstanceOf(BusinessRuleViolationException::class.java)
        assertThat(po(line(unitPrice = "0")).totals().total).isEqualByComparingTo("0") // free sample
    }

    @Test
    fun sameProductTwice_isRejected() {
        assertThatThrownBy { po(line(bolt), line(bolt)) }
            .isInstanceOf(BusinessRuleViolationException::class.java)
            .hasMessageContaining("only once")
    }

    @Test
    fun inactiveSupplier_isRejected() {
        val inactive = Supplier("S-2000", "Gone Ltd", null, "EUR", 30).apply { deactivate() }
        assertThatThrownBy { po(supplier = inactive) }
            .isInstanceOf(BusinessRuleViolationException::class.java)
            .hasMessageContaining("inactive")
    }

    @Test
    fun inactiveProduct_isRejected() {
        val discontinued = Product("OLD-1", "Old part", null, UnitOfMeasure.PIECE).apply { deactivate() }
        assertThatThrownBy { po(line(discontinued)) }.isInstanceOf(BusinessRuleViolationException::class.java)
    }

    @Test
    fun approve_freezesTheOrder() {
        val po = po()
        po.approve()

        assertThat(po.status).isEqualTo(PurchaseOrderStatus.APPROVED)
        assertThatThrownBy { po.updateDraft(LocalDate.now(), "EUR", listOf(line())) }
            .isInstanceOf(BusinessRuleViolationException::class.java)
            .hasMessageContaining("Only draft")
        assertThatThrownBy { po.approve() }.isInstanceOf(BusinessRuleViolationException::class.java)
    }

    @Test
    fun approve_rejectsSupplierDeactivatedAfterDrafting() {
        val po = po()
        supplier.deactivate()
        assertThatThrownBy { po.approve() }.isInstanceOf(BusinessRuleViolationException::class.java)
    }

    @Test
    fun updateDraft_replacesLines() {
        val po = po(line(bolt))
        po.updateDraft(LocalDate.of(2026, 10, 7), "USD", listOf(line(nut, "5", "1"), line(bolt, "1", "1")))

        assertThat(po.currency).isEqualTo("USD")
        assertThat(po.lines.map { it.product.sku }).containsExactly("NUT-M8", "BOLT-M8")
        assertThat(po.lines.map { it.lineNumber }).containsExactly(1, 2)
    }

    // ---- receiving --------------------------------------------------------------------------

    private fun approvedPo() = po(line(bolt, "100", "42"), line(nut, "10", "1")).apply { approve() }

    private fun PurchaseOrder.receipt(lineIndex: Int, quantity: String) =
        ReceivedQuantity(lines[lineIndex], BigDecimal(quantity))

    @Test
    fun partialReceipt_setsPartiallyReceived_andTracksOpenQuantity() {
        val po = approvedPo()
        po.receive(listOf(po.receipt(0, "98")))

        assertThat(po.status).isEqualTo(PurchaseOrderStatus.PARTIALLY_RECEIVED)
        assertThat(po.lines[0].receivedQuantity).isEqualByComparingTo("98")
        assertThat(po.lines[0].openQuantity).isEqualByComparingTo("2")
    }

    @Test
    fun receivingEverything_inSeveralReceipts_setsReceived() {
        val po = approvedPo()
        po.receive(listOf(po.receipt(0, "60"), po.receipt(1, "10")))
        po.receive(listOf(po.receipt(0, "40")))

        assertThat(po.status).isEqualTo(PurchaseOrderStatus.RECEIVED)
        assertThat(po.lines).allMatch { it.isFullyReceived }
    }

    @Test
    fun overReceipt_isRejected_andChangesNothing() {
        val po = approvedPo()
        po.receive(listOf(po.receipt(0, "98")))

        assertThatThrownBy { po.receive(listOf(po.receipt(1, "1"), po.receipt(0, "3"))) }
            .isInstanceOf(BusinessRuleViolationException::class.java)
            .hasMessageContaining("exceeds the open quantity 2")
        // validation happens before any line is booked
        assertThat(po.lines[1].receivedQuantity).isEqualByComparingTo("0")
    }

    @Test
    fun receivingADraft_isRejected() {
        val draft = po()
        assertThatThrownBy { draft.receive(listOf(draft.receipt(0, "1"))) }
            .isInstanceOf(BusinessRuleViolationException::class.java)
            .hasMessageContaining("approved")
    }

    @Test
    fun receivingALineOfAnotherOrder_isRejected() {
        val po = approvedPo()
        val other = approvedPo()
        assertThatThrownBy { po.receive(listOf(other.receipt(0, "1"))) }
            .isInstanceOf(BusinessRuleViolationException::class.java)
            .hasMessageContaining("does not belong")
    }

    @Test
    fun close_isAllowedAfterPartialReceipt_butNotBefore() {
        val po = approvedPo()
        assertThatThrownBy { po.close() }.isInstanceOf(BusinessRuleViolationException::class.java)

        po.receive(listOf(po.receipt(0, "50")))
        po.close()
        assertThat(po.status).isEqualTo(PurchaseOrderStatus.CLOSED)
        assertThatThrownBy { po.receive(listOf(po.receipt(0, "1"))) }.isInstanceOf(BusinessRuleViolationException::class.java)
    }

    @Test
    fun cancel_isRejectedOnceGoodsArrived() {
        val po = approvedPo()
        po.receive(listOf(po.receipt(0, "1")))
        assertThatThrownBy { po.cancel() }.isInstanceOf(BusinessRuleViolationException::class.java)
    }

    @Test
    fun cancel_isAllowedFromDraftAndApproved_onlyOnce() {
        val po = po()
        po.approve()
        po.cancel()
        assertThat(po.status).isEqualTo(PurchaseOrderStatus.CANCELLED)
        assertThatThrownBy { po.cancel() }.isInstanceOf(BusinessRuleViolationException::class.java)
    }
}
