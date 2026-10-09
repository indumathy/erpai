# M1 — Supplier invoices and three-way matching (design)

Status: approved in chat on 2026-10-06 · Roadmap: [ROADMAP.md](../../../ROADMAP.md) Stage 0

## Purpose

M1 is not about a richer ERP. It produces the **deterministic ground truth** that every later AI milestone
is measured against: the M2 golden dataset, M3 extraction, M7 explanations and the M10 agent.
Rule of thumb for every decision below: *build it only if a later AI milestone needs it.*

## Scope

In scope:

- Supplier invoices with lines, optionally linked to a purchase order and its lines.
- A pure, deterministic matching function that compares invoice ↔ purchase order ↔ goods receipts.
- REST API to create, list and read invoices and to get their match result.
- Read-only UI: invoice list and invoice detail with match exceptions.
- Seed data: invoices that trigger every rule, plus clean ones.

Out of scope (deferred to the milestone that needs it):

| Deferred | Until |
|---|---|
| Supplier contracts, contract price rule | M6 (with contract RAG) |
| Invoice status lifecycle, approval / rejection | M10 (human-in-the-loop) |
| Invoice entry / review form in the UI | M3 (review of extracted invoices) |
| Stored match results and history | M10 / M15 if needed |
| Configurable tolerances | M14 or later |
| Invoices spanning several purchase orders | not planned |

## Data model

One Flyway migration `V6__create_supplier_invoice.sql`.

`supplier_invoice`

| Column | Type | Notes |
|---|---|---|
| id | BIGINT identity | |
| supplier_id | BIGINT NOT NULL → supplier | |
| invoice_number | VARCHAR(64) NOT NULL | the supplier's number as printed; **not unique** — duplicates must be storable so the rule can detect them |
| invoice_date | DATE NOT NULL | |
| currency | VARCHAR(3) NOT NULL | ISO 4217 check like other tables |
| purchase_order_id | BIGINT NULL → purchase_order | null = invoice without PO reference |
| created_at, updated_at | TIMESTAMPTZ | via `BaseEntity` |

`supplier_invoice_item`

| Column | Type | Notes |
|---|---|---|
| id | BIGINT identity | |
| supplier_invoice_id | BIGINT NOT NULL → supplier_invoice ON DELETE CASCADE | |
| line_number | INTEGER NOT NULL | unique per invoice |
| purchase_order_item_id | BIGINT NULL → purchase_order_item | null = line not linked to a PO line |
| description | VARCHAR(500) NOT NULL | |
| quantity | NUMERIC(19,3) NOT NULL, > 0 | |
| unit_price | NUMERIC(19,4) NOT NULL, ≥ 0 | |
| tax_rate | NUMERIC(5,2) NOT NULL, 0–100 | |

Creation-time validation (rejected requests, not match exceptions), because these are data-entry errors rather
than business discrepancies. Following the existing convention: unknown supplier / PO → 404, rule violations
via `requireRule` → 422, bean-validation errors → 400:

- supplier must exist; if a PO is given, it must belong to the same supplier;
- a line may only reference a PO line of the invoice's own PO, and each PO line at most once per invoice;
- a line may reference a PO line only if the invoice has a PO;
- invoice date must not lie in the future; at least one line.

Totals reuse `DocumentTotals` / `MoneyRounding`, so invoice and PO amounts use identical arithmetic.

## Matching engine

Package `com.example.erp.invoice.matching`. A **pure function** — no Spring, no JPA, no clock:

```kotlin
object InvoiceMatcher {
    fun match(input: MatchInput): List<MatchException>
}
```

Input is a plain data snapshot built by the service from entities:

```kotlin
data class MatchInput(
    val invoice: InvoiceSnapshot,           // header + lines
    val purchaseOrder: PurchaseOrderSnapshot?,  // null when the invoice has no PO
    val duplicateInvoiceIds: List<Long>,    // other invoices, same supplier + invoice number
)
// PurchaseOrderSnapshot lines carry: id, lineNumber, sku, quantity, unitPrice, taxRate,
// receivedQuantity, and alreadyInvoicedQuantity (sum over EARLIER invoices — lower id — for that PO line,
// so only the invoice that over-bills is flagged, not the legitimate first one).
```

