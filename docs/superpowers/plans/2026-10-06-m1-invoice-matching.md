# M1 Supplier Invoices & Three-Way Matching — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add supplier invoices and a pure, deterministic three-way matching function (invoice ↔ PO ↔ goods receipts) that later AI milestones use as ground truth.

**Architecture:** New backend package `com.example.erp.invoice` (entities, repository, service, controller, DTOs) plus `com.example.erp.invoice.matching` (pure `InvoiceMatcher` with snapshot input types — no Spring/JPA). The service builds a `MatchInput` snapshot from entities and calls the matcher on demand; results are not stored. Frontend gets a read-only `features/invoices` folder. The demo seed script gains invoices that trigger every rule.

**Tech Stack:** Kotlin 2.2, Spring Boot 4, Spring Data JPA, Flyway, PostgreSQL, JUnit 5 + AssertJ, Testcontainers/MockMvc; React 19 + TypeScript + Mantine 9 + TanStack Query; Node 24 for the seed script.

**Spec:** `docs/superpowers/specs/2026-10-06-m1-invoice-matching-design.md`

## Global Constraints

- Lean ERP: build only what the spec lists; no status lifecycle, no stored match results, no invoice form.
- `InvoiceMatcher` must stay pure: no Spring, no JPA, no `LocalDate.now()`, no I/O.
- Price tolerance: `PRICE_TOLERANCE_PERCENT = 0.5` (exactly 0.5 % passes, anything above fails). Quantity: no tolerance. Tax: exact.
- `alreadyInvoicedQuantity` = sum over **earlier** invoices (lower id) for the same PO line.
- Exception order: header exceptions (DUPLICATE_INVOICE, MISSING_PO, CURRENCY_MISMATCH), then line exceptions by line number, within a line in enum order (UNMATCHED_LINE, PRICE_MISMATCH, QUANTITY_MISMATCH, TAX_MISMATCH).
- `invoice_number` is **not** unique in the database.
- Errors follow the existing convention: unknown supplier/PO/invoice → 404 (`NotFoundException`), rule violations → 422 (`requireRule`), bean validation → 400.
- All numeric normalisation goes through `MoneyRounding`; totals through `DocumentTotals`.
- Expected/actual values in `MatchException` are rendered with `stripTrailingZeros().toPlainString()` (`42.0000` → `"42"`), so they do not depend on DB scale.

## Review Focus

1. A duplicate invoice number with different letter case or surrounding spaces (`" se-re-1 "` vs `"SE-RE-1"`) must still be detected — pinned in Task 4 (`duplicateInvoiceNumber_isDetectedCaseAndSpaceInsensitive`).
2. Partial invoicing: a first invoice for 60 of 100 received units and a second for 40 must both match; a third for 1 more must be flagged — pinned in Task 4 (`quantityRule_countsOnlyEarlierInvoices`).
3. An invoice line referencing a PO line of a *different* PO must be rejected at creation (422), not silently stored — pinned in Task 3 (`lineOfAnotherPurchaseOrder_returns422`).
4. A PO unit price of 0 (free goods) must not cause a division by zero — pinned in Task 1 (`price_zeroPoPrice_onlyZeroMatches`).
5. An invoice in a currency different from the PO must not *also* produce misleading price exceptions — pinned in Task 1 (`currencyMismatch_skipsPriceRule`).

---

## File structure

Backend (`backend/src/main/kotlin/com/example/erp/invoice/`):

| File | Responsibility |
|---|---|
| `matching/MatchModel.kt` | Snapshot input types, `MatchException`, `MatchExceptionCode` |
| `matching/InvoiceMatcher.kt` | Pure matching function: composition + one private function per rule |
| `SupplierInvoice.kt` | Entities `SupplierInvoice`, `SupplierInvoiceItem`, value `NewInvoiceLine` |
| `SupplierInvoiceRepository.kt` | Loading with entity graphs, duplicate lookup, earlier-invoiced quantities |
| `SupplierInvoiceService.kt` | Create/get/list, builds `MatchInput`, runs matcher |
| `SupplierInvoiceDtos.kt` | Requests, responses, mapping |
| `SupplierInvoiceController.kt` | `/api/invoices` endpoints |

Migration: `backend/src/main/resources/db/migration/V6__create_supplier_invoice.sql`

Tests: `backend/src/test/kotlin/com/example/erp/invoice/matching/InvoiceMatcherTest.kt`, `backend/src/test/kotlin/com/example/erp/invoice/SupplierInvoiceApiTest.kt`

Frontend (`frontend/src/features/invoices/`): `types.ts`, `api.ts`, `InvoicesPage.tsx`, `InvoiceDetailPage.tsx`, `MatchBadge.tsx`; modify `frontend/src/router.tsx`, `frontend/src/layout/AppLayout.tsx`.

Seed: modify `scripts/seed-demo-data.mjs`. Docs: modify `README.md`.

Commands (run from repo root unless stated):
- Backend unit test: `cd backend && ./mvnw -q test -Dtest=InvoiceMatcherTest`
- Backend integration test (needs Docker running): `cd backend && ./mvnw -q test -Dtest=SupplierInvoiceApiTest`
- Frontend check: `cd frontend && npm run build`

---

### Task 1: Matcher model, skeleton and failing tests

**Files:**
- Create: `backend/src/main/kotlin/com/example/erp/invoice/matching/MatchModel.kt`
- Create: `backend/src/main/kotlin/com/example/erp/invoice/matching/InvoiceMatcher.kt`
- Test: `backend/src/test/kotlin/com/example/erp/invoice/matching/InvoiceMatcherTest.kt`

**Interfaces:**
- Produces: `InvoiceMatcher.match(input: MatchInput): List<MatchException>`; types `MatchInput`, `InvoiceSnapshot`, `InvoiceLineSnapshot`, `PurchaseOrderSnapshot`, `PurchaseOrderLineSnapshot`, `MatchException`, `MatchExceptionCode` (all in package `com.example.erp.invoice.matching`).

- [ ] **Step 1: Create the model**

`MatchModel.kt`:

```kotlin
package com.example.erp.invoice.matching

import java.math.BigDecimal

/**
 * Everything the matcher needs, as plain data. Built by the service from entities; built by hand in
 * tests; serialisable to JSON for the M2 golden dataset and the M7 AI tools.
 */
data class MatchInput(
    val invoice: InvoiceSnapshot,
    /** Null when the invoice carries no purchase order reference. */
    val purchaseOrder: PurchaseOrderSnapshot?,
    /** Other invoices of the same supplier with the same invoice number (trimmed, case-insensitive). */
    val duplicateInvoiceIds: List<Long>,
)

data class InvoiceSnapshot(
    val id: Long,
    val invoiceNumber: String,
    val currency: String,
    val lines: List<InvoiceLineSnapshot>,
)

data class InvoiceLineSnapshot(
    val lineNumber: Int,
    /** Null when the line is not linked to a PO line (e.g. freight). */
    val purchaseOrderItemId: Long?,
    val description: String,
    val quantity: BigDecimal,
    val unitPrice: BigDecimal,
    val taxRate: BigDecimal,
)

data class PurchaseOrderSnapshot(
    val id: Long,
    val poNumber: String,
    val currency: String,
    val lines: List<PurchaseOrderLineSnapshot>,
)

data class PurchaseOrderLineSnapshot(
    val id: Long,
    val lineNumber: Int,
    val sku: String,
    val quantity: BigDecimal,
    val unitPrice: BigDecimal,
    val taxRate: BigDecimal,
    val receivedQuantity: BigDecimal,
    /** Sum invoiced for this PO line by EARLIER invoices (lower id). */
    val alreadyInvoicedQuantity: BigDecimal,
)

/** Declaration order = order within one line / within the header in the result list. */
enum class MatchExceptionCode {
    // header level
    DUPLICATE_INVOICE,
    MISSING_PO,
    CURRENCY_MISMATCH,

    // line level
    UNMATCHED_LINE,
    PRICE_MISMATCH,
    QUANTITY_MISMATCH,
    TAX_MISMATCH,
}

/**
 * One discrepancy. `expected` / `actual` are plain strings so they serialise identically everywhere
 * (API, eval dataset, LLM prompt).
 */
data class MatchException(
    val code: MatchExceptionCode,
    /** Invoice line number; null for header-level exceptions. */
    val lineNumber: Int?,
    val expected: String?,
    val actual: String?,
    val message: String,
)
```

