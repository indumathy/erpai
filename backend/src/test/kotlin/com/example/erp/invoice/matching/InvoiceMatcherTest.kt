package com.example.erp.invoice.matching

import com.example.erp.invoice.matching.MatchExceptionCode.CURRENCY_MISMATCH
import com.example.erp.invoice.matching.MatchExceptionCode.DUPLICATE_INVOICE
import com.example.erp.invoice.matching.MatchExceptionCode.MISSING_PO
import com.example.erp.invoice.matching.MatchExceptionCode.PRICE_MISMATCH
import com.example.erp.invoice.matching.MatchExceptionCode.QUANTITY_MISMATCH
import com.example.erp.invoice.matching.MatchExceptionCode.TAX_MISMATCH
import com.example.erp.invoice.matching.MatchExceptionCode.UNMATCHED_LINE
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class InvoiceMatcherTest {

    // ---- builders: a PO line 100 x 42.00 @ 19 %, fully received, nothing invoiced before ---------

    private fun poLine(
        id: Long = 10,
        lineNumber: Int = 1,
        quantity: String = "100",
        unitPrice: String = "42.00",
        taxRate: String = "19",
        received: String = "100",
        alreadyInvoiced: String = "0",
    ) = PurchaseOrderLineSnapshot(
        id, lineNumber, "SKU-$id", BigDecimal(quantity), BigDecimal(unitPrice), BigDecimal(taxRate),
        BigDecimal(received), BigDecimal(alreadyInvoiced),
    )

    private fun po(vararg lines: PurchaseOrderLineSnapshot = arrayOf(poLine()), currency: String = "EUR") =
        PurchaseOrderSnapshot(1, "PO-000001", currency, lines.toList())

    private fun invLine(
        lineNumber: Int = 1,
        poItemId: Long? = 10,
        quantity: String = "100",
        unitPrice: String = "42.00",
        taxRate: String = "19",
    ) = InvoiceLineSnapshot(
        lineNumber, poItemId, "line $lineNumber", BigDecimal(quantity), BigDecimal(unitPrice), BigDecimal(taxRate),
    )

    private fun input(
        vararg lines: InvoiceLineSnapshot = arrayOf(invLine()),
        currency: String = "EUR",
        purchaseOrder: PurchaseOrderSnapshot? = po(),
        duplicates: List<Long> = emptyList(),
    ) = MatchInput(InvoiceSnapshot(99, "INV-1", currency, lines.toList()), purchaseOrder, duplicates)

    private fun codes(input: MatchInput) = InvoiceMatcher.match(input).map { it.code }

    // ---- happy path ----------------------------------------------------------------------------

    @Test
    fun perfectInvoice_hasNoExceptions() {
        assertThat(InvoiceMatcher.match(input())).isEmpty()
    }

    // ---- DUPLICATE_INVOICE ---------------------------------------------------------------------

    @Test
    fun duplicate_isReportedWithTheOtherInvoiceIds() {
        val result = InvoiceMatcher.match(input(duplicates = listOf(7, 8)))
        assertThat(result.map { it.code }).containsExactly(DUPLICATE_INVOICE)
        assertThat(result[0].lineNumber).isNull()
        assertThat(result[0].actual).isEqualTo("7, 8")
    }

    // ---- MISSING_PO ----------------------------------------------------------------------------

    @Test
    fun missingPo_isTheOnlyExceptionEvenIfLinesWouldFail() {
        val badLine = invLine(poItemId = null, unitPrice = "999", taxRate = "7")
        assertThat(codes(input(badLine, purchaseOrder = null))).containsExactly(MISSING_PO)
    }

    @Test
    fun missingPo_isStillCombinedWithDuplicate() {
        assertThat(codes(input(purchaseOrder = null, duplicates = listOf(3))))
            .containsExactly(DUPLICATE_INVOICE, MISSING_PO)
    }

    // ---- CURRENCY_MISMATCH ---------------------------------------------------------------------

    @Test
    fun currencyMismatch_reportsBothCurrencies() {
        val result = InvoiceMatcher.match(input(purchaseOrder = po(currency = "USD")))
        assertThat(result.map { it.code }).containsExactly(CURRENCY_MISMATCH)
        assertThat(result[0].expected).isEqualTo("USD")
        assertThat(result[0].actual).isEqualTo("EUR")
    }

    @Test
    fun currencyMismatch_skipsPriceRule() {
        val pricey = invLine(unitPrice = "50")
        assertThat(codes(input(pricey, purchaseOrder = po(currency = "USD")))).containsExactly(CURRENCY_MISMATCH)
    }

    // ---- UNMATCHED_LINE ------------------------------------------------------------------------

    @Test
    fun lineWithoutPoReference_isUnmatched() {
        val freight = invLine(lineNumber = 2, poItemId = null, quantity = "1", unitPrice = "85")
        val result = InvoiceMatcher.match(input(invLine(), freight))
        assertThat(result.map { it.code }).containsExactly(UNMATCHED_LINE)
        assertThat(result[0].lineNumber).isEqualTo(2)
    }

    @Test
    fun lineReferencingUnknownPoLine_isUnmatched() {
        assertThat(codes(input(invLine(poItemId = 12345)))).containsExactly(UNMATCHED_LINE)
    }

    // ---- PRICE_MISMATCH ------------------------------------------------------------------------

    @Test
    fun price_exactlyAtTolerance_passes() {
        val po = po(poLine(unitPrice = "100"))
        assertThat(codes(input(invLine(unitPrice = "100.50"), purchaseOrder = po))).isEmpty()
        assertThat(codes(input(invLine(unitPrice = "99.50"), purchaseOrder = po))).isEmpty()
    }

    @Test
    fun price_aboveTolerance_failsWithExpectedAndActual() {
        val po = po(poLine(unitPrice = "100"))
        val result = InvoiceMatcher.match(input(invLine(unitPrice = "100.51"), purchaseOrder = po))
        assertThat(result.map { it.code }).containsExactly(PRICE_MISMATCH)
        assertThat(result[0].lineNumber).isEqualTo(1)
        assertThat(result[0].expected).isEqualTo("100")
        assertThat(result[0].actual).isEqualTo("100.51")
    }

    @Test
    fun price_belowTolerance_alsoFails() {
        val po = po(poLine(unitPrice = "100"))
        assertThat(codes(input(invLine(unitPrice = "99.49"), purchaseOrder = po))).containsExactly(PRICE_MISMATCH)
    }

    @Test
    fun price_zeroPoPrice_onlyZeroMatches() {
        val freeGoods = po(poLine(unitPrice = "0"))
        assertThat(codes(input(invLine(unitPrice = "0"), purchaseOrder = freeGoods))).isEmpty()
        assertThat(codes(input(invLine(unitPrice = "0.01"), purchaseOrder = freeGoods))).containsExactly(PRICE_MISMATCH)
    }

    // ---- QUANTITY_MISMATCH ---------------------------------------------------------------------

    @Test
    fun quantity_billingExactlyWhatArrived_passes() {
        val po = po(poLine(received = "98"))
        assertThat(codes(input(invLine(quantity = "98"), purchaseOrder = po))).isEmpty()
    }

    @Test
    fun quantity_billingMoreThanArrived_failsWithInvoiceableQuantity() {
        val po = po(poLine(received = "98"))
        val result = InvoiceMatcher.match(input(invLine(quantity = "100"), purchaseOrder = po))
        assertThat(result.map { it.code }).containsExactly(QUANTITY_MISMATCH)
        assertThat(result[0].expected).isEqualTo("98")
        assertThat(result[0].actual).isEqualTo("100")
    }

    @Test
    fun quantity_countsEarlierInvoices() {
        val po = po(poLine(received = "100", alreadyInvoiced = "60"))
        assertThat(codes(input(invLine(quantity = "40"), purchaseOrder = po))).isEmpty()
        val result = InvoiceMatcher.match(input(invLine(quantity = "41"), purchaseOrder = po))
        assertThat(result.map { it.code }).containsExactly(QUANTITY_MISMATCH)
        assertThat(result[0].expected).isEqualTo("40")
    }

    @Test
    fun quantity_nothingReceivedYet_fails() {
        val po = po(poLine(received = "0"))
        assertThat(codes(input(invLine(quantity = "1"), purchaseOrder = po))).containsExactly(QUANTITY_MISMATCH)
    }

    // ---- TAX_MISMATCH --------------------------------------------------------------------------

    @Test
    fun tax_sameRateDifferentScale_passes() {
        assertThat(codes(input(invLine(taxRate = "19.00")))).isEmpty()
    }

    @Test
    fun tax_differentRate_fails() {
        val result = InvoiceMatcher.match(input(invLine(taxRate = "7")))
        assertThat(result.map { it.code }).containsExactly(TAX_MISMATCH)
        assertThat(result[0].expected).isEqualTo("19")
        assertThat(result[0].actual).isEqualTo("7")
    }

    // ---- combinations and ordering -------------------------------------------------------------

    @Test
    fun exceptions_areOrderedHeaderFirstThenByLineThenByCode() {
        val po = po(poLine(id = 10, lineNumber = 1), poLine(id = 11, lineNumber = 2, received = "5"))
        val line1 = invLine(lineNumber = 1, poItemId = 10, unitPrice = "50", taxRate = "7")
        val line2 = invLine(lineNumber = 2, poItemId = 11, quantity = "6")
        val freight = invLine(lineNumber = 3, poItemId = null)

        val result = InvoiceMatcher.match(input(freight, line2, line1, purchaseOrder = po, duplicates = listOf(5)))

        assertThat(result.map { it.code to it.lineNumber }).containsExactly(
            DUPLICATE_INVOICE to null,
            PRICE_MISMATCH to 1,
            TAX_MISMATCH to 1,
            QUANTITY_MISMATCH to 2,
            UNMATCHED_LINE to 3,
        )
    }

    @Test
    fun everyExceptionHasAMessage() {
        val po = po(poLine(received = "0"), currency = "USD")
        val result = InvoiceMatcher.match(input(invLine(taxRate = "7"), invLine(lineNumber = 2, poItemId = null), purchaseOrder = po, duplicates = listOf(1)))
        assertThat(result).isNotEmpty.allSatisfy { assertThat(it.message).isNotBlank() }
    }
}
