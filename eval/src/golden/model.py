"""Plain data describing what the generator creates. No I/O and no ERP ids in here.

Three groups of classes:
  1. Master data   - Party, Product, Supplier        (who sells what)
  2. Scenario spec - PoLine, Receipt, InvLine, Intent, InvoiceSpec, ScenarioSpec
                     (the "story" we push into the ERP: order -> receipt -> invoice)
  3. Printed PDF   - PrintedLine, PrintedInvoice     (exactly what one PDF shows)
"""

# @dataclass generates __init__, __eq__, __repr__ (Kotlin: `data class`).
# frozen=True makes instances immutable (Kotlin: all `val`, no `var`).
from dataclasses import dataclass
from datetime import date  # Kotlin: java.time.LocalDate
from decimal import Decimal  # Kotlin: BigDecimal

# Order of MatchExceptionCode in the Kotlin matcher (header codes first, then line codes).
# A tuple is an immutable list (Kotlin: listOf(...)).
CODES = (
    "DUPLICATE_INVOICE",
    "MISSING_PO",
    "CURRENCY_MISMATCH",
    "UNMATCHED_LINE",
    "PRICE_MISMATCH",
    "QUANTITY_MISMATCH",
    "TAX_MISMATCH",
)


# ---------- 1. Master data ----------


@dataclass(frozen=True)
class Party:
    """A company with an address: a supplier or the buyer."""

    name: str
    street: str
    city: str
    vat_id: str


@dataclass(frozen=True)
class Product:
    sku: str
    name: str
    unit_price: Decimal
    tax_rate: Decimal  # percent, e.g. Decimal("19")


@dataclass(frozen=True)
class Supplier:
    key: str  # = template key (which PDF layout this supplier uses)
    number: str  # ERP supplier number
    party: Party
    currency: str
    invoice_format: str  # e.g. "NB-2026-{:04d}" -> "NB-2026-0007"
    # tuple[Product, ...] = an immutable list of any length (Kotlin: List<Product>).
    products: tuple[Product, ...]


# ---------- 2. Scenario spec (the story we create in the ERP) ----------


@dataclass(frozen=True)
class PoLine:
    """One line of the purchase order."""

    sku: str
    quantity: Decimal
    unit_price: Decimal
    tax_rate: Decimal


@dataclass(frozen=True)
class Receipt:
    """Goods received for the PO."""

    receipt_date: date
    quantities: tuple[Decimal, ...]  # one per PO line; 0 = nothing received for that line


@dataclass(frozen=True)
class InvLine:
    """One line of the supplier invoice."""

    # `int | None` = nullable (Kotlin: Int?).
    po_line: int | None  # 1-based PO line number; None = not linked (freight, invoice without PO)
    description: str
    quantity: Decimal
    unit_price: Decimal
    tax_rate: Decimal


@dataclass(frozen=True)
class Intent:
    """An exception the scenario *intends* to cause, e.g. PRICE_MISMATCH on line 2.

    Later a test checks: intent == what the ERP matcher actually returns.
    """

    code: str
    line: int | None = None  # invoice line number; None = header-level (default value, like Kotlin)

    def sort_key(self) -> tuple[int, int]:
        """The matcher's output order: header first, then by line, then by code order."""
        # `self` = Kotlin `this`, but written explicitly as first parameter.
        # `self.line or 0` = Kotlin `line ?: 0`. Tuples compare element by element.
        return (self.line or 0, CODES.index(self.code))


@dataclass(frozen=True)
class InvoiceSpec:
    invoice_number: str
    invoice_date: date
    currency: str
    with_po: bool  # False = invoice that references no purchase order
    lines: tuple[InvLine, ...]
    intent: tuple[Intent, ...] = ()  # () = empty tuple -> a clean invoice


@dataclass(frozen=True)
class ScenarioSpec:
    """One complete test case: PO + receipts + one or more invoices (two for duplicates)."""

    scenario: str  # e.g. "price_mismatch"
    template: str  # which supplier / PDF layout
    order_date: date
    po_lines: tuple[PoLine, ...]  # empty = no purchase order at all
    receipts: tuple[Receipt, ...]
    invoices: tuple[InvoiceSpec, ...]


# ---------- 3. Printed PDF ----------


@dataclass(frozen=True)
class PrintedLine:
    description: str
    quantity: Decimal
    unit_price: Decimal
    tax_rate: Decimal
    net_amount: Decimal


@dataclass(frozen=True)
class PrintedInvoice:
    """Exactly what one PDF shows. Built from the ERP's invoice response, rendered by a template."""

    template: str
    supplier: Party
    buyer: Party
    invoice_number: str
    invoice_date: date
    currency: str
    po_number: str | None
    lines: tuple[PrintedLine, ...]
    net: Decimal
    tax: Decimal
    gross: Decimal
    tax_breakdown: tuple[tuple[Decimal, Decimal, Decimal], ...]  # (rate, net, tax), highest rate first