- [ ] **Step 2: Create the matcher skeleton**

`InvoiceMatcher.kt` — composition and ordering are done; the seven rule functions are the user's exercise (Task 2):

```kotlin
package com.example.erp.invoice.matching

import java.math.BigDecimal

/**
 * Deterministic three-way match: invoice vs purchase order vs goods receipts.
 * Pure function — no Spring, no database, no clock. Empty result = matched.
 */
object InvoiceMatcher {

    /** Relative price deviation that is still accepted, in percent. Exactly 0.5 % passes. */
    val PRICE_TOLERANCE_PERCENT = BigDecimal("0.5")

    fun match(input: MatchInput): List<MatchException> {
        val exceptions = mutableListOf<MatchException>()
        exceptions += duplicateInvoice(input)

        val po = input.purchaseOrder
        if (po == null) {
            exceptions += missingPo(input.invoice)
            return exceptions.sortedWith(ORDER)
        }

        val currencyMismatch = currencyMismatch(input.invoice, po)
        exceptions += currencyMismatch

        for (line in input.invoice.lines) {
            val poLine = po.lines.find { it.id == line.purchaseOrderItemId }
            if (poLine == null) {
                exceptions += unmatchedLine(line)
                continue
            }
            if (currencyMismatch == null) exceptions += priceMismatch(line, poLine)
            exceptions += quantityMismatch(line, poLine)
            exceptions += taxMismatch(line, poLine)
        }
        return exceptions.sortedWith(ORDER)
    }

    // ---- rules: each returns the exception, or null when the rule passes ------------------------

    private fun duplicateInvoice(input: MatchInput): MatchException? =
        TODO("M1 exercise: DUPLICATE_INVOICE")

    private fun missingPo(invoice: InvoiceSnapshot): MatchException =
        TODO("M1 exercise: MISSING_PO")

    private fun currencyMismatch(invoice: InvoiceSnapshot, po: PurchaseOrderSnapshot): MatchException? =
        TODO("M1 exercise: CURRENCY_MISMATCH")

    private fun unmatchedLine(line: InvoiceLineSnapshot): MatchException =
        TODO("M1 exercise: UNMATCHED_LINE")

    private fun priceMismatch(line: InvoiceLineSnapshot, poLine: PurchaseOrderLineSnapshot): MatchException? =
        TODO("M1 exercise: PRICE_MISMATCH")

    private fun quantityMismatch(line: InvoiceLineSnapshot, poLine: PurchaseOrderLineSnapshot): MatchException? =
        TODO("M1 exercise: QUANTITY_MISMATCH")

    private fun taxMismatch(line: InvoiceLineSnapshot, poLine: PurchaseOrderLineSnapshot): MatchException? =
        TODO("M1 exercise: TAX_MISMATCH")

    // ---- helpers ---------------------------------------------------------------------------------

    /** 42.0000 -> "42", 0.50 -> "0.5": independent of database scale. */
    internal fun BigDecimal.plain(): String = stripTrailingZeros().toPlainString()

    /** Header (lineNumber null -> 0) before lines; within the same line, enum declaration order. */
    private val ORDER = compareBy<MatchException>({ it.lineNumber ?: 0 }, { it.code.ordinal })

    private operator fun MutableList<MatchException>.plusAssign(exception: MatchException?) {
        if (exception != null) add(exception)
    }
}
```

- [ ] **Step 3: Write the failing tests**

`InvoiceMatcherTest.kt`:

```kotlin
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
```

- [ ] **Step 4: Run the tests to verify they fail**

Run: `cd backend && ./mvnw -q test -Dtest=InvoiceMatcherTest`
Expected: compiles; **all 20 tests fail** (even `perfectInvoice_hasNoExceptions`), because `duplicateInvoice` runs for every input and throws `NotImplementedError: An operation is not implemented: M1 exercise: DUPLICATE_INVOICE`. That is the starting point for Task 2.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/example/erp/invoice/matching backend/src/test/kotlin/com/example/erp/invoice/matching
git commit -m "test(invoice): matcher model, skeleton and failing rule tests"
```

---

### Task 2: Implement the seven rules (USER EXERCISE)

**Files:**
- Modify: `backend/src/main/kotlin/com/example/erp/invoice/matching/InvoiceMatcher.kt` (the seven `TODO(...)` functions only)

**Interfaces:**
- Consumes: types from Task 1.
- Produces: a fully passing `InvoiceMatcherTest`. Signatures stay exactly as in Task 1.

Work rule by rule. After each rule run `cd backend && ./mvnw -q test -Dtest=InvoiceMatcherTest` and watch the failure count drop. Helpers available inside the object: `BigDecimal.plain()`, `PRICE_TOLERANCE_PERCENT`, `com.example.erp.shared.money.sameValueAs` (scale-insensitive equality — import it).

- [ ] **Step 1: `duplicateInvoice`** — return `null` when `input.duplicateInvoiceIds` is empty; otherwise header exception, `actual` = ids joined with `", "`.
- [ ] **Step 2: `missingPo`** — always returns a header exception (it is only called when the PO is null).
- [ ] **Step 3: `currencyMismatch`** — compare `invoice.currency` with `po.currency`; `expected` = PO currency, `actual` = invoice currency.
- [ ] **Step 4: `unmatchedLine`** — always returns a line exception for `line.lineNumber`.
- [ ] **Step 5: `priceMismatch`** — PO price 0: mismatch unless invoice price is 0. Otherwise deviation % = |invoice − PO| × 100 / PO; mismatch if `> PRICE_TOLERANCE_PERCENT`. Use `divide(poPrice, 6, RoundingMode.HALF_UP)` — a plain `divide` throws on non-terminating decimals.
- [ ] **Step 6: `quantityMismatch`** — invoiceable = received − alreadyInvoiced; mismatch if line quantity > invoiceable. `expected` = invoiceable, `actual` = line quantity.
- [ ] **Step 7: `taxMismatch`** — mismatch unless `line.taxRate sameValueAs poLine.taxRate`.
- [ ] **Step 8: Run all matcher tests** — Expected: `Tests run: 20, Failures: 0, Errors: 0`.
- [ ] **Step 9: Commit**

```bash
git add backend/src/main/kotlin/com/example/erp/invoice/matching/InvoiceMatcher.kt
git commit -m "feat(invoice): implement three-way matching rules"
```

<details>
<summary>Reference solution — only if stuck, or if the user delegates this task</summary>

```kotlin
    private fun duplicateInvoice(input: MatchInput): MatchException? {
        if (input.duplicateInvoiceIds.isEmpty()) return null
        return MatchException(
            MatchExceptionCode.DUPLICATE_INVOICE, null, null, input.duplicateInvoiceIds.joinToString(", "),
            "Invoice number ${input.invoice.invoiceNumber} was already used by this supplier " +
                "(invoice id ${input.duplicateInvoiceIds.joinToString(", ")})",
        )
    }

    private fun missingPo(invoice: InvoiceSnapshot): MatchException =
        MatchException(
            MatchExceptionCode.MISSING_PO, null, null, null,
            "Invoice ${invoice.invoiceNumber} does not reference a purchase order",
        )

    private fun currencyMismatch(invoice: InvoiceSnapshot, po: PurchaseOrderSnapshot): MatchException? {
        if (invoice.currency == po.currency) return null
        return MatchException(
            MatchExceptionCode.CURRENCY_MISMATCH, null, po.currency, invoice.currency,
            "Invoice currency ${invoice.currency} differs from ${po.poNumber} currency ${po.currency}",
        )
    }

    private fun unmatchedLine(line: InvoiceLineSnapshot): MatchException =
        MatchException(
            MatchExceptionCode.UNMATCHED_LINE, line.lineNumber, null, null,
            "Line ${line.lineNumber} (${line.description}) is not linked to a purchase order line",
        )

    private fun priceMismatch(line: InvoiceLineSnapshot, poLine: PurchaseOrderLineSnapshot): MatchException? {
        val poPrice = poLine.unitPrice
        val mismatch = if (poPrice.signum() == 0) line.unitPrice.signum() != 0
        else (line.unitPrice - poPrice).abs().multiply(BigDecimal(100))
            .divide(poPrice, 6, RoundingMode.HALF_UP) > PRICE_TOLERANCE_PERCENT
        if (!mismatch) return null
        return MatchException(
            MatchExceptionCode.PRICE_MISMATCH, line.lineNumber, poPrice.plain(), line.unitPrice.plain(),
            "Line ${line.lineNumber}: unit price ${line.unitPrice.plain()} differs from PO price ${poPrice.plain()} " +
                "by more than $PRICE_TOLERANCE_PERCENT %",
        )
    }

    private fun quantityMismatch(line: InvoiceLineSnapshot, poLine: PurchaseOrderLineSnapshot): MatchException? {
        val invoiceable = poLine.receivedQuantity - poLine.alreadyInvoicedQuantity
        if (line.quantity <= invoiceable) return null
        return MatchException(
            MatchExceptionCode.QUANTITY_MISMATCH, line.lineNumber, invoiceable.plain(), line.quantity.plain(),
            "Line ${line.lineNumber}: invoiced ${line.quantity.plain()} but only ${invoiceable.plain()} can be billed " +
                "(received ${poLine.receivedQuantity.plain()}, already invoiced ${poLine.alreadyInvoicedQuantity.plain()})",
        )
    }

    private fun taxMismatch(line: InvoiceLineSnapshot, poLine: PurchaseOrderLineSnapshot): MatchException? {
        if (line.taxRate sameValueAs poLine.taxRate) return null
        return MatchException(
            MatchExceptionCode.TAX_MISMATCH, line.lineNumber, poLine.taxRate.plain(), line.taxRate.plain(),
            "Line ${line.lineNumber}: tax rate ${line.taxRate.plain()} % differs from PO tax rate ${poLine.taxRate.plain()} %",
        )
    }
