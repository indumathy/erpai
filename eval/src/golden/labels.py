"""Label format of the golden dataset (one JSON file per invoice) and the manifest.

These models are the data contract between the generator, the dataset checks and — from M3 on — the
extraction service, whose LLM output must validate against InvoiceFields.
"""

from datetime import date, datetime
from decimal import Decimal
from enum import StrEnum
from typing import Literal

# pydantic = validated data classes (Kotlin: data class + Jackson + Bean Validation in one).
# BaseModel parses JSON/dicts into typed objects and raises ValidationError on bad input.
from pydantic import BaseModel, ConfigDict, Field, model_validator

from golden.money import line_net


class Strict(BaseModel):
    """Immutable, and unknown keys are an error (a typo in a label must not pass silently)."""

    # frozen=True  -> like Kotlin `val` only
    # extra="forbid" -> like Jackson FAIL_ON_UNKNOWN_PROPERTIES = true
    model_config = ConfigDict(frozen=True, extra="forbid")


# StrEnum: each member is also a plain string, so it serialises to JSON as "PRICE_MISMATCH".
# Same 7 codes as MatchExceptionCode in the Kotlin matcher.
class ExceptionCode(StrEnum):
    DUPLICATE_INVOICE = "DUPLICATE_INVOICE"
    MISSING_PO = "MISSING_PO"
    CURRENCY_MISMATCH = "CURRENCY_MISMATCH"
    UNMATCHED_LINE = "UNMATCHED_LINE"
    PRICE_MISMATCH = "PRICE_MISMATCH"
    QUANTITY_MISMATCH = "QUANTITY_MISMATCH"
    TAX_MISMATCH = "TAX_MISMATCH"


# ---- E1: your exercise ----------------------------------------------------------------------
#
# Write the three models below. Field names and types:
#
#   LineFields     description: str, quantity: Decimal, unit_price: Decimal, tax_rate: Decimal,
#                  net_amount: Decimal
#                  rule: net_amount == line_net(quantity, unit_price)
#   Totals         net: Decimal, tax: Decimal, gross: Decimal
#                  rule: gross == net + tax
#   InvoiceFields  supplier_name: str, invoice_number: str, invoice_date: date,
#                  currency: str (exactly three capital letters), po_number: str | None,
#                  lines: list[LineFields] (at least one), totals: Totals
#                  rule: totals.net == sum of the lines' net_amount
#
# Inherit from Strict. tests/test_labels.py tells you when you are done.


class LineFields(Strict):
    """One invoice line as printed on the PDF."""

    # Plain typed fields: pydantic turns the JSON string "4.15" into Decimal("4.15").
    description: str
    quantity: Decimal
    unit_price: Decimal
    tax_rate: Decimal
    net_amount: Decimal

    # Cross-field rule, runs after all fields are parsed (Kotlin: init { require(...) }).
    @model_validator(mode="after")
    def net_is_quantity_times_price(self) -> "LineFields":
        # line_net rounds half-up to cents, exactly like the ERP's DocumentTotals.lineNet.
        expected = line_net(self.quantity, self.unit_price)
        if self.net_amount != expected:
            # pydantic wraps this ValueError into a ValidationError for the caller.
            raise ValueError(f"net_amount {self.net_amount} != quantity x unit_price = {expected}")
        return self


class Totals(Strict):
    """The invoice's footer amounts."""

    net: Decimal
    tax: Decimal
    gross: Decimal

    @model_validator(mode="after")
    def gross_is_net_plus_tax(self) -> "Totals":
        # No rounding needed: net and tax are already in cents, so the sum is exact.
        if self.gross != self.net + self.tax:
            raise ValueError(f"gross {self.gross} != net {self.net} + tax {self.tax}")
        return self


class InvoiceFields(Strict):
    """Everything the extractor (M3) must read from one PDF."""

    supplier_name: str
    invoice_number: str
    invoice_date: date  # "2026-03-14" -> date(2026, 3, 14)
    currency: str = Field(pattern=r"^[A-Z]{3}$")  # ISO code like EUR (Kotlin: @Pattern)
    po_number: str | None  # None = invoice without purchase order
    lines: list[LineFields] = Field(min_length=1)  # nested models are validated too (Kotlin: @Valid @Size(min = 1))
    totals: Totals

    @model_validator(mode="after")
    def net_is_sum_of_lines(self) -> "InvoiceFields":
        # Generator expression inside sum() (Kotlin: lines.sumOf { it.netAmount }).
        # Start value Decimal(0): sum() would otherwise start with the int 0.
        lines_net = sum((line.net_amount for line in self.lines), Decimal(0))
        if self.totals.net != lines_net:
            raise ValueError(f"totals.net {self.totals.net} != sum of line nets {lines_net}")
        return self


# ---- prepared models ------------------------------------------------------------------------


class Intent(Strict):
    """An exception the scenario intends to cause (line None = header-level)."""

    code: ExceptionCode
    line: int | None = None


class ExpectedException(Strict):
    """Verbatim copy of one entry of the ERP's /match response (hence camelCase lineNumber)."""

    code: ExceptionCode
    lineNumber: int | None
    expected: str | None
    actual: str | None
    message: str


class ErpRefs(Strict):
    """Database ids in the golden ERP, so a label can be traced back to its ERP records."""

    invoice_id: int
    purchase_order_id: int | None
    line_po_item_ids: list[int | None]


class Label(Strict):
    """One JSON file per invoice: what the PDF shows + what the ERP matcher must report."""

    id: str = Field(pattern=r"^inv-\d{4}$")  # regex check, like @Pattern in Bean Validation
    split: Literal["dev", "test"] | None = None  # Literal = only these exact values allowed
    template: str
    scenario: str
    group: str  # invoices of one scenario (e.g. a duplicate pair) share a group -> same split
    pdf: str
    fields: InvoiceFields  # the answer key for extraction (M3)
    intent: list[Intent]  # what the scenario meant to cause
    expected_exceptions: list[ExpectedException]  # what the ERP actually reported
    erp: ErpRefs


class Manifest(Strict):
    """dataset/v1/manifest.json: how and when the dataset was generated, and the dev/test split."""

    dataset_version: str
    seed: int
    generated_at: datetime
    erp_commit: str
    generator_version: str
    counts: dict[str, int]
    splits: dict[str, list[str]]