Output:

```kotlin
data class MatchException(
    val code: MatchExceptionCode,
    val lineNumber: Int?,      // invoice line, null for header-level exceptions
    val expected: String?,     // e.g. "42.0000"
    val actual: String?,       // e.g. "45.0000"
    val message: String,       // human-readable, deterministic
)
```

An empty list means **matched**.

### Rules

| Code | Level | Fires when |
|---|---|---|
| `DUPLICATE_INVOICE` | header | `duplicateInvoiceIds` is not empty (same supplier, same invoice number after trim + case-insensitive compare). Both the original and the copy are flagged — the matcher cannot know which one is "real"; deciding that is a human (later: agent) task. |
| `MISSING_PO` | header | invoice has no purchase order. No line rules run in this case. |
| `CURRENCY_MISMATCH` | header | invoice currency ≠ PO currency. Price rule is skipped (amounts not comparable). |
| `UNMATCHED_LINE` | line | invoice has a PO but the line references no PO line (e.g. freight) |
| `PRICE_MISMATCH` | line | \|invoice price − PO price\| / PO price > **0.5 %**; if PO price is 0, any non-zero invoice price |
| `QUANTITY_MISMATCH` | line | `alreadyInvoicedQuantity + this line's quantity > receivedQuantity` (billed for more than arrived, cumulative over earlier invoices); no tolerance |
| `TAX_MISMATCH` | line | invoice tax rate ≠ PO tax rate (exact) |

Order of the returned list: header exceptions first (in table order), then line exceptions by line number,
then by table order. Deterministic ordering makes test assertions and later eval comparisons stable.

Tolerances are named constants in `InvoiceMatcher` (`PRICE_TOLERANCE_PERCENT = 0.5`).

## API

| Method | Path | Result |
|---|---|---|
| POST | `/api/invoices` | 201, invoice with lines and totals |
| GET | `/api/invoices` | list rows: id, invoice number, supplier, PO number, date, currency, total, `exceptionCount` |
| GET | `/api/invoices/{id}` | invoice with lines and totals |
| GET | `/api/invoices/{id}/match` | `{ matched: Boolean, exceptions: [MatchException] }` |

`exceptionCount` in the list is computed by running the matcher per invoice — acceptable at demo data volume.

## UI

Feature folder `frontend/src/features/invoices`, following the existing features:

- `InvoicesPage` — table with a matched ✓ / "n exceptions" badge; link to detail.
- `InvoiceDetailPage` — header, lines, totals, and the exception list (code, line, expected, actual, message).
- Navigation entry "Invoices". No create/edit form.

## Seed data

`scripts/seed-demo-data.mjs` gains ~8 invoices against the existing demo POs: clean matches for the fully
received POs, plus at least one invoice per rule (price +7 %, billed more than received, wrong tax rate,
freight line without PO line, USD invoice for EUR PO, no PO reference, duplicate invoice number).
The script gets two independent guards: master data + POs are seeded only if no suppliers exist (unchanged),
invoices are seeded only if no invoices exist and looked up against the existing demo POs by supplier number
and SKU. That way an already-seeded database gets the invoices by simply re-running the script.

## Testing

- `InvoiceMatcherTest` — pure unit tests, at least one positive and one negative case per rule, plus
  tolerance boundaries (exactly 0.5 % passes, above fails) and the "no PO → only `MISSING_PO`" case.
  **Written first; the user implements the rule bodies against them.**
- `SupplierInvoiceApiTest` — Testcontainers integration test: create (incl. 400 cases for the validation
  above), list, get, match.

## Division of work

Claude prepares migration, entities, repository, service, controller, DTOs, UI, seed data, the matcher's
types and signature, and all tests. The user implements the rule logic inside `InvoiceMatcher.match`.