```

Needed imports: `java.math.RoundingMode`, `com.example.erp.shared.money.sameValueAs`.
</details>

---

### Task 3: Invoice persistence and create/get/list API

**Files:**
- Create: `backend/src/main/resources/db/migration/V6__create_supplier_invoice.sql`
- Create: `backend/src/main/kotlin/com/example/erp/invoice/SupplierInvoice.kt`
- Create: `backend/src/main/kotlin/com/example/erp/invoice/SupplierInvoiceRepository.kt`
- Create: `backend/src/main/kotlin/com/example/erp/invoice/SupplierInvoiceDtos.kt`
- Create: `backend/src/main/kotlin/com/example/erp/invoice/SupplierInvoiceService.kt`
- Create: `backend/src/main/kotlin/com/example/erp/invoice/SupplierInvoiceController.kt`
- Test: `backend/src/test/kotlin/com/example/erp/invoice/SupplierInvoiceApiTest.kt`

**Interfaces:**
- Consumes: `SupplierService.get(id): Supplier`, `PurchaseOrderService.get(id): PurchaseOrder` (loads supplier, items, items.product), `PurchaseOrder.lines`, `DocumentTotals`, `TotalsResponse`/`toResponse()` and `SupplierRef` from `com.example.erp.purchaseorder`, `PurchaseOrderRef` from `com.example.erp.goodsreceipt`.
- Produces: `SupplierInvoice` entity (`supplier`, `invoiceNumber`, `invoiceDate`, `currency`, `purchaseOrder`, `lines`, `totals()`), `SupplierInvoiceRepository` (incl. `findDuplicateIds`, `findEarlierInvoicedQuantities`), `SupplierInvoiceService.create/get/list`, DTOs `SupplierInvoiceResponse`, `SupplierInvoiceSummaryResponse`. Task 4 adds `match`.

- [ ] **Step 1: Write the failing API test**

`SupplierInvoiceApiTest.kt` (Task 4 appends more tests to this class):

```kotlin
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
    fun invalidFields_return400() {
        invoice(quantity = "0").andExpect { status { isBadRequest() } }
        invoice(currency = "eur").andExpect { status { isBadRequest() } }
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd backend && ./mvnw -q test -Dtest=SupplierInvoiceApiTest`
Expected: FAIL — `POST /api/invoices` returns 404 (no controller yet).

- [ ] **Step 3: Migration**

`V6__create_supplier_invoice.sql`:

```sql
-- Supplier invoices ("Eingangsrechnungen"). Matching against PO and goods receipts is computed
-- on demand by InvoiceMatcher; nothing about the match result is stored here.
CREATE TABLE supplier_invoice
(
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    supplier_id       BIGINT      NOT NULL REFERENCES supplier (id),
    -- The supplier's own number as printed. Deliberately NOT unique: duplicates must be storable
    -- so the DUPLICATE_INVOICE rule can detect them.
    invoice_number    VARCHAR(64) NOT NULL,
    invoice_date      DATE        NOT NULL,
    currency          VARCHAR(3)  NOT NULL,
    purchase_order_id BIGINT REFERENCES purchase_order (id),
    created_at        TIMESTAMPTZ NOT NULL,
    updated_at        TIMESTAMPTZ NOT NULL,

    CONSTRAINT ck_supplier_invoice_currency CHECK (currency ~ '^[A-Z]{3}$')
);

CREATE INDEX ix_supplier_invoice_supplier_number ON supplier_invoice (supplier_id, lower(invoice_number));
CREATE INDEX ix_supplier_invoice_purchase_order ON supplier_invoice (purchase_order_id);

CREATE TABLE supplier_invoice_item
(
    id                     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    supplier_invoice_id    BIGINT         NOT NULL REFERENCES supplier_invoice (id) ON DELETE CASCADE,
    line_number            INTEGER        NOT NULL,
    purchase_order_item_id BIGINT REFERENCES purchase_order_item (id),
    description            VARCHAR(500)   NOT NULL,
    quantity               NUMERIC(19, 3) NOT NULL,
    unit_price             NUMERIC(19, 4) NOT NULL,
    tax_rate               NUMERIC(5, 2)  NOT NULL,
    created_at             TIMESTAMPTZ    NOT NULL,
    updated_at             TIMESTAMPTZ    NOT NULL,

    CONSTRAINT uq_supplier_invoice_item_line UNIQUE (supplier_invoice_id, line_number),
    CONSTRAINT ck_supplier_invoice_item_quantity CHECK (quantity > 0),
    CONSTRAINT ck_supplier_invoice_item_unit_price CHECK (unit_price >= 0),
    CONSTRAINT ck_supplier_invoice_item_tax_rate CHECK (tax_rate BETWEEN 0 AND 100)
);

CREATE INDEX ix_supplier_invoice_item_po_item ON supplier_invoice_item (purchase_order_item_id);
```

- [ ] **Step 4: Entities**

`SupplierInvoice.kt`:

```kotlin
package com.example.erp.invoice

import com.example.erp.purchaseorder.PurchaseOrder
import com.example.erp.purchaseorder.PurchaseOrderItem
import com.example.erp.shared.error.requireRule
import com.example.erp.shared.money.DocumentTotals
import com.example.erp.shared.money.MoneyRounding
import com.example.erp.shared.money.TaxableLine
import com.example.erp.shared.persistence.BaseEntity
import com.example.erp.supplier.Supplier
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.LocalDate

/** Input for one invoice line; the entity numbers the lines. */
data class NewInvoiceLine(
    val purchaseOrderItem: PurchaseOrderItem?,
    val description: String,
    val quantity: BigDecimal,
    val unitPrice: BigDecimal,
    val taxRate: BigDecimal,
)

/**
 * A supplier invoice as received. Immutable once created; whether it matches the PO and goods
 * receipts is not a property of the invoice but computed by InvoiceMatcher on demand.
 */
@Entity
@Table(name = "supplier_invoice")
class SupplierInvoice(
    supplier: Supplier,
    invoiceNumber: String,
    invoiceDate: LocalDate,
    currency: String,
    purchaseOrder: PurchaseOrder?,
    lines: List<NewInvoiceLine>,
) : BaseEntity() {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id", nullable = false, updatable = false)
    var supplier: Supplier = supplier
        protected set

    @Column(name = "invoice_number", nullable = false, updatable = false, length = 64)
    var invoiceNumber: String = invoiceNumber.trim()
        protected set

    @Column(name = "invoice_date", nullable = false, updatable = false)
    var invoiceDate: LocalDate = invoiceDate
        protected set

    @Column(nullable = false, updatable = false, length = 3)
    var currency: String = currency
        protected set

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_order_id", updatable = false)
    var purchaseOrder: PurchaseOrder? = purchaseOrder
        protected set

    @OneToMany(mappedBy = "invoice", cascade = [CascadeType.ALL])
    protected var items: MutableList<SupplierInvoiceItem> = mutableListOf()

    val lines: List<SupplierInvoiceItem>
        get() = items.sortedBy { it.lineNumber }

    fun totals(): DocumentTotals = DocumentTotals.of(items.map { TaxableLine(it.netAmount, it.taxRate) })

    init {
        requireRule(lines.isNotEmpty()) { "An invoice needs at least one line" }
        requireRule(!invoiceDate.isAfter(LocalDate.now())) { "Invoice date $invoiceDate lies in the future" }
        if (purchaseOrder != null) {
            requireRule(purchaseOrder.supplier.id == supplier.id) {
                "Purchase order ${purchaseOrder.poNumber} belongs to another supplier"
            }
        }
        val referenced = lines.mapNotNull { it.purchaseOrderItem }
        requireRule(referenced.isEmpty() || purchaseOrder != null) {
            "Invoice lines can only reference purchase order lines when the invoice references a purchase order"
        }
        requireRule(referenced.all { it.purchaseOrder.id == purchaseOrder?.id }) {
            "Invoice lines may only reference lines of ${purchaseOrder?.poNumber}"
        }
        requireRule(referenced.map { it.id }.toSet().size == referenced.size) {
            "Each purchase order line may appear only once per invoice"
        }
        lines.forEachIndexed { index, line -> items += SupplierInvoiceItem(this, index + 1, line) }
    }
}

@Entity
@Table(name = "supplier_invoice_item")
class SupplierInvoiceItem(
    invoice: SupplierInvoice,
    lineNumber: Int,
    line: NewInvoiceLine,
) : BaseEntity() {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_invoice_id", nullable = false, updatable = false)
    var invoice: SupplierInvoice = invoice
        protected set

    @Column(name = "line_number", nullable = false, updatable = false)
    var lineNumber: Int = lineNumber
        protected set

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_order_item_id", updatable = false)
    var purchaseOrderItem: PurchaseOrderItem? = line.purchaseOrderItem
        protected set

    @Column(nullable = false, updatable = false, length = 500)
    var description: String = line.description.trim()
        protected set

    @Column(nullable = false, updatable = false, precision = 19, scale = 3)
    var quantity: BigDecimal = MoneyRounding.quantity(line.quantity)
        protected set

    @Column(name = "unit_price", nullable = false, updatable = false, precision = 19, scale = 4)
    var unitPrice: BigDecimal = MoneyRounding.unitPrice(line.unitPrice)
        protected set

    @Column(name = "tax_rate", nullable = false, updatable = false, precision = 5, scale = 2)
    var taxRate: BigDecimal = MoneyRounding.percent(line.taxRate)
        protected set

    val netAmount: BigDecimal
        get() = DocumentTotals.lineNet(quantity, unitPrice)
}
```

- [ ] **Step 5: Repository**

`SupplierInvoiceRepository.kt`:

```kotlin
package com.example.erp.invoice

import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.math.BigDecimal

/** Quantity already invoiced for one PO line (Spring Data interface projection). */
interface InvoicedQuantity {
    val purchaseOrderItemId: Long
    val quantity: BigDecimal
}

interface SupplierInvoiceRepository : JpaRepository<SupplierInvoice, Long> {

    @EntityGraph(attributePaths = ["supplier", "purchaseOrder", "items", "items.purchaseOrderItem"])
    fun findWithDetailsById(id: Long): SupplierInvoice?

    @EntityGraph(attributePaths = ["supplier", "purchaseOrder", "items"])
    fun findAllByOrderByIdDesc(): List<SupplierInvoice>

    /** Other invoices of the same supplier with the same number, ignoring case and surrounding spaces. */
    @Query(
        """SELECT i.id FROM SupplierInvoice i
           WHERE i.supplier.id = :supplierId AND i.id <> :invoiceId
             AND lower(trim(i.invoiceNumber)) = lower(trim(:invoiceNumber))
           ORDER BY i.id""",
    )
    fun findDuplicateIds(supplierId: Long, invoiceNumber: String, invoiceId: Long): List<Long>

    /** Per PO line: quantity billed by invoices created BEFORE the given one (lower id). */
    @Query(
        """SELECT it.purchaseOrderItem.id AS purchaseOrderItemId, SUM(it.quantity) AS quantity
           FROM SupplierInvoiceItem it
           WHERE it.purchaseOrderItem.id IN :purchaseOrderItemIds AND it.invoice.id < :invoiceId
           GROUP BY it.purchaseOrderItem.id""",
    )
    fun findEarlierInvoicedQuantities(purchaseOrderItemIds: Collection<Long>, invoiceId: Long): List<InvoicedQuantity>
}
```

- [ ] **Step 6: DTOs**

`SupplierInvoiceDtos.kt`:

```kotlin
package com.example.erp.invoice

import com.example.erp.goodsreceipt.PurchaseOrderRef
import com.example.erp.purchaseorder.SupplierRef
import com.example.erp.purchaseorder.TotalsResponse
import com.example.erp.purchaseorder.toResponse
import com.example.erp.invoice.matching.MatchException
import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Digits
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

// ---- requests ---------------------------------------------------------------------------------

data class SupplierInvoiceItemRequest(
    /** Null for lines without a PO line, e.g. freight. */
    val purchaseOrderItemId: Long? = null,
    @field:NotBlank
    @field:Size(max = 500)
    val description: String,
    @field:Positive
    @field:Digits(integer = 16, fraction = 3)
    val quantity: BigDecimal,
    @field:PositiveOrZero
    @field:Digits(integer = 15, fraction = 4)
    val unitPrice: BigDecimal,
    @field:DecimalMin("0")
    @field:DecimalMax("100")
    @field:Digits(integer = 3, fraction = 2)
    val taxRate: BigDecimal,
)

data class CreateSupplierInvoiceRequest(
    val supplierId: Long,
    @field:NotBlank
    @field:Size(max = 64)
    val invoiceNumber: String,
    val invoiceDate: LocalDate,
    @field:Pattern(regexp = "[A-Z]{3}", message = "must be an ISO 4217 code such as EUR")
    val currency: String,
    /** Null when the supplier's invoice carries no PO number. */
    val purchaseOrderId: Long? = null,
    @field:NotEmpty
    @field:Valid
    val items: List<SupplierInvoiceItemRequest>,
)

// ---- responses --------------------------------------------------------------------------------

data class SupplierInvoiceItemResponse(
    val id: Long,
    val lineNumber: Int,
    val purchaseOrderItemId: Long?,
    val purchaseOrderLineNumber: Int?,
    val description: String,
    val quantity: BigDecimal,
    val unitPrice: BigDecimal,
    val taxRate: BigDecimal,
    val netAmount: BigDecimal,
)

data class SupplierInvoiceResponse(
    val id: Long,
    val invoiceNumber: String,
    val supplier: SupplierRef,
    val purchaseOrder: PurchaseOrderRef?,
    val invoiceDate: LocalDate,
    val currency: String,
    val items: List<SupplierInvoiceItemResponse>,
    val totals: TotalsResponse,
    val createdAt: Instant?,
)

data class SupplierInvoiceSummaryResponse(
    val id: Long,
    val invoiceNumber: String,
    val supplier: SupplierRef,
    val purchaseOrder: PurchaseOrderRef?,
    val invoiceDate: LocalDate,
    val currency: String,
    val total: BigDecimal,
    /** Number of match exceptions; 0 = matched. */
    val exceptionCount: Int,
)

data class MatchResultResponse(val matched: Boolean, val exceptions: List<MatchException>)

// ---- mapping ----------------------------------------------------------------------------------

private fun SupplierInvoice.supplierRef() = SupplierRef(supplier.requireId(), supplier.supplierNumber, supplier.name)

private fun SupplierInvoice.poRef() = purchaseOrder?.let { PurchaseOrderRef(it.requireId(), it.poNumber) }

fun SupplierInvoice.toResponse() = SupplierInvoiceResponse(
    id = requireId(),
    invoiceNumber = invoiceNumber,
    supplier = supplierRef(),
    purchaseOrder = poRef(),
    invoiceDate = invoiceDate,
    currency = currency,
    items = lines.map {
        SupplierInvoiceItemResponse(
            id = it.requireId(),
            lineNumber = it.lineNumber,
            purchaseOrderItemId = it.purchaseOrderItem?.requireId(),
            purchaseOrderLineNumber = it.purchaseOrderItem?.lineNumber,
            description = it.description,
            quantity = it.quantity,
            unitPrice = it.unitPrice,
            taxRate = it.taxRate,
            netAmount = it.netAmount,
        )
    },
    totals = totals().toResponse(),
    createdAt = createdAt,
)

fun SupplierInvoice.toSummaryResponse(exceptionCount: Int) = SupplierInvoiceSummaryResponse(
    id = requireId(),
    invoiceNumber = invoiceNumber,
    supplier = supplierRef(),
    purchaseOrder = poRef(),
    invoiceDate = invoiceDate,
    currency = currency,
    total = totals().total,
    exceptionCount = exceptionCount,
)
```

- [ ] **Step 7: Service (create/get/list; `list` uses a placeholder count of 0 until Task 4)**

`SupplierInvoiceService.kt`:

```kotlin
package com.example.erp.invoice

import com.example.erp.purchaseorder.PurchaseOrderService
import com.example.erp.shared.error.BusinessRuleViolationException
import com.example.erp.shared.error.NotFoundException
import com.example.erp.supplier.SupplierService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Returns response DTOs (like GoodsReceiptService): an invoice spans supplier, PO and PO lines,
 * and mapping inside the transaction keeps lazy associations loadable with open-in-view disabled.
 */
@Service
@Transactional
class SupplierInvoiceService(
    private val invoices: SupplierInvoiceRepository,
    private val supplierService: SupplierService,
    private val purchaseOrderService: PurchaseOrderService,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun create(request: CreateSupplierInvoiceRequest): SupplierInvoiceResponse {
        val supplier = supplierService.get(request.supplierId)
        val po = request.purchaseOrderId?.let { purchaseOrderService.get(it) }

        val lines = request.items.map { item ->
            val poItem = item.purchaseOrderItemId?.let { poItemId ->
                po?.lines?.find { it.id == poItemId }
                    ?: throw BusinessRuleViolationException(
                        "Purchase order item $poItemId does not belong to " + (po?.poNumber ?: "the invoice (no purchase order given)"),
                    )
            }
            NewInvoiceLine(poItem, item.description, item.quantity, item.unitPrice, item.taxRate)
        }

        val invoice = invoices.save(
            SupplierInvoice(supplier, request.invoiceNumber, request.invoiceDate, request.currency, po, lines),
        )
        log.atInfo()
            .addKeyValue("invoiceId", invoice.id)
            .addKeyValue("supplierId", supplier.id)
            .addKeyValue("purchaseOrderId", po?.id)
            .log("Supplier invoice created")
        return invoice.toResponse()
    }

    @Transactional(readOnly = true)
    fun get(id: Long): SupplierInvoiceResponse = load(id).toResponse()

    @Transactional(readOnly = true)
    fun list(): List<SupplierInvoiceSummaryResponse> =
        invoices.findAllByOrderByIdDesc().map { it.toSummaryResponse(exceptionCount = 0) }

    private fun load(id: Long): SupplierInvoice =
        invoices.findWithDetailsById(id) ?: throw NotFoundException("Invoice $id not found")
}
```

Note: when `purchaseOrderItemId` is set but `purchaseOrderId` is null, the service throws before the entity's own check — both produce 422, which is what `lineReferenceWithoutPurchaseOrder_returns422` expects.

- [ ] **Step 8: Controller**

`SupplierInvoiceController.kt`:

```kotlin
package com.example.erp.invoice

import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URI

@RestController
@RequestMapping("/api/invoices")
class SupplierInvoiceController(private val service: SupplierInvoiceService) {

    @PostMapping
    fun create(@Valid @RequestBody request: CreateSupplierInvoiceRequest): ResponseEntity<SupplierInvoiceResponse> {
        val invoice = service.create(request)
        return ResponseEntity.created(URI("/api/invoices/${invoice.id}")).body(invoice)
    }

    @GetMapping("/{id}")
    fun get(@PathVariable id: Long): SupplierInvoiceResponse = service.get(id)

    @GetMapping
    fun list(): List<SupplierInvoiceSummaryResponse> = service.list()
}
```

- [ ] **Step 9: Run the API test**

Run: `cd backend && ./mvnw -q test -Dtest=SupplierInvoiceApiTest`
Expected: PASS (10 tests). If `futureInvoiceDate` fails with 400 instead of 422, check that no `@PastOrPresent` was added to the DTO — the rule lives in the entity on purpose.

- [ ] **Step 10: Commit**

```bash
git add backend/src/main/resources/db/migration/V6__create_supplier_invoice.sql backend/src/main/kotlin/com/example/erp/invoice backend/src/test/kotlin/com/example/erp/invoice/SupplierInvoiceApiTest.kt
git commit -m "feat(invoice): supplier invoices with create/get/list API"
```

---

### Task 4: Match endpoint and exception count

Requires Task 2 (rules implemented).

**Files:**
- Modify: `backend/src/main/kotlin/com/example/erp/invoice/SupplierInvoiceService.kt`
- Modify: `backend/src/main/kotlin/com/example/erp/invoice/SupplierInvoiceController.kt`
- Test: `backend/src/test/kotlin/com/example/erp/invoice/SupplierInvoiceApiTest.kt` (append)

**Interfaces:**
- Consumes: `InvoiceMatcher.match`, snapshot types (Task 1), repository queries (Task 3).
- Produces: `SupplierInvoiceService.match(id): MatchResultResponse`, `SupplierInvoiceService.matchInput(invoice): MatchInput` (internal; reused by M2/M7), `GET /api/invoices/{id}/match`, real `exceptionCount` in the list.

- [ ] **Step 1: Append failing tests to `SupplierInvoiceApiTest`**

```kotlin
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
```

- [ ] **Step 2: Run to verify they fail**

Run: `cd backend && ./mvnw -q test -Dtest=SupplierInvoiceApiTest`
Expected: the six new tests FAIL (`/match` → 404 / 500, exceptionCount 0 for the price test); the Task 3 tests still pass.

- [ ] **Step 3: Add matching to the service**

In `SupplierInvoiceService.kt` add imports:

```kotlin
import com.example.erp.invoice.matching.InvoiceLineSnapshot
import com.example.erp.invoice.matching.InvoiceMatcher
import com.example.erp.invoice.matching.InvoiceSnapshot
import com.example.erp.invoice.matching.MatchInput
import com.example.erp.invoice.matching.PurchaseOrderLineSnapshot
import com.example.erp.invoice.matching.PurchaseOrderSnapshot
import com.example.erp.shared.money.MoneyRounding
import java.math.BigDecimal
```

Replace `list()` and add `match` / `matchInput`:

```kotlin
    @Transactional(readOnly = true)
    fun list(): List<SupplierInvoiceSummaryResponse> =
        invoices.findAllByOrderByIdDesc().map { it.toSummaryResponse(InvoiceMatcher.match(matchInput(it)).size) }

    @Transactional(readOnly = true)
    fun match(id: Long): MatchResultResponse {
        val exceptions = InvoiceMatcher.match(matchInput(load(id)))
        return MatchResultResponse(matched = exceptions.isEmpty(), exceptions = exceptions)
    }

    /** Snapshot of everything the matcher needs. Also the future input of eval datasets and AI tools. */
    internal fun matchInput(invoice: SupplierInvoice): MatchInput {
        val invoiceId = invoice.requireId()
        val po = invoice.purchaseOrder?.let { purchaseOrderService.get(it.requireId()) }

        val poSnapshot = po?.let {
            val poLines = it.lines
            val invoicedBefore = invoices
                .findEarlierInvoicedQuantities(poLines.map { line -> line.requireId() }, invoiceId)
                .associate { q -> q.purchaseOrderItemId to q.quantity }
            PurchaseOrderSnapshot(
                id = it.requireId(),
                poNumber = it.poNumber,
                currency = it.currency,
                lines = poLines.map { line ->
                    PurchaseOrderLineSnapshot(
                        id = line.requireId(),
                        lineNumber = line.lineNumber,
                        sku = line.product.sku,
                        quantity = line.quantity,
                        unitPrice = line.unitPrice,
                        taxRate = line.taxRate,
                        receivedQuantity = line.receivedQuantity,
                        alreadyInvoicedQuantity = MoneyRounding.quantity(invoicedBefore[line.requireId()] ?: BigDecimal.ZERO),
                    )
                },
            )
        }

        return MatchInput(
            invoice = InvoiceSnapshot(
                id = invoiceId,
                invoiceNumber = invoice.invoiceNumber,
                currency = invoice.currency,
                lines = invoice.lines.map {
                    InvoiceLineSnapshot(
                        lineNumber = it.lineNumber,
                        purchaseOrderItemId = it.purchaseOrderItem?.requireId(),
                        description = it.description,
                        quantity = it.quantity,
                        unitPrice = it.unitPrice,
                        taxRate = it.taxRate,
                    )
                },
            ),
            purchaseOrder = poSnapshot,
            duplicateInvoiceIds = invoices.findDuplicateIds(invoice.supplier.requireId(), invoice.invoiceNumber, invoiceId),
        )
    }
```

Note: in `list()` each invoice triggers a few extra queries (PO with lines, lazy PO-line proxies, the two repository queries). The read-only transaction is open, so this is correct, just N+1 — accepted by the spec at demo data volume. Do not optimise it in M1.

- [ ] **Step 4: Add the endpoint**

In `SupplierInvoiceController.kt`:

```kotlin
    @GetMapping("/{id}/match")
    fun match(@PathVariable id: Long): MatchResultResponse = service.match(id)
```

- [ ] **Step 5: Run the full backend suite**

Run: `cd backend && ./mvnw -q verify`
Expected: all tests pass, including the 16 in `SupplierInvoiceApiTest` and the 20 in `InvoiceMatcherTest`.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/kotlin/com/example/erp/invoice backend/src/test/kotlin/com/example/erp/invoice/SupplierInvoiceApiTest.kt
git commit -m "feat(invoice): three-way match endpoint and exception counts"
```

---

### Task 5: Read-only invoice UI

**Files:**
- Create: `frontend/src/features/invoices/types.ts`, `api.ts`, `MatchBadge.tsx`, `InvoicesPage.tsx`, `InvoiceDetailPage.tsx`
- Modify: `frontend/src/router.tsx`, `frontend/src/layout/AppLayout.tsx`

**Interfaces:**
- Consumes: `GET /api/invoices`, `GET /api/invoices/{id}`, `GET /api/invoices/{id}/match` (Task 3/4); `http` from `../../api/http`; `formatDate`, `formatMoney`, `formatUnitPrice`, `formatQuantity`, `formatPercent` from `../../lib/format`; `SupplierRef` from `../purchase-orders/types`; `PurchaseOrderRef` from `../goods-receipts/types`.
- Produces: routes `/invoices`, `/invoices/:id`; nav section "Accounts payable".

- [ ] **Step 1: Types**

`types.ts`:

```ts
import type { PurchaseOrderRef } from '../goods-receipts/types'
import type { SupplierRef } from '../purchase-orders/types'

/** Mirrors backend supplier invoice DTOs. */

export interface InvoiceItem {
  id: number
  lineNumber: number
  purchaseOrderItemId: number | null
  purchaseOrderLineNumber: number | null
  description: string
  quantity: number
  unitPrice: number
  taxRate: number
  netAmount: number
}

export interface Invoice {
  id: number
  invoiceNumber: string
  supplier: SupplierRef
  purchaseOrder: PurchaseOrderRef | null
  invoiceDate: string
  currency: string
  items: InvoiceItem[]
  totals: { subtotal: number; taxAmount: number; total: number }
  createdAt: string
}

export interface InvoiceSummary {
  id: number
  invoiceNumber: string
  supplier: SupplierRef
  purchaseOrder: PurchaseOrderRef | null
  invoiceDate: string
  currency: string
  total: number
  exceptionCount: number
}

export type MatchExceptionCode =
  | 'DUPLICATE_INVOICE'
  | 'MISSING_PO'
  | 'CURRENCY_MISMATCH'
  | 'UNMATCHED_LINE'
  | 'PRICE_MISMATCH'
  | 'QUANTITY_MISMATCH'
  | 'TAX_MISMATCH'

export interface MatchException {
  code: MatchExceptionCode
  lineNumber: number | null
  expected: string | null
  actual: string | null
  message: string
}

export interface MatchResult {
  matched: boolean
  exceptions: MatchException[]
}
```

- [ ] **Step 2: API hooks**

`api.ts`:

```ts
import { useQuery } from '@tanstack/react-query'
import { http } from '../../api/http'
import type { Invoice, InvoiceSummary, MatchResult } from './types'

export const invoiceKeys = {
  all: ['invoices'] as const,
  list: () => ['invoices', 'list'] as const,
  detail: (id: number) => ['invoices', 'detail', id] as const,
  match: (id: number) => ['invoices', 'match', id] as const,
}

export function useInvoices() {
  return useQuery({ queryKey: invoiceKeys.list(), queryFn: () => http.get<InvoiceSummary[]>('/invoices') })
}

export function useInvoice(id: number) {
  return useQuery({ queryKey: invoiceKeys.detail(id), queryFn: () => http.get<Invoice>(`/invoices/${id}`) })
}

/** Computed on the server on every request; nothing is stored. */
export function useInvoiceMatch(id: number) {
  return useQuery({ queryKey: invoiceKeys.match(id), queryFn: () => http.get<MatchResult>(`/invoices/${id}/match`) })
}
```

- [ ] **Step 3: Badge**

`MatchBadge.tsx`:

```tsx
import { Badge } from '@mantine/core'

export function MatchBadge({ exceptionCount }: { exceptionCount: number }) {
  if (exceptionCount === 0) return <Badge color="green">Matched</Badge>
  return <Badge color="red">{exceptionCount === 1 ? '1 exception' : `${exceptionCount} exceptions`}</Badge>
}
```

- [ ] **Step 4: List page**

`InvoicesPage.tsx`:

```tsx
import { Alert, Anchor, Loader, Table, Text, Title } from '@mantine/core'
import { Link } from 'react-router'
import { formatDate, formatMoney } from '../../lib/format'
import { useInvoices } from './api'
import { MatchBadge } from './MatchBadge'

export function InvoicesPage() {
  const invoices = useInvoices()

  return (
    <>
      <Title order={2} mb="md">
        Supplier invoices
      </Title>

      {invoices.isPending && <Loader />}
      {invoices.isError && <Alert color="red">Could not load invoices: {invoices.error.message}</Alert>}
      {invoices.isSuccess && invoices.data.length === 0 && (
        <Text c="dimmed">No invoices yet. Run scripts/seed-demo-data.mjs to add demo invoices.</Text>
      )}
      {invoices.isSuccess && invoices.data.length > 0 && (
        <Table striped highlightOnHover>
          <Table.Thead>
            <Table.Tr>
              <Table.Th>Invoice number</Table.Th>
              <Table.Th>Supplier</Table.Th>
              <Table.Th>Purchase order</Table.Th>
              <Table.Th>Date</Table.Th>
              <Table.Th ta="right">Total</Table.Th>
              <Table.Th>Match</Table.Th>
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {invoices.data.map((inv) => (
              <Table.Tr key={inv.id}>
                <Table.Td>
                  <Anchor component={Link} to={`/invoices/${inv.id}`}>
                    {inv.invoiceNumber}
                  </Anchor>
                </Table.Td>
                <Table.Td>{inv.supplier.name}</Table.Td>
                <Table.Td>
                  {inv.purchaseOrder ? (
                    <Anchor component={Link} to={`/purchase-orders/${inv.purchaseOrder.id}`}>
                      {inv.purchaseOrder.poNumber}
                    </Anchor>
                  ) : (
                    '-'
                  )}
                </Table.Td>
                <Table.Td>{formatDate(inv.invoiceDate)}</Table.Td>
                <Table.Td ta="right">{formatMoney(inv.total, inv.currency)}</Table.Td>
                <Table.Td>
                  <MatchBadge exceptionCount={inv.exceptionCount} />
                </Table.Td>
              </Table.Tr>
            ))}
          </Table.Tbody>
        </Table>
      )}
    </>
  )
}
```

- [ ] **Step 5: Detail page**

`InvoiceDetailPage.tsx`:

```tsx
import { Alert, Anchor, Group, Loader, SimpleGrid, Stack, Table, Text, Title } from '@mantine/core'
import type { ReactNode } from 'react'
import { Link, useParams } from 'react-router'
import { formatDate, formatMoney, formatPercent, formatQuantity, formatUnitPrice } from '../../lib/format'
import { useInvoice, useInvoiceMatch } from './api'
import { MatchBadge } from './MatchBadge'
import type { MatchResult } from './types'

export function InvoiceDetailPage() {
  const id = Number(useParams().id)
  const invoice = useInvoice(id)
  const match = useInvoiceMatch(id)

  if (invoice.isPending) return <Loader />
  if (invoice.isError) return <Alert color="red">Could not load invoice: {invoice.error.message}</Alert>
  const inv = invoice.data

  return (
    <Stack>
      <Anchor component={Link} to="/invoices" size="sm">
        ← Supplier invoices
      </Anchor>
      <Group>
        <Title order={2}>{inv.invoiceNumber}</Title>
        {match.isSuccess && <MatchBadge exceptionCount={match.data.exceptions.length} />}
      </Group>

      <SimpleGrid cols={{ base: 1, sm: 4 }}>
        <Info label="Supplier">
          {inv.supplier.supplierNumber} · {inv.supplier.name}
        </Info>
        <Info label="Purchase order">
          {inv.purchaseOrder ? (
            <Anchor component={Link} to={`/purchase-orders/${inv.purchaseOrder.id}`}>
              {inv.purchaseOrder.poNumber}
            </Anchor>
          ) : (
            '-'
          )}
        </Info>
        <Info label="Invoice date">{formatDate(inv.invoiceDate)}</Info>
        <Info label="Total">{formatMoney(inv.totals.total, inv.currency)}</Info>
      </SimpleGrid>

      <Title order={4}>Three-way match</Title>
      {match.isPending && <Loader size="sm" />}
      {match.isError && <Alert color="red">Could not run the match: {match.error.message}</Alert>}
      {match.isSuccess && <MatchExceptions result={match.data} />}

      <Title order={4}>Lines</Title>
      <Table striped>
        <Table.Thead>
          <Table.Tr>
            <Table.Th>Line</Table.Th>
            <Table.Th>PO line</Table.Th>
            <Table.Th>Description</Table.Th>
            <Table.Th ta="right">Quantity</Table.Th>
            <Table.Th ta="right">Unit price</Table.Th>
            <Table.Th ta="right">Tax</Table.Th>
            <Table.Th ta="right">Net</Table.Th>
          </Table.Tr>
        </Table.Thead>
        <Table.Tbody>
          {inv.items.map((item) => (
            <Table.Tr key={item.id}>
              <Table.Td>{item.lineNumber}</Table.Td>
              <Table.Td>{item.purchaseOrderLineNumber ?? '-'}</Table.Td>
              <Table.Td>{item.description}</Table.Td>
              <Table.Td ta="right">{formatQuantity(item.quantity)}</Table.Td>
              <Table.Td ta="right">{formatUnitPrice(item.unitPrice, inv.currency)}</Table.Td>
              <Table.Td ta="right">{formatPercent(item.taxRate)}</Table.Td>
              <Table.Td ta="right">{formatMoney(item.netAmount, inv.currency)}</Table.Td>
            </Table.Tr>
          ))}
        </Table.Tbody>
      </Table>
    </Stack>
  )
}

function MatchExceptions({ result }: { result: MatchResult }) {
  if (result.matched) {
    return <Alert color="green">Invoice, purchase order and goods receipts agree.</Alert>
  }
  return (
    <Table>
      <Table.Thead>
        <Table.Tr>
          <Table.Th>Rule</Table.Th>
          <Table.Th>Line</Table.Th>
          <Table.Th>Expected</Table.Th>
          <Table.Th>Actual</Table.Th>
          <Table.Th>Details</Table.Th>
        </Table.Tr>
      </Table.Thead>
      <Table.Tbody>
        {result.exceptions.map((ex, index) => (
          <Table.Tr key={index}>
            <Table.Td>
              <Text fw={600} c="red" size="sm">
                {ex.code}
              </Text>
            </Table.Td>
            <Table.Td>{ex.lineNumber ?? 'header'}</Table.Td>
            <Table.Td>{ex.expected ?? '-'}</Table.Td>
            <Table.Td>{ex.actual ?? '-'}</Table.Td>
            <Table.Td>{ex.message}</Table.Td>
          </Table.Tr>
        ))}
      </Table.Tbody>
    </Table>
  )
}

function Info({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div>
      <Text size="xs" c="dimmed" tt="uppercase" fw={700}>
        {label}
      </Text>
      <Text>{children}</Text>
    </div>
  )
}
```

- [ ] **Step 6: Wire route and navigation**

`router.tsx` — add imports and two routes before the `'*'` route:

```tsx
import { InvoiceDetailPage } from './features/invoices/InvoiceDetailPage'
import { InvoicesPage } from './features/invoices/InvoicesPage'
// ...
      { path: 'invoices', Component: InvoicesPage },
      { path: 'invoices/:id', Component: InvoiceDetailPage },
```

`AppLayout.tsx` — add a section after "Purchasing":

```tsx
  {
    section: 'Accounts payable',
    items: [{ label: 'Supplier invoices', to: '/invoices' }],
  },
```

- [ ] **Step 7: Type-check and build**

Run: `cd frontend && npm run build`
Expected: build succeeds with no TypeScript errors.

- [ ] **Step 8: Commit**

```bash
git add frontend/src/features/invoices frontend/src/router.tsx frontend/src/layout/AppLayout.tsx
git commit -m "feat(ui): read-only supplier invoice list and match details"
```

---

### Task 6: Demo invoices in the seed script

**Files:**
- Modify: `scripts/seed-demo-data.mjs`

**Interfaces:**
- Consumes: `POST /api/invoices`, `GET /api/invoices`, `GET /api/purchase-orders`, `GET /api/purchase-orders/{id}`, `GET /api/suppliers`; demo POs identified by supplier number + status: SUP-1001/RECEIVED, SUP-1002/CLOSED, SUP-1003/PARTIALLY_RECEIVED, SUP-1004/APPROVED.

- [ ] **Step 1: Split `main` into two guarded parts**

Rename the existing body of `main()` (everything after the suppliers guard) into `async function seedMasterDataAndOrders()`, keeping the code unchanged. Replace `main` with:

```js
async function main() {
  if ((await api('GET', '/suppliers')).length === 0) await seedMasterDataAndOrders()
  else console.log('Suppliers exist - master data and purchase orders already seeded.')

  if ((await api('GET', '/invoices')).length === 0) await seedInvoices()
  else console.log('Invoices exist - nothing to do.')
}
```

Remove the old early-return guard inside the moved body.

- [ ] **Step 2: Add `seedInvoices`**

Append above `main`:

```js
// ---- invoices: one or more per matching rule, plus clean ones --------------------------------

async function seedInvoices() {
  console.log('Creating supplier invoices...')
  const suppliers = await api('GET', '/suppliers')
  const supplierId = (number) => suppliers.find((s) => s.supplierNumber === number).id
  const summaries = await api('GET', '/purchase-orders')
  const poOf = async (supplierNumber, status) => {
    const summary = summaries.find((o) => o.supplier.supplierNumber === supplierNumber && o.status === status)
    if (!summary) throw new Error(`Demo PO ${supplierNumber}/${status} not found - reseed the database`)
    return api('GET', `/purchase-orders/${summary.id}`)
  }
  const lineOf = (po, sku) => po.items.find((i) => i.sku === sku)

  const invoice = (supplierNumber, invoiceNumber, invoiceDate, currency, po, items) =>
    api('POST', '/invoices', {
      supplierId: supplierId(supplierNumber), invoiceNumber, invoiceDate, currency,
      purchaseOrderId: po?.id ?? null, items,
    })
  // Bill a PO line; overrides change what the supplier "printed".
  const bill = (po, sku, quantity, overrides = {}) => {
    const l = lineOf(po, sku)
    return {
      purchaseOrderItemId: l.id, description: l.description, quantity,
      unitPrice: l.unitPrice, taxRate: l.taxRate, ...overrides,
    }
  }

  const office = await poOf('SUP-1001', 'RECEIVED')
  const electronics = await poOf('SUP-1002', 'CLOSED')
  const packaging = await poOf('SUP-1003', 'PARTIALLY_RECEIVED')
  const imports = await poOf('SUP-1004', 'APPROVED')

  // Clean: everything delivered, billed at PO prices.
  await invoice('SUP-1001', 'MB-2026-0815', daysAgo(24), 'EUR', office, [
    bill(office, 'OFF-PAPER-A4', 50), bill(office, 'OFF-TONER-HP26X', 10), bill(office, 'OFF-PEN-BLUE', 5),
  ])
  // Clean: first truck of cartons.
  await invoice('SUP-1003', 'ALV-R-30117', daysAgo(3), 'EUR', packaging, [bill(packaging, 'PKG-CARTON-M', 1200)])
  // PRICE_MISMATCH: monitors billed 7 % above the PO price.
  await invoice('SUP-1002', 'SE-RE-77821', daysAgo(12), 'EUR', electronics, [
    bill(electronics, 'IT-MON-27', 10, { unitPrice: 298.53 }),
  ])
  // QUANTITY_MISMATCH: second carton invoice for goods that have not arrived yet.
  await invoice('SUP-1003', 'ALV-R-30188', daysAgo(2), 'EUR', packaging, [bill(packaging, 'PKG-CARTON-M', 800)])
  // TAX_MISMATCH: keyboards invoiced with 7 % instead of 19 % VAT.
  await invoice('SUP-1002', 'SE-RE-77840', daysAgo(11), 'EUR', electronics, [
    bill(electronics, 'IT-KB-DE', 20, { taxRate: 7 }),
  ])
  // UNMATCHED_LINE: pallets plus a freight charge that was never ordered.
  await invoice('SUP-1003', 'ALV-R-30120', daysAgo(3), 'EUR', packaging, [
    bill(packaging, 'PKG-PALLET-EUR', 40),
    { purchaseOrderItemId: null, description: 'Freight / Fracht', quantity: 1, unitPrice: 85, taxRate: 19 },
  ])
  // CURRENCY_MISMATCH + QUANTITY_MISMATCH: USD order invoiced in EUR before anything shipped.
  await invoice('SUP-1004', 'PC-INV-5521', daysAgo(1), 'EUR', imports, [
    bill(imports, 'IT-CABLE-CAT6', 915), bill(imports, 'IT-MON-27', 25),
  ])
  // MISSING_PO + DUPLICATE_INVOICE: cleaning supplies without PO, sent twice.
  const cleaner = { purchaseOrderItemId: null, description: 'All-purpose cleaner', quantity: 100, unitPrice: 3.4, taxRate: 19 }
  await invoice('SUP-1005', 'WR-2026-114', daysAgo(5), 'EUR', null, [cleaner])
  await invoice('SUP-1005', 'WR-2026-114', daysAgo(1), 'EUR', null, [cleaner])

  const invoices = await api('GET', '/invoices')
  const flagged = invoices.filter((i) => i.exceptionCount > 0).length
  console.log(`Done: ${invoices.length} invoices, ${invoices.length - flagged} matched, ${flagged} with exceptions.`)
}
```

- [ ] **Step 3: Run against the running backend (restart it first so Flyway applies V6)**

Run: `node scripts/seed-demo-data.mjs`
Expected output ends with: `Done: 9 invoices, 2 matched, 7 with exceptions.`

- [ ] **Step 4: Verify every rule fires**

Run:

```bash
for id in $(curl -s localhost:8080/api/invoices | node -e "let d='';process.stdin.on('data',c=>d+=c).on('end',()=>console.log(JSON.parse(d).map(i=>i.id).join(' ')))"); do
  curl -s localhost:8080/api/invoices/$id/match | node -e "let d='';process.stdin.on('data',c=>d+=c).on('end',()=>console.log('$id', JSON.parse(d).exceptions.map(e=>e.code).join(',')||'MATCHED'))"
done
```

Expected: across all lines, each of `DUPLICATE_INVOICE, MISSING_PO, CURRENCY_MISMATCH, UNMATCHED_LINE, PRICE_MISMATCH, QUANTITY_MISMATCH, TAX_MISMATCH` appears at least once, and exactly two lines say `MATCHED`.

- [ ] **Step 5: Commit**

```bash
git add scripts/seed-demo-data.mjs
git commit -m "chore(seed): demo supplier invoices covering every matching rule"
```

---

### Task 7: Docs

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Update the status table**

Replace the two "planned" rows:

```markdown
| Supplier invoices | ✅ | ✅ (read-only) |
| Three-way invoice matching | ✅ | ✅ |
```

- [ ] **Step 2: Mention the seed script under "Getting started"** — after the frontend block add:

```markdown
# 4. Optional demo data (backend must be running)
node scripts/seed-demo-data.mjs
```

- [ ] **Step 3: Commit**

```bash
git add README.md
git commit -m "docs: M1 status and demo data"
```
