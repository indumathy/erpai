# M2 Golden Dataset — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Generate a committed golden dataset of 200 synthetic supplier-invoice PDFs with labels whose expected exceptions come from the M1 ERP matcher.

**Architecture:** A uv project in `eval/`. Pure modules (`model`, `suppliers`, `scenarios`, `split`, `labels`) describe *what* to generate; `erp_client` creates the data through the ERP REST API (a separate database `erpai_golden`, backend on port 8081); `templates` render PDFs with reportlab; `generate` orchestrates and writes `eval/dataset/v1/` atomically. Pytest validates both the code and the finished dataset.

**Tech Stack:** Python ≥ 3.12 (uv), httpx, Pydantic v2, reportlab, pdfplumber, pytest. ERP: Kotlin/Spring backend from M1 (JDK 22), PostgreSQL 17 in Docker.

**Spec:** `docs/superpowers/specs/2026-10-10-m2-golden-dataset-design.md`

## Working agreement (overrides the generic skill steps)

- **The user does all git.** Claude never runs `git add/commit/push/branch`. Every "Commit" step below means: *stop, list the changed files, and suggest the commit message to the user.*
- Work happens on branch `m2-golden-dataset` (created by the user).
- **USER EXERCISE** tasks: Claude prepares the files exactly as shown (skeleton + tests), then hands over with the hints. Claude does not fill in the TODOs unless the user asks; a reference solution is in a `<details>` block for reviewing the user's code.
- Shell is Git Bash on Windows. All `uv` commands run from `eval/`.

## Global Constraints

- Seed `42`; all dates fixed between `2026-01-05` and `2026-09-30`, never relative to today.
- Exactly 200 invoices: 80 clean, 96 single-exception, 24 combinations; every exception code ≥ 10 invoices.
- Dev/test split 50 ± 5 / 150 ± 5; all invoices of a `group` share a split.
- Amounts, quantities, prices and rates are `Decimal` in Python and strings in JSON — never `float`.
- `fields` holds only what is printed on the PDF, in canonical form (ISO date, `.` decimal separator, no grouping, no currency symbol).
- `expected_exceptions` is copied verbatim from `GET /api/invoices/{id}/match`.
- The generator uses only the REST API and refuses to run if the ERP already has invoices.
- The ERP for generation runs on database `erpai_golden` in container `erpai-postgres` (port 5433), backend on port 8081; the normal ERP database is never touched.
- Dataset output: `eval/dataset/v1/` with `manifest.json`, `README.md`, `invoices/inv-NNNN.pdf`, `labels/inv-NNNN.json`.

## Review Focus

1. Re-running the generator against an ERP that already has invoices must abort **before** writing anything — pinned in Task 9 (`test_refuses_non_empty_erp`).
2. An ERP error in the middle of a run must leave an existing `dataset/v1/` untouched — pinned in Task 9 (`test_failed_run_keeps_previous_dataset`).
3. Regenerating with the same seed must produce identical PDFs and labels (no timestamps/random ids inside PDFs) — pinned in Task 7 (`test_render_is_byte_identical`) and Task 6 (`test_catalog_is_deterministic`).
4. Numbers with thousands separators and 4-decimal unit prices (`1234.5`, `12.4017`) must print exactly per template — pinned in Task 7 (`test_number_formatting`).
5. ERP JSON numbers like `21.9000` must never pass through `float` — pinned in Task 3 (`test_response_numbers_are_decimals`).

---

## File structure

| File | Responsibility | Built by |
|---|---|---|
| `eval/pyproject.toml` | uv project, deps, pytest config | Claude |
| `eval/README.md` | how to regenerate, run checks, dataset layout | Claude |
| `eval/scripts/golden-erp.sh` | recreate `erpai_golden`, start backend on 8081 | Claude |
| `eval/src/golden/__init__.py` | package marker | Claude |
| `eval/src/golden/money.py` | Decimal rounding identical to the ERP | Claude |
| `eval/src/golden/model.py` | plain dataclasses: suppliers, scenarios, printed invoice | Claude |
| `eval/src/golden/suppliers.py` | the 5 suppliers, their products, the buyer | Claude |
| `eval/src/golden/labels.py` | Pydantic label + manifest models | Claude + **user (E1)** |
| `eval/src/golden/erp_client.py` | httpx wrapper around the ERP API | Claude |
| `eval/src/golden/scenarios.py` | building blocks, scenario functions, catalog | Claude + **user (E2)** |
| `eval/src/golden/split.py` | stratified dev/test split | **user (E3)** |
| `eval/src/golden/templates/` | `formats.py`, `base.py`, 5 style modules, `__init__.py` | Claude |
| `eval/src/golden/generate.py` | orchestration, atomic write | Claude |
| `eval/src/golden/card.py` | dataset card (README.md) | Claude |
| `eval/tests/*.py` | unit tests + dataset checks | Claude + **user (E4)** |

---

### Task 1: Project scaffold, money helpers, model and suppliers

**Files:**
- Create: `eval/pyproject.toml`, `eval/src/golden/__init__.py`, `eval/src/golden/money.py`, `eval/src/golden/model.py`, `eval/src/golden/suppliers.py`
- Create: `eval/.gitignore`
- Test: `eval/tests/test_money.py`, `eval/tests/test_suppliers.py`

**Interfaces:**
- Produces: `money.amount(Decimal) -> Decimal`, `money.line_net(q, p) -> Decimal`, `money.plain(Decimal) -> str`; dataclasses `Party, Product, Supplier, PoLine, Receipt, InvLine, Intent(code, line=None).sort_key(), InvoiceSpec, ScenarioSpec, PrintedLine, PrintedInvoice`; constant `CODES`; `suppliers.SUPPLIERS: dict[str, Supplier]`, `suppliers.TEMPLATE_KEYS: tuple[str, ...]`, `suppliers.BUYER: Party`.

- [ ] **Step 1: Create the project files**

`eval/pyproject.toml`:

```toml
[project]
name = "erpai-eval"
version = "0.1.0"
description = "Golden dataset generator and evaluation data for erpai"
requires-python = ">=3.12"
dependencies = [
    "httpx>=0.27",
    "pdfplumber>=0.11",
    "pydantic>=2.9",
    "reportlab>=4.2",
]

[dependency-groups]
dev = [
    "pytest>=8.3",
]

# Kotlin comparison: like a Gradle module with the `application` plugin. hatchling builds the
# `golden` package from src/, and `uv sync` installs it in editable mode, so
# `uv run python -m golden.generate` works from eval/.
[build-system]
requires = ["hatchling"]
build-backend = "hatchling.build"

[tool.hatch.build.targets.wheel]
packages = ["src/golden"]

[tool.pytest.ini_options]
testpaths = ["tests"]
```

`eval/.gitignore`:

```gitignore
.venv/
__pycache__/
.pytest_cache/
dataset/.golden-*/
```

`eval/src/golden/__init__.py`:

```python
"""Golden dataset generator for erpai (M2)."""
```

- [ ] **Step 2: Write the failing tests**

`eval/tests/test_money.py`:

```python
from decimal import Decimal

from golden.money import amount, line_net, plain


def test_amount_rounds_half_up_to_cents():
    assert amount(Decimal("1.005")) == Decimal("1.01")
    assert amount(Decimal("1.004")) == Decimal("1.00")
    assert str(amount(Decimal("7"))) == "7.00"


def test_line_net_matches_erp_document_totals():
    # DocumentTotals.lineNet: quantity x unit price, rounded to cents (half-up)
    assert line_net(Decimal("3"), Decimal("0.335")) == Decimal("1.01")
    assert line_net(Decimal("50"), Decimal("4.18")) == Decimal("209.00")


def test_plain_strips_scale_without_scientific_notation():
    assert plain(Decimal("50.000")) == "50"
    assert plain(Decimal("21.9000")) == "21.9"
    assert plain(Decimal("100")) == "100"
    assert plain(Decimal("0.50")) == "0.5"
```

`eval/tests/test_suppliers.py`:

```python
from decimal import Decimal

from golden.suppliers import BUYER, SUPPLIERS, TEMPLATE_KEYS


def test_one_supplier_per_template():
    assert set(SUPPLIERS) == set(TEMPLATE_KEYS)
    assert len(TEMPLATE_KEYS) == 5
    assert all(s.key == key for key, s in SUPPLIERS.items())


def test_prices_have_even_cents():
    # Even cents make ±0.5 % exactly representable with 4 decimals (price_at_tolerance near misses).
    for supplier in SUPPLIERS.values():
        for p in supplier.products:
            cents = p.unit_price * 100
            assert cents == cents.to_integral_value() and cents % 2 == 0, p.sku


def test_names_fit_on_one_table_line():
    for supplier in SUPPLIERS.values():
        for p in supplier.products:
            assert len(p.name) <= 38, p.name


def test_skus_are_unique_and_valid_for_the_erp():
    skus = [p.sku for s in SUPPLIERS.values() for p in s.products]
    assert len(skus) == len(set(skus))
    assert all(sku.replace("-", "").isalnum() for sku in skus)


def test_bilingual_supplier_can_fill_long_invoices():
    assert len(SUPPLIERS["bilingual"].products) >= 16


def test_currencies_and_buyer():
    assert {s.currency for s in SUPPLIERS.values()} == {"EUR", "USD", "GBP"}
    assert BUYER.name
    assert all(isinstance(p.tax_rate, Decimal) for s in SUPPLIERS.values() for p in s.products)
```

- [ ] **Step 3: Run them to verify they fail**

Run: `cd eval && uv sync && uv run pytest -q`
Expected: collection errors — `ModuleNotFoundError: No module named 'golden.money'`.

- [ ] **Step 4: Implement `money.py`, `model.py`, `suppliers.py`**

`eval/src/golden/money.py`:

```python
"""Decimal helpers that round exactly like the ERP (backend shared/money: MoneyRounding, DocumentTotals)."""

from decimal import ROUND_HALF_UP, Decimal

CENT = Decimal("0.01")


def amount(value: Decimal) -> Decimal:
    """Round to cents, half-up — MoneyRounding.amount."""
    return value.quantize(CENT, rounding=ROUND_HALF_UP)


def line_net(quantity: Decimal, unit_price: Decimal) -> Decimal:
    """Quantity x unit price, rounded to cents — DocumentTotals.lineNet."""
    return amount(quantity * unit_price)


def plain(value: Decimal) -> str:
    """42.0000 -> '42', 0.50 -> '0.5' (the Kotlin `.plain()` helper); never scientific notation."""
    return format(value.normalize(), "f")
```

`eval/src/golden/model.py`:

```python
"""Plain data describing what the generator creates. No I/O and no ERP ids in here."""

from dataclasses import dataclass
from datetime import date
from decimal import Decimal

# Order of MatchExceptionCode in the Kotlin matcher (header codes first, then line codes).
CODES = (
    "DUPLICATE_INVOICE",
    "MISSING_PO",
    "CURRENCY_MISMATCH",
    "UNMATCHED_LINE",
    "PRICE_MISMATCH",
    "QUANTITY_MISMATCH",
    "TAX_MISMATCH",
)


@dataclass(frozen=True)
class Party:
    name: str
    street: str
    city: str
    vat_id: str


@dataclass(frozen=True)
class Product:
    sku: str
    name: str
    unit_price: Decimal
    tax_rate: Decimal


@dataclass(frozen=True)
class Supplier:
    key: str  # = template key
    number: str  # ERP supplier number
    party: Party
    currency: str
    invoice_format: str  # e.g. "NB-2026-{:04d}"
    products: tuple[Product, ...]


@dataclass(frozen=True)
class PoLine:
    sku: str
    quantity: Decimal
    unit_price: Decimal
    tax_rate: Decimal


@dataclass(frozen=True)
class Receipt:
    receipt_date: date
    quantities: tuple[Decimal, ...]  # one per PO line; 0 = nothing received for that line


@dataclass(frozen=True)
class InvLine:
    po_line: int | None  # 1-based PO line number; None = not linked (freight, invoice without PO)
    description: str
    quantity: Decimal
    unit_price: Decimal
    tax_rate: Decimal


@dataclass(frozen=True)
class Intent:
    code: str
    line: int | None = None  # invoice line number; None = header-level

    def sort_key(self) -> tuple[int, int]:
        """The matcher's output order: header first, then by line, then by code order."""
        return (self.line or 0, CODES.index(self.code))


@dataclass(frozen=True)
class InvoiceSpec:
    invoice_number: str
    invoice_date: date
    currency: str
    with_po: bool
    lines: tuple[InvLine, ...]
    intent: tuple[Intent, ...] = ()


@dataclass(frozen=True)
class ScenarioSpec:
    scenario: str
    template: str
    order_date: date
    po_lines: tuple[PoLine, ...]  # empty = no purchase order at all
    receipts: tuple[Receipt, ...]
    invoices: tuple[InvoiceSpec, ...]


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
```

`eval/src/golden/suppliers.py`:

```python
"""The five fictional suppliers of the golden dataset — one per PDF template — and the buyer."""

from decimal import Decimal

from golden.model import Party, Product, Supplier

BUYER = Party("erpai Demo GmbH", "Musterstraße 1", "20095 Hamburg, Germany", "DE123456789")


def _p(sku: str, name: str, price: str, tax: str) -> Product:
    return Product(sku, name, Decimal(price), Decimal(tax))


SUPPLIERS: dict[str, Supplier] = {
    s.key: s
    for s in (
        Supplier(
            "nordbuero_de", "GS-NB",
            Party("Nordbüro GmbH", "Speicherstraße 12", "28195 Bremen", "DE281947365"),
            "EUR", "NB-2026-{:04d}",
            (
                _p("NB-PAPIER-A4", "Kopierpapier A4, 500 Blatt", "4.18", "19"),
                _p("NB-TONER-26X", "Toner HP 26X schwarz", "89.50", "19"),
                _p("NB-ORDNER-80", "Ordner A4, 80 mm, blau", "2.36", "19"),
                _p("NB-KULI-BL", "Kugelschreiber blau, 10 Stück", "3.90", "19"),
                _p("NB-HEFT-A5", "Notizheft A5 kariert", "1.24", "19"),
                _p("NB-FACHBUCH", "Fachbuch Buchhaltung 2026", "39.90", "7"),
            ),
        ),
        Supplier(
            "acme_us", "GS-ACME",
            Party("Acme Industrial Supply Inc.", "1200 Harbor Blvd", "Oakland, CA 94607, USA", "US94-1234567"),
            "USD", "INV-{:06d}",
            (
                _p("AC-BOLT-M8", "Hex bolt M8x40, box of 100", "12.48", "0"),
                _p("AC-NUT-M8", "Hex nut M8, box of 100", "6.24", "0"),
                _p("AC-GLOVE-L", "Work gloves, size L", "3.86", "0"),
                _p("AC-TAPE-48", "Packing tape 48 mm x 66 m", "2.14", "0"),
                _p("AC-DRILL-18V", "Cordless drill 18V", "129.00", "0"),
                _p("AC-SAFETY-GL", "Safety glasses, clear", "5.62", "0"),
            ),
        ),
        Supplier(
            "techsource_uk", "GS-TS",
            Party("TechSource Ltd", "45 Kingsway", "London WC2B 6TE, United Kingdom", "GB123456789"),
            "GBP", "TS/26/{:05d}",
            (
                _p("TS-MON-27", "27in monitor, QHD", "218.00", "20"),
                _p("TS-KB-UK", "Keyboard, UK layout", "24.90", "20"),
                _p("TS-MOUSE", "Wireless mouse", "14.36", "20"),
                _p("TS-DOCK-USBC", "USB-C docking station", "112.50", "20"),
                _p("TS-CABLE-HDMI", "HDMI cable 2 m", "6.98", "20"),
                _p("TS-HEADSET", "Headset with microphone", "39.98", "20"),
            ),
        ),
        Supplier(
            "alpenpack_de", "GS-AP",
            Party("Alpenpack Verpackungen GmbH", "Industriestraße 7", "83022 Rosenheim", "DE314159265"),
            "EUR", "AP-R-{:05d}",
            (
                _p("AP-KARTON-M", "Faltkarton 400x300x200 mm", "0.86", "19"),
                _p("AP-KARTON-L", "Faltkarton 600x400x400 mm", "1.42", "19"),
                _p("AP-PALETTE", "Europalette 1200x800 mm", "14.20", "19"),
                _p("AP-FOLIE-23", "Stretchfolie 23 my, 300 m", "9.64", "19"),
                _p("AP-BOX-LM", "Lebensmittelbox 1 l, 50 Stück", "7.48", "7"),
                _p("AP-BEUTEL-LM", "Brotbeutel Papier, 1000 Stück", "18.26", "7"),
            ),
        ),
        Supplier(
            "bilingual", "GS-HL",
            Party("Hanse Logistik & Bedarf GmbH", "Am Sandtorkai 40", "20457 Hamburg", "DE271828182"),
            "EUR", "RE-2026-{:04d}",
            (
                _p("HL-01", "Lagerregal 2000x1000x500 mm", "148.00", "19"),
                _p("HL-02", "Kunststoffbox 60 l", "12.84", "19"),
                _p("HL-03", "Gabelhubwagen 2500 kg", "389.00", "19"),
                _p("HL-04", "Klebeband braun 50 mm", "1.98", "19"),
                _p("HL-05", "Etiketten 100x150 mm, 500 Stk.", "8.46", "19"),
                _p("HL-06", "Handscanner Barcode", "96.40", "19"),
                _p("HL-07", "Sicherheitsschuhe S3, Gr. 42", "54.90", "19"),
                _p("HL-08", "Warnweste gelb", "3.28", "19"),
                _p("HL-09", "Erste-Hilfe-Kasten DIN 13157", "32.60", "19"),
                _p("HL-10", "Kaffee Bohnen 1 kg", "16.98", "7"),
                _p("HL-11", "Mineralwasser 12 x 1 l", "5.94", "7"),
                _p("HL-12", "Zucker 1 kg", "1.58", "7"),
                _p("HL-13", "Kantinen-Snackbox", "4.72", "7"),
                _p("HL-14", "Reinigungstücher, 100 Stk.", "6.36", "19"),
                _p("HL-15", "Müllsäcke 120 l, 25 Stk.", "7.12", "19"),
                _p("HL-16", "Paletten-Etikettendrucker", "412.00", "19"),
            ),
        ),
    )
}

TEMPLATE_KEYS: tuple[str, ...] = tuple(SUPPLIERS)
```

- [ ] **Step 5: Run the tests**

Run: `cd eval && uv run pytest -q`
Expected: `9 passed`.

- [ ] **Step 6: Commit (user)**

Suggest: `git add eval/pyproject.toml eval/uv.lock eval/.gitignore eval/src eval/tests && git commit -m "feat(eval): uv project, money helpers, model and suppliers"`

---

### Task 2: Label models — USER EXERCISE E1

**Files:**
- Create: `eval/src/golden/labels.py` (Claude writes it as shown, with the three TODO classes)
- Test: `eval/tests/test_labels.py`

**Interfaces:**
- Consumes: `golden.money.line_net`.
- Produces: `ExceptionCode` (StrEnum of the 7 codes), `LineFields(description, quantity, unit_price, tax_rate, net_amount)`, `Totals(net, tax, gross)`, `InvoiceFields(supplier_name, invoice_number, invoice_date, currency, po_number, lines, totals)`, `Intent(code, line)`, `ExpectedException(code, lineNumber, expected, actual, message)`, `ErpRefs(invoice_id, purchase_order_id, line_po_item_ids)`, `Label(...)`, `Manifest(...)`. All Decimal fields serialise to JSON strings.

- [ ] **Step 1: Claude creates `labels.py` with the exercise skeleton**

`eval/src/golden/labels.py`:

```python
"""Label format of the golden dataset (one JSON file per invoice) and the manifest.

These models are the data contract between the generator, the dataset checks and — from M3 on — the
extraction service, whose LLM output must validate against InvoiceFields.
"""

from datetime import date, datetime
from decimal import Decimal
from enum import StrEnum
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field, model_validator

from golden.money import line_net


class Strict(BaseModel):
    """Immutable, and unknown keys are an error (a typo in a label must not pass silently)."""

    model_config = ConfigDict(frozen=True, extra="forbid")


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
    pass  # TODO E1


class Totals(Strict):
    pass  # TODO E1


class InvoiceFields(Strict):
    pass  # TODO E1


# ---- prepared models ------------------------------------------------------------------------


class Intent(Strict):
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
    invoice_id: int
    purchase_order_id: int | None
    line_po_item_ids: list[int | None]


class Label(Strict):
    id: str = Field(pattern=r"^inv-\d{4}$")
    split: Literal["dev", "test"] | None = None
    template: str
    scenario: str
    group: str
    pdf: str
    fields: InvoiceFields
    intent: list[Intent]
    expected_exceptions: list[ExpectedException]
    erp: ErpRefs


class Manifest(Strict):
    dataset_version: str
    seed: int
    generated_at: datetime
    erp_commit: str
    generator_version: str
    counts: dict[str, int]
    splits: dict[str, list[str]]
```

- [ ] **Step 2: Claude creates the tests**

`eval/tests/test_labels.py`:

```python
import json
from datetime import date
from decimal import Decimal

import pytest
from pydantic import ValidationError

from golden.labels import InvoiceFields, Label, LineFields, Totals


def line(**overrides):
    data = {"description": "Bolt", "quantity": "10", "unit_price": "4.15", "tax_rate": "19", "net_amount": "41.50"}
    return data | overrides


def fields(**overrides):
    data = {
        "supplier_name": "Acme",
        "invoice_number": "INV-1",
        "invoice_date": "2026-03-14",
        "currency": "EUR",
        "po_number": "PO-000001",
        "lines": [line()],
        "totals": {"net": "41.50", "tax": "7.89", "gross": "49.39"},
    }
    return data | overrides


def test_valid_fields_parse_into_exact_decimals_and_dates():
    f = InvoiceFields.model_validate(fields())
    assert f.lines[0].unit_price == Decimal("4.15")
    assert isinstance(f.lines[0].unit_price, Decimal)
    assert f.invoice_date == date(2026, 3, 14)
    assert f.totals.gross == Decimal("49.39")


def test_line_net_must_equal_quantity_times_price():
    with pytest.raises(ValidationError):
        LineFields.model_validate(line(net_amount="41.49"))


def test_line_net_uses_half_up_rounding_like_the_erp():
    assert LineFields.model_validate(line(quantity="3", unit_price="0.335", net_amount="1.01"))
    with pytest.raises(ValidationError):
        LineFields.model_validate(line(quantity="3", unit_price="0.335", net_amount="1.00"))


def test_gross_must_be_net_plus_tax():
    with pytest.raises(ValidationError):
        Totals.model_validate({"net": "41.50", "tax": "7.89", "gross": "49.40"})


def test_totals_net_must_equal_sum_of_line_nets():
    with pytest.raises(ValidationError):
        InvoiceFields.model_validate(fields(totals={"net": "41.00", "tax": "7.89", "gross": "48.89"}))


def test_currency_must_be_three_capital_letters():
    for bad in ("eur", "EURO", "E1R"):
        with pytest.raises(ValidationError):
            InvoiceFields.model_validate(fields(currency=bad))


def test_po_number_may_be_missing():
    assert InvoiceFields.model_validate(fields(po_number=None)).po_number is None


def test_invoice_needs_at_least_one_line():
    with pytest.raises(ValidationError):
        InvoiceFields.model_validate(fields(lines=[], totals={"net": "0.00", "tax": "0.00", "gross": "0.00"}))


def test_unknown_keys_are_rejected():
    with pytest.raises(ValidationError):
        LineFields.model_validate(line(colour="red"))


def test_decimals_are_written_as_strings():
    dumped = json.loads(InvoiceFields.model_validate(fields()).model_dump_json())
    assert dumped["lines"][0]["unit_price"] == "4.15"
    assert dumped["totals"]["net"] == "41.50"


def test_full_label_round_trips_through_json():
    label = {
        "id": "inv-0001", "split": "dev", "template": "nordbuero_de", "scenario": "price_above_tolerance",
        "group": "g-001", "pdf": "invoices/inv-0001.pdf", "fields": fields(),
        "intent": [{"code": "PRICE_MISMATCH", "line": 1}],
        "expected_exceptions": [{"code": "PRICE_MISMATCH", "lineNumber": 1, "expected": "3.89",
                                 "actual": "4.15", "message": "Line 1: ..."}],
        "erp": {"invoice_id": 7, "purchase_order_id": 3, "line_po_item_ids": [11]},
    }
    parsed = Label.model_validate(label)
    assert Label.model_validate_json(parsed.model_dump_json()) == parsed
```

- [ ] **Step 3: Run the tests to see the starting point**

Run: `cd eval && uv run pytest tests/test_labels.py -q`
Expected: most tests FAIL (the TODO classes have no fields; e.g. `test_valid_fields...` fails with `extra_forbidden`).

- [ ] **Step 4: HAND OVER TO THE USER — E1**

Tell the user: implement `LineFields`, `Totals`, `InvoiceFields` in `eval/src/golden/labels.py` until `uv run pytest tests/test_labels.py -q` is green. Hints (Kotlin mapping in brackets):

- A field is a class attribute with a type: `quantity: Decimal` (like a `val` in a data class constructor).
- Pydantic converts the JSON string `"4.15"` into `Decimal("4.15")` by itself.
- Constraints go into `Field(...)`: `Field(pattern=r"^[A-Z]{3}$")`, `Field(min_length=1)` for a list.
- Cross-field rules: a method decorated with `@model_validator(mode="after")` that receives `self`, raises `ValueError("...")` when broken and otherwise `return self` (like an `init { require(...) }` block).
- Use `line_net` from `golden.money` — the label must round exactly like the ERP.
- `sum(...)` of Decimals needs a Decimal start value: `sum((l.net_amount for l in self.lines), Decimal(0))`.

<details>
<summary>Reference solution (for review only)</summary>

```python
class LineFields(Strict):
    description: str
    quantity: Decimal
    unit_price: Decimal
    tax_rate: Decimal
    net_amount: Decimal

    @model_validator(mode="after")
    def net_is_quantity_times_price(self) -> "LineFields":
        expected = line_net(self.quantity, self.unit_price)
        if self.net_amount != expected:
            raise ValueError(f"net_amount {self.net_amount} != quantity x unit_price = {expected}")
        return self


class Totals(Strict):
    net: Decimal
    tax: Decimal
    gross: Decimal

    @model_validator(mode="after")
    def gross_is_net_plus_tax(self) -> "Totals":
        if self.gross != self.net + self.tax:
            raise ValueError(f"gross {self.gross} != net {self.net} + tax {self.tax}")
        return self


class InvoiceFields(Strict):
    supplier_name: str
    invoice_number: str
    invoice_date: date
    currency: str = Field(pattern=r"^[A-Z]{3}$")
    po_number: str | None
    lines: list[LineFields] = Field(min_length=1)
    totals: Totals

    @model_validator(mode="after")
    def net_is_sum_of_lines(self) -> "InvoiceFields":
        lines_net = sum((line.net_amount for line in self.lines), Decimal(0))
        if self.totals.net != lines_net:
            raise ValueError(f"totals.net {self.totals.net} != sum of line nets {lines_net}")
        return self
```
</details>

- [ ] **Step 5: Verify after the user's turn**

Run: `cd eval && uv run pytest tests/test_labels.py -q`
Expected: `11 passed`. Review the user's code against the reference (behaviour, not wording).

- [ ] **Step 6: Commit (user)**

Suggest: `git add eval/src/golden/labels.py eval/tests/test_labels.py && git commit -m "feat(eval): label and manifest models (E1)"`

---

### Task 3: ERP client and the golden ERP script

**Files:**
- Create: `eval/src/golden/erp_client.py`, `eval/scripts/golden-erp.sh`
- Test: `eval/tests/test_erp_client.py`

**Interfaces:**
- Produces: `ErpError(RuntimeError)`; `ErpClient(base_url="http://localhost:8081", transport=None)` with `list_invoices() -> list[dict]`, `create_supplier(number, name, currency, vat_id) -> int`, `create_product(sku, name) -> int`, `warehouse_id(code="MAIN") -> int`, `create_purchase_order(supplier_id, order_date, items) -> dict`, `approve(po_id) -> None`, `receive(po_id, warehouse_id, receipt_date, items) -> None`, `create_invoice(body) -> dict`, `get_invoice(invoice_id) -> dict`, `match(invoice_id) -> dict`. All response numbers are `Decimal`.

- [ ] **Step 1: Write the failing tests**

`eval/tests/test_erp_client.py`:

```python
import json
from datetime import date
from decimal import Decimal

import httpx
import pytest

from golden.erp_client import ErpClient, ErpError


def client_with(handler) -> ErpClient:
    return ErpClient("http://erp.test", transport=httpx.MockTransport(handler))


def test_response_numbers_are_decimals():
    def handler(request):
        return httpx.Response(200, text='{"id": 1, "items": [{"unitPrice": 21.9000, "quantity": 50.000}]}')

    invoice = client_with(handler).get_invoice(1)
    assert invoice["items"][0]["unitPrice"] == Decimal("21.9000")
    assert isinstance(invoice["items"][0]["quantity"], Decimal)


def test_request_body_sends_decimals_and_dates_as_exact_strings():
    seen = {}

    def handler(request):
        seen["path"] = request.url.path
        seen["body"] = json.loads(request.content)
        return httpx.Response(201, text='{"id": 5, "items": []}')

    client_with(handler).create_purchase_order(
        3, date(2026, 3, 1), [{"productId": 9, "quantity": Decimal("12"), "unitPrice": Decimal("4.2009"), "taxRate": Decimal("19")}]
    )
    assert seen["path"] == "/api/purchase-orders"
    assert seen["body"] == {
        "supplierId": 3, "orderDate": "2026-03-01",
        "items": [{"productId": 9, "quantity": "12", "unitPrice": "4.2009", "taxRate": "19"}],
    }


def test_error_response_raises_with_status_and_body():
    def handler(request):
        return httpx.Response(422, text='{"detail":"Receipt date lies in the future"}')

    with pytest.raises(ErpError, match=r"POST /goods-receipts -> 422: .*future"):
        client_with(handler).receive(1, 1, date(2026, 3, 1), [{"purchaseOrderItemId": 1, "quantity": Decimal(1)}])


def test_warehouse_id_finds_the_default_warehouse():
    def handler(request):
        return httpx.Response(200, text='[{"id": 4, "code": "HAM"}, {"id": 1, "code": "MAIN"}]')

    assert client_with(handler).warehouse_id() == 1
```

- [ ] **Step 2: Run them to verify they fail**

Run: `cd eval && uv run pytest tests/test_erp_client.py -q`
Expected: `ModuleNotFoundError: No module named 'golden.erp_client'`.

- [ ] **Step 3: Implement the client**

`eval/src/golden/erp_client.py`:

```python
"""Thin client for the erpai REST API — only the calls the generator needs."""

import json
from datetime import date
from decimal import Decimal
from typing import Any

import httpx


class ErpError(RuntimeError):
    """Non-2xx response from the ERP; the message carries method, path, status and body."""


def _jsonable(value: Any) -> Any:
    """Decimals and dates as strings: exact on the wire (Jackson reads "4.2009" into a BigDecimal)."""
    if isinstance(value, Decimal):
        return format(value, "f")
    if isinstance(value, date):
        return value.isoformat()
    if isinstance(value, dict):
        return {key: _jsonable(item) for key, item in value.items()}
    if isinstance(value, (list, tuple)):
        return [_jsonable(item) for item in value]
    return value


class ErpClient:
    def __init__(self, base_url: str = "http://localhost:8081", transport: httpx.BaseTransport | None = None) -> None:
        self._http = httpx.Client(base_url=f"{base_url.rstrip('/')}/api", transport=transport, timeout=30)

    def _call(self, method: str, path: str, body: Any = None) -> Any:
        response = self._http.request(method, path, json=None if body is None else _jsonable(body))
        if response.is_error:
            raise ErpError(f"{method} {path} -> {response.status_code}: {response.text}")
        # parse_float=Decimal: 21.9000 stays Decimal("21.9000") and never becomes a binary float
        return json.loads(response.text, parse_float=Decimal) if response.content else None

    def list_invoices(self) -> list[dict]:
        return self._call("GET", "/invoices")

    def create_supplier(self, number: str, name: str, currency: str, vat_id: str) -> int:
        body = {"supplierNumber": number, "name": name, "vatId": vat_id, "currency": currency, "paymentTermsDays": 30}
        return self._call("POST", "/suppliers", body)["id"]

    def create_product(self, sku: str, name: str) -> int:
        return self._call("POST", "/products", {"sku": sku, "name": name, "unitOfMeasure": "PIECE"})["id"]

    def warehouse_id(self, code: str = "MAIN") -> int:
        return next(w["id"] for w in self._call("GET", "/warehouses") if w["code"] == code)

    def create_purchase_order(self, supplier_id: int, order_date: date, items: list[dict]) -> dict:
        return self._call("POST", "/purchase-orders", {"supplierId": supplier_id, "orderDate": order_date, "items": items})

    def approve(self, po_id: int) -> None:
        self._call("POST", f"/purchase-orders/{po_id}/approve")

    def receive(self, po_id: int, warehouse_id: int, receipt_date: date, items: list[dict]) -> None:
        body = {"purchaseOrderId": po_id, "warehouseId": warehouse_id, "receiptDate": receipt_date, "items": items}
        self._call("POST", "/goods-receipts", body)

    def create_invoice(self, body: dict) -> dict:
        return self._call("POST", "/invoices", body)

    def get_invoice(self, invoice_id: int) -> dict:
        return self._call("GET", f"/invoices/{invoice_id}")

    def match(self, invoice_id: int) -> dict:
        return self._call("GET", f"/invoices/{invoice_id}/match")
```

- [ ] **Step 4: Run the tests**

Run: `cd eval && uv run pytest tests/test_erp_client.py -q`
Expected: `4 passed`.

- [ ] **Step 5: Create the golden ERP script**

`eval/scripts/golden-erp.sh`:

```bash
#!/usr/bin/env bash
# Recreates the golden-dataset database and starts the ERP backend against it on port 8081.
# The normal ERP (its own database, port 8080) is not touched. Stop with Ctrl+C.
set -euo pipefail
cd "$(dirname "$0")/../.."   # repo root

docker compose up -d postgres >/dev/null
until docker exec erpai-postgres pg_isready -U erpai -d erpai >/dev/null 2>&1; do sleep 1; done
docker exec erpai-postgres psql -q -U erpai -d erpai -c "DROP DATABASE IF EXISTS erpai_golden WITH (FORCE)"
docker exec erpai-postgres psql -q -U erpai -d erpai -c "CREATE DATABASE erpai_golden"

export JAVA_HOME="${GOLDEN_JAVA_HOME:-$HOME/.jdks/openjdk-22.0.2}"
export SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5433/erpai_golden"
export SPRING_DATASOURCE_USERNAME=erpai
export SPRING_DATASOURCE_PASSWORD=erpai

cd backend
exec ./mvnw -q spring-boot:run -Dspring-boot.run.profiles=dev -Dspring-boot.run.arguments=--server.port=8081
```

- [ ] **Step 6: Smoke-test the script against a real backend**

Run (background): `bash eval/scripts/golden-erp.sh`
Then: `curl -s localhost:8081/api/invoices` → Expected: `[]`. And `curl -s localhost:8081/api/warehouses` → Expected: a list containing `"code":"MAIN"`.
Leave it running for Task 9.

- [ ] **Step 7: Commit (user)**

Suggest: `git add eval/src/golden/erp_client.py eval/tests/test_erp_client.py eval/scripts/golden-erp.sh && git commit -m "feat(eval): ERP client and golden ERP script"`

---

### Task 4: Scenario building blocks and Claude's scenario functions

**Files:**
- Create: `eval/src/golden/scenarios.py` (all of it, including the five E2 stubs)
- Test: `eval/tests/test_scenarios_base.py`

**Interfaces:**
- Consumes: `golden.model.*`, `golden.suppliers.SUPPLIERS, TEMPLATE_KEYS`.
- Produces: `Ctx(rng, supplier, next_invoice_number)`; building blocks `clean(ctx, n_lines=None, quantities=None)`, `named`, `replace_invoice(spec, index=0, **changes)`, `replace_line(spec, line, invoice=0, **changes)`, `add_intent(spec, *intents, invoice=0)`, `pick_line(ctx, spec)`, `part_of(ctx, quantity)`, `freight_line(ctx)`; constants `FIRST_ORDER_DATE, LAST_ORDER_DATE, LAST_INVOICE_DATE, QUANTITIES, OTHER_CURRENCY, OTHER_TAX_RATE`; scenario functions `clean_full, clean_partial_delivery, quantity_not_received, tax_rate_wrong, currency_mismatch, missing_po, price_and_tax, freight_and_price, missing_po_duplicate, currency_and_quantity`; E2 stubs `price_above_tolerance, price_at_tolerance, unmatched_freight, duplicate_pair, partial_series_overbilling`. Every scenario function: `(Ctx) -> ScenarioSpec` with `scenario` = its own name.

- [ ] **Step 1: Write the failing tests**

`eval/tests/test_scenarios_base.py`:

```python
import itertools
import random
from decimal import Decimal

import pytest

from golden.model import Intent
from golden.scenarios import (
    LAST_INVOICE_DATE, Ctx, clean, clean_full, clean_partial_delivery, currency_mismatch,
    missing_po, quantity_not_received, tax_rate_wrong,
)
from golden.suppliers import SUPPLIERS, TEMPLATE_KEYS

SEEDS = range(25)


def ctx(seed: int, key: str | None = None) -> Ctx:
    numbers = itertools.count(1)
    supplier = SUPPLIERS[key or TEMPLATE_KEYS[seed % len(TEMPLATE_KEYS)]]
    return Ctx(random.Random(seed), supplier, lambda: f"T-{next(numbers)}")


@pytest.mark.parametrize("seed", SEEDS)
def test_clean_bills_exactly_what_was_ordered_and_received(seed):
    spec = clean(ctx(seed))
    inv = spec.invoices[0]
    assert inv.intent == () and inv.with_po
    assert [l.quantity for l in spec.po_lines] == list(spec.receipts[0].quantities)
    for n, line in enumerate(inv.lines, 1):
        po = spec.po_lines[line.po_line - 1]
        assert line.po_line == n
        assert (line.quantity, line.unit_price, line.tax_rate) == (po.quantity, po.unit_price, po.tax_rate)
    assert spec.order_date <= spec.receipts[0].receipt_date <= inv.invoice_date <= LAST_INVOICE_DATE


def test_clean_with_explicit_quantities():
    spec = clean(ctx(1), quantities=[Decimal(100)])
    assert [l.quantity for l in spec.po_lines] == [Decimal(100)]


@pytest.mark.parametrize("seed", SEEDS)
def test_clean_full_is_long_only_for_bilingual(seed):
    spec = clean_full(ctx(seed))
    assert spec.scenario == "clean_full"
    if spec.template == "bilingual":
        assert 13 <= len(spec.invoices[0].lines) <= 16
    else:
        assert len(spec.invoices[0].lines) <= 5


@pytest.mark.parametrize("seed", SEEDS)
def test_partial_delivery_bills_what_arrived(seed):
    spec = clean_partial_delivery(ctx(seed))
    received = spec.receipts[0].quantities
    assert [l.quantity for l in spec.invoices[0].lines] == list(received)
    assert all(0 < r < po.quantity for r, po in zip(received, spec.po_lines))
    assert spec.invoices[0].intent == ()


@pytest.mark.parametrize("seed", SEEDS)
def test_quantity_not_received(seed):
    spec = quantity_not_received(ctx(seed))
    (intent,) = spec.invoices[0].intent
    short = [n for n, (r, po) in enumerate(zip(spec.receipts[0].quantities, spec.po_lines), 1) if r < po.quantity]
    assert intent == Intent("QUANTITY_MISMATCH", short[0]) and len(short) == 1


@pytest.mark.parametrize("seed", SEEDS)
def test_tax_rate_wrong(seed):
    spec = tax_rate_wrong(ctx(seed))
    (intent,) = spec.invoices[0].intent
    line = spec.invoices[0].lines[intent.line - 1]
    assert intent.code == "TAX_MISMATCH"
    assert line.tax_rate != spec.po_lines[line.po_line - 1].tax_rate


@pytest.mark.parametrize("seed", SEEDS)
def test_currency_mismatch(seed):
    c = ctx(seed)
    spec = currency_mismatch(c)
    assert spec.invoices[0].currency != c.supplier.currency
    assert spec.invoices[0].intent == (Intent("CURRENCY_MISMATCH"),)


@pytest.mark.parametrize("seed", SEEDS)
def test_missing_po_has_no_purchase_order_at_all(seed):
    spec = missing_po(ctx(seed))
    inv = spec.invoices[0]
    assert spec.po_lines == () and spec.receipts == ()
    assert not inv.with_po and all(l.po_line is None for l in inv.lines)
    assert inv.intent == (Intent("MISSING_PO"),)
```

- [ ] **Step 2: Run them to verify they fail**

Run: `cd eval && uv run pytest tests/test_scenarios_base.py -q`
Expected: `ModuleNotFoundError: No module named 'golden.scenarios'`.

- [ ] **Step 3: Implement `scenarios.py` (without the catalog — that is Task 6)**

`eval/src/golden/scenarios.py`:

```python
"""Scenario catalog: what each golden-dataset invoice is meant to test.

A scenario function takes a Ctx and returns a ScenarioSpec. `intent` is what the scenario MEANS to
trigger; the ERP's match result becomes the label, and tests/test_dataset.py checks that both agree.
"""

import random
from collections.abc import Callable
from dataclasses import dataclass, replace
from datetime import date, timedelta
from decimal import ROUND_FLOOR, ROUND_HALF_UP, Decimal

from golden.model import Intent, InvLine, InvoiceSpec, PoLine, Receipt, ScenarioSpec, Supplier

FIRST_ORDER_DATE = date(2026, 1, 5)
LAST_ORDER_DATE = date(2026, 8, 15)
LAST_INVOICE_DATE = date(2026, 9, 30)

# All even, so every quantity splits into two whole halves (duplicate_pair).
QUANTITIES = tuple(Decimal(q) for q in (2, 4, 6, 8, 10, 12, 20, 24, 40, 50, 60, 80, 100, 120, 200, 250))

OTHER_CURRENCY = {"EUR": "USD", "USD": "EUR", "GBP": "EUR"}
OTHER_TAX_RATE = {Decimal(19): Decimal(7), Decimal(7): Decimal(19), Decimal(0): Decimal(19), Decimal(20): Decimal(5)}


@dataclass
class Ctx:
    """What a scenario function may use: its own random generator, its supplier, fresh invoice numbers."""

    rng: random.Random
    supplier: Supplier
    next_invoice_number: Callable[[], str]


# ---- building blocks ------------------------------------------------------------------------


def clean(ctx: Ctx, n_lines: int | None = None, quantities: list[Decimal] | None = None) -> ScenarioSpec:
    """A PO, fully received, billed exactly at PO prices — the starting point of every scenario."""
    rng, supplier = ctx.rng, ctx.supplier
    n = len(quantities) if quantities else (n_lines or rng.randint(1, min(5, len(supplier.products))))
    products = rng.sample(supplier.products, n)
    qtys = list(quantities) if quantities else [rng.choice(QUANTITIES) for _ in products]
    po_lines = tuple(PoLine(p.sku, q, p.unit_price, p.tax_rate) for p, q in zip(products, qtys))

    order_date = FIRST_ORDER_DATE + timedelta(days=rng.randint(0, (LAST_ORDER_DATE - FIRST_ORDER_DATE).days))
    receipt_date = order_date + timedelta(days=rng.randint(2, 10))
    invoice_date = receipt_date + timedelta(days=rng.randint(0, 14))

    invoice = InvoiceSpec(
        invoice_number=ctx.next_invoice_number(),
        invoice_date=invoice_date,
        currency=supplier.currency,
        with_po=True,
        lines=tuple(
            InvLine(n, p.name, line.quantity, line.unit_price, line.tax_rate)
            for n, (p, line) in enumerate(zip(products, po_lines), 1)
        ),
    )
    receipt = Receipt(receipt_date, tuple(qtys))
    return ScenarioSpec("clean", supplier.key, order_date, po_lines, (receipt,), (invoice,))


def named(spec: ScenarioSpec, scenario: str) -> ScenarioSpec:
    return replace(spec, scenario=scenario)


def replace_invoice(spec: ScenarioSpec, index: int = 0, **changes) -> ScenarioSpec:
    """Copy of `spec` with fields of invoice `index` changed (dataclasses are immutable)."""
    invoices = list(spec.invoices)
    invoices[index] = replace(invoices[index], **changes)
    return replace(spec, invoices=tuple(invoices))


def replace_line(spec: ScenarioSpec, line: int, invoice: int = 0, **changes) -> ScenarioSpec:
    """Copy of `spec` with fields of invoice line `line` (1-based) of invoice `invoice` changed."""
    lines = list(spec.invoices[invoice].lines)
    lines[line - 1] = replace(lines[line - 1], **changes)
    return replace_invoice(spec, invoice, lines=tuple(lines))


def add_intent(spec: ScenarioSpec, *intents: Intent, invoice: int = 0) -> ScenarioSpec:
    """Add intents to one invoice, kept in the matcher's output order."""
    merged = spec.invoices[invoice].intent + intents
    return replace_invoice(spec, invoice, intent=tuple(sorted(merged, key=Intent.sort_key)))


def pick_line(ctx: Ctx, spec: ScenarioSpec) -> int:
    """A random line number (1-based) of the first invoice."""
    return ctx.rng.randint(1, len(spec.invoices[0].lines))


def part_of(ctx: Ctx, quantity: Decimal) -> Decimal:
    """50-90 % of `quantity`, rounded down: at least 1 and always less than `quantity` (which is >= 2)."""
    pct = ctx.rng.choice((50, 60, 75, 80, 90))
    return max(Decimal(1), (quantity * pct / 100).to_integral_value(rounding=ROUND_FLOOR))


def freight_line(ctx: Ctx) -> InvLine:
    """A charge nobody ordered: not linked to any PO line."""
    price = Decimal(ctx.rng.choice((25, 38, 45, 60, 85, 120)))
    return InvLine(None, "Freight / Fracht", Decimal(1), price, ctx.supplier.products[0].tax_rate)


def _with_received(spec: ScenarioSpec, quantities: tuple[Decimal, ...]) -> ScenarioSpec:
    return replace(spec, receipts=(replace(spec.receipts[0], quantities=quantities),))


# ---- scenarios built by Claude --------------------------------------------------------------


def clean_full(ctx: Ctx) -> ScenarioSpec:
    """Clean. Bilingual invoices are long (13-16 lines) so the table runs over a page break."""
    n_lines = ctx.rng.randint(13, 16) if ctx.supplier.key == "bilingual" else None
    return named(clean(ctx, n_lines=n_lines), "clean_full")


def clean_partial_delivery(ctx: Ctx) -> ScenarioSpec:
    """Clean: only part of each line arrived, and exactly that part is billed."""
    spec = clean(ctx)
    received = tuple(part_of(ctx, q) for q in spec.receipts[0].quantities)
    spec = _with_received(spec, received)
    for n, quantity in enumerate(received, 1):
        spec = replace_line(spec, n, quantity=quantity)
    return named(spec, "clean_partial_delivery")


def quantity_not_received(ctx: Ctx) -> ScenarioSpec:
    """QUANTITY_MISMATCH: one line only partly delivered but billed in full."""
    spec = clean(ctx)
    n = pick_line(ctx, spec)
    received = list(spec.receipts[0].quantities)
    received[n - 1] = part_of(ctx, received[n - 1])
    spec = _with_received(spec, tuple(received))
    return named(add_intent(spec, Intent("QUANTITY_MISMATCH", n)), "quantity_not_received")


def tax_rate_wrong(ctx: Ctx) -> ScenarioSpec:
    """TAX_MISMATCH: one line billed with another VAT rate than ordered."""
    spec = clean(ctx)
    n = pick_line(ctx, spec)
    wrong = OTHER_TAX_RATE[spec.invoices[0].lines[n - 1].tax_rate]
    return named(add_intent(replace_line(spec, n, tax_rate=wrong), Intent("TAX_MISMATCH", n)), "tax_rate_wrong")


def currency_mismatch(ctx: Ctx) -> ScenarioSpec:
    """CURRENCY_MISMATCH: same numbers, other currency (the matcher then skips the price rule)."""
    spec = replace_invoice(clean(ctx), currency=OTHER_CURRENCY[ctx.supplier.currency])
    return named(add_intent(spec, Intent("CURRENCY_MISMATCH")), "currency_mismatch")


def missing_po(ctx: Ctx) -> ScenarioSpec:
    """MISSING_PO: goods billed without any purchase order."""
    spec = clean(ctx)
    lines = tuple(replace(line, po_line=None) for line in spec.invoices[0].lines)
    spec = replace(replace_invoice(spec, with_po=False, lines=lines), po_lines=(), receipts=())
    return named(add_intent(spec, Intent("MISSING_PO")), "missing_po")


def price_and_tax(ctx: Ctx) -> ScenarioSpec:
    """PRICE_MISMATCH + TAX_MISMATCH on the same line."""
    spec = price_above_tolerance(ctx)
    n = spec.invoices[0].intent[0].line
    wrong = OTHER_TAX_RATE[spec.invoices[0].lines[n - 1].tax_rate]
    return named(add_intent(replace_line(spec, n, tax_rate=wrong), Intent("TAX_MISMATCH", n)), "price_and_tax")


def freight_and_price(ctx: Ctx) -> ScenarioSpec:
    """PRICE_MISMATCH on one line plus an unordered freight line."""
    spec = price_above_tolerance(ctx)
    lines = spec.invoices[0].lines + (freight_line(ctx),)
    spec = replace_invoice(spec, lines=lines)
    return named(add_intent(spec, Intent("UNMATCHED_LINE", len(lines))), "freight_and_price")


def missing_po_duplicate(ctx: Ctx) -> ScenarioSpec:
    """MISSING_PO + DUPLICATE_INVOICE: an invoice without PO, sent twice."""
    spec = missing_po(ctx)
    first = spec.invoices[0]
    copy = replace(first, invoice_date=first.invoice_date + timedelta(days=ctx.rng.randint(3, 10)))
    spec = replace(spec, invoices=(first, copy))
    spec = add_intent(spec, Intent("DUPLICATE_INVOICE"), invoice=0)
    spec = add_intent(spec, Intent("DUPLICATE_INVOICE"), invoice=1)
    return named(spec, "missing_po_duplicate")


def currency_and_quantity(ctx: Ctx) -> ScenarioSpec:
    """CURRENCY_MISMATCH + QUANTITY_MISMATCH: billed in another currency before everything arrived."""
    spec = replace_invoice(quantity_not_received(ctx), currency=OTHER_CURRENCY[ctx.supplier.currency])
    return named(add_intent(spec, Intent("CURRENCY_MISMATCH")), "currency_and_quantity")


# ---- E2: your exercise ----------------------------------------------------------------------
#
# Each function starts from clean(ctx) (or clean(ctx, quantities=[...])), changes what the supplier
# "prints", adds the intent with add_intent(...), and returns named(spec, "<function name>").
# tests/test_scenarios_exercise.py tells you when you are done.


def price_above_tolerance(ctx: Ctx) -> ScenarioSpec:
    """PRICE_MISMATCH: one line billed 1, 2, 3, 5, 7 or 10 % above or below the PO price,
    rounded to cents — always clearly outside the 0.5 % tolerance."""
    raise NotImplementedError("M2 exercise E2")


def price_at_tolerance(ctx: Ctx) -> ScenarioSpec:
    """Near miss that must stay CLEAN: one line exactly 0.5 % above or below the PO price."""
    raise NotImplementedError("M2 exercise E2")


def unmatched_freight(ctx: Ctx) -> ScenarioSpec:
    """UNMATCHED_LINE: a clean invoice plus a freight line at the end."""
    raise NotImplementedError("M2 exercise E2")


def duplicate_pair(ctx: Ctx) -> ScenarioSpec:
    """DUPLICATE_INVOICE on both invoices: the supplier bills each line in two halves but reuses the
    same invoice number for the second invoice (3-10 days later). Quantities still add up, so the
    duplicate is the ONLY exception."""
    raise NotImplementedError("M2 exercise E2")


def partial_series_overbilling(ctx: Ctx) -> ScenarioSpec:
    """A single-line PO of 50, 100 or 200 units, fully received, billed in three invoices with fresh
    numbers: 60 %, then 40 % (7 days later), then 1-5 extra units (14 days after the first).
    The first two are clean; the third gets QUANTITY_MISMATCH on line 1."""
    raise NotImplementedError("M2 exercise E2")
```

- [ ] **Step 4: Run the tests**

Run: `cd eval && uv run pytest tests/test_scenarios_base.py -q`
Expected: `176 passed` (7 parametrised tests × 25 seeds + 1 single test).

- [ ] **Step 5: Commit (user)**

Suggest: `git add eval/src/golden/scenarios.py eval/tests/test_scenarios_base.py && git commit -m "feat(eval): scenario building blocks and base scenarios"`

---

### Task 5: Scenario functions — USER EXERCISE E2

**Files:**
- Modify: `eval/src/golden/scenarios.py` (the five E2 functions only — by the user)
- Test: `eval/tests/test_scenarios_exercise.py` (Claude creates it)

**Interfaces:**
- Consumes: building blocks from Task 4.
- Produces: `price_above_tolerance`, `price_at_tolerance`, `unmatched_freight`, `duplicate_pair`, `partial_series_overbilling` — signatures unchanged; `price_above_tolerance` returns exactly one intent `Intent("PRICE_MISMATCH", n)` (Task 4's combos rely on it).

- [ ] **Step 1: Claude creates the tests**

`eval/tests/test_scenarios_exercise.py`:

```python
import itertools
import random
from decimal import Decimal

import pytest

from golden.model import Intent
from golden.scenarios import (
    Ctx, duplicate_pair, partial_series_overbilling, price_above_tolerance, price_at_tolerance, unmatched_freight,
)
from golden.suppliers import SUPPLIERS, TEMPLATE_KEYS

SEEDS = range(25)


def ctx(seed: int) -> Ctx:
    numbers = itertools.count(1)
    return Ctx(random.Random(seed), SUPPLIERS[TEMPLATE_KEYS[seed % 5]], lambda: f"T-{next(numbers)}")


def changed_prices(spec):
    inv = spec.invoices[0]
    return [(n, l) for n, l in enumerate(inv.lines, 1) if l.unit_price != spec.po_lines[l.po_line - 1].unit_price]


@pytest.mark.parametrize("seed", SEEDS)
def test_price_above_tolerance(seed):
    spec = price_above_tolerance(ctx(seed))
    assert spec.scenario == "price_above_tolerance"
    [(n, line)] = changed_prices(spec)
    po_price = spec.po_lines[line.po_line - 1].unit_price
    assert abs(line.unit_price - po_price) * 100 > Decimal("0.5") * po_price
    assert line.unit_price == line.unit_price.quantize(Decimal("0.01"))
    assert spec.invoices[0].intent == (Intent("PRICE_MISMATCH", n),)


@pytest.mark.parametrize("seed", SEEDS)
def test_price_at_tolerance_is_exactly_half_a_percent_and_clean(seed):
    spec = price_at_tolerance(ctx(seed))
    assert spec.scenario == "price_at_tolerance"
    [(n, line)] = changed_prices(spec)
    po_price = spec.po_lines[line.po_line - 1].unit_price
    assert abs(line.unit_price - po_price) * 1000 == po_price * 5
    assert spec.invoices[0].intent == ()


@pytest.mark.parametrize("seed", SEEDS)
def test_unmatched_freight(seed):
    spec = unmatched_freight(ctx(seed))
    inv = spec.invoices[0]
    assert spec.scenario == "unmatched_freight"
    assert inv.lines[-1].po_line is None
    assert all(l.po_line == n for n, l in enumerate(inv.lines[:-1], 1))
    assert inv.intent == (Intent("UNMATCHED_LINE", len(inv.lines)),)


@pytest.mark.parametrize("seed", SEEDS)
def test_duplicate_pair(seed):
    spec = duplicate_pair(ctx(seed))
    first, second = spec.invoices
    assert spec.scenario == "duplicate_pair"
    assert first.invoice_number == second.invoice_number
    assert first.invoice_date < second.invoice_date
    for po_line_no, po in enumerate(spec.po_lines, 1):
        billed = sum(l.quantity for inv in (first, second) for l in inv.lines if l.po_line == po_line_no)
        assert billed == po.quantity
    assert first.intent == second.intent == (Intent("DUPLICATE_INVOICE"),)


@pytest.mark.parametrize("seed", SEEDS)
def test_partial_series_overbilling(seed):
    spec = partial_series_overbilling(ctx(seed))
    first, second, third = spec.invoices
    assert spec.scenario == "partial_series_overbilling"
    assert len(spec.po_lines) == 1 and spec.po_lines[0].quantity in (50, 100, 200)
    assert len({i.invoice_number for i in spec.invoices}) == 3
    assert first.invoice_date < second.invoice_date < third.invoice_date
    q = spec.po_lines[0].quantity
    assert first.lines[0].quantity + second.lines[0].quantity == q == spec.receipts[0].quantities[0]
    assert 1 <= third.lines[0].quantity <= 5
    assert (first.intent, second.intent, third.intent) == ((), (), (Intent("QUANTITY_MISMATCH", 1),))
```

- [ ] **Step 2: Run them to see the starting point**

Run: `cd eval && uv run pytest tests/test_scenarios_exercise.py -q`
Expected: `125 failed` with `NotImplementedError: M2 exercise E2`.

- [ ] **Step 3: HAND OVER TO THE USER — E2**

Tell the user: implement the five functions at the bottom of `eval/src/golden/scenarios.py`. Work one function at a time and run `uv run pytest tests/test_scenarios_exercise.py -q -k <name>`. Hints:

- Dataclasses are immutable (Kotlin `data class` with `val`); you never modify, you build copies: `replace_line(spec, n, unit_price=new_price)`, `replace_invoice(spec, lines=...)`, `dataclasses.replace(obj, field=value)` (= Kotlin `copy(...)`).
- In `clean(...)`, invoice line `n` belongs to PO line `n`, so `spec.po_lines[n - 1]` is its PO line.
- Use only `ctx.rng` for randomness (`ctx.rng.choice(...)`, `ctx.rng.randint(a, b)`) — never `random.*` directly — so the dataset stays reproducible.
- Rounding to cents: `value.quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)`. The near miss needs **no** rounding: product prices have even cents, so `price * Decimal("1.005")` is exact.
- A tuple plus one element: `lines + (freight_line(ctx),)` (note the comma).
- Fresh invoice numbers: `ctx.next_invoice_number()`. Later dates: `some_date + timedelta(days=7)`.
- Think about *why* `duplicate_pair` bills halves instead of sending the same full invoice twice: what would the matcher report for the second full copy?

<details>
<summary>Reference solution (for review only)</summary>

```python
def price_above_tolerance(ctx: Ctx) -> ScenarioSpec:
    spec = clean(ctx)
    n = pick_line(ctx, spec)
    po_price = spec.po_lines[n - 1].unit_price
    pct = Decimal(ctx.rng.choice((1, 2, 3, 5, 7, 10))) * ctx.rng.choice((1, -1))
    new_price = (po_price * (100 + pct) / 100).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)
    spec = replace_line(spec, n, unit_price=new_price)
    return named(add_intent(spec, Intent("PRICE_MISMATCH", n)), "price_above_tolerance")


def price_at_tolerance(ctx: Ctx) -> ScenarioSpec:
    spec = clean(ctx)
    n = pick_line(ctx, spec)
    factor = Decimal("1.005") if ctx.rng.random() < 0.5 else Decimal("0.995")
    spec = replace_line(spec, n, unit_price=spec.po_lines[n - 1].unit_price * factor)
    return named(spec, "price_at_tolerance")


def unmatched_freight(ctx: Ctx) -> ScenarioSpec:
    spec = clean(ctx)
    lines = spec.invoices[0].lines + (freight_line(ctx),)
    spec = replace_invoice(spec, lines=lines)
    return named(add_intent(spec, Intent("UNMATCHED_LINE", len(lines))), "unmatched_freight")


def duplicate_pair(ctx: Ctx) -> ScenarioSpec:
    spec = clean(ctx)
    first = spec.invoices[0]
    first = replace(first, lines=tuple(replace(line, quantity=line.quantity / 2) for line in first.lines))
    second = replace(first, invoice_date=first.invoice_date + timedelta(days=ctx.rng.randint(3, 10)))
    spec = replace(spec, invoices=(first, second))
    spec = add_intent(spec, Intent("DUPLICATE_INVOICE"), invoice=0)
    spec = add_intent(spec, Intent("DUPLICATE_INVOICE"), invoice=1)
    return named(spec, "duplicate_pair")


def partial_series_overbilling(ctx: Ctx) -> ScenarioSpec:
    spec = clean(ctx, quantities=[ctx.rng.choice((Decimal(50), Decimal(100), Decimal(200)))])
    first = spec.invoices[0]
    line = first.lines[0]
    q = line.quantity

    def bill(quantity: Decimal, days: int, number: str) -> InvoiceSpec:
        return replace(first, invoice_number=number, invoice_date=first.invoice_date + timedelta(days=days),
                       lines=(replace(line, quantity=quantity),))

    invoices = (
        bill(q * 3 / 5, 0, first.invoice_number),
        bill(q * 2 / 5, 7, ctx.next_invoice_number()),
        bill(Decimal(ctx.rng.randint(1, 5)), 14, ctx.next_invoice_number()),
    )
    spec = replace(spec, invoices=invoices)
    return named(add_intent(spec, Intent("QUANTITY_MISMATCH", 1), invoice=2), "partial_series_overbilling")
```

Answer to the `duplicate_pair` question: a second full copy would also exceed the invoiceable quantity
(the first copy already billed everything), so the matcher reports `QUANTITY_MISMATCH` on every line
of the copy — the scenario would no longer test the duplicate rule in isolation.
</details>

- [ ] **Step 4: Verify after the user's turn**

Run: `cd eval && uv run pytest tests/test_scenarios_exercise.py tests/test_scenarios_base.py -q`
Expected: `301 passed`.

- [ ] **Step 5: Commit (user)**

Suggest: `git add eval/src/golden/scenarios.py eval/tests/test_scenarios_exercise.py && git commit -m "feat(eval): scenario functions (E2)"`

---

### Task 6: Scenario catalog

Requires Task 5 (the catalog calls the user's functions).

**Files:**
- Modify: `eval/src/golden/scenarios.py` (append the catalog)
- Test: `eval/tests/test_catalog.py`

**Interfaces:**
- Produces: `RECIPE: tuple[tuple[Callable[[Ctx], ScenarioSpec], int], ...]`, `build_catalog(seed: int) -> list[ScenarioSpec]` (183 specs, 200 invoices, ordered).

- [ ] **Step 1: Write the failing tests**

`eval/tests/test_catalog.py`:

```python
from collections import Counter, defaultdict

from golden.scenarios import FIRST_ORDER_DATE, LAST_INVOICE_DATE, build_catalog

SPECS = build_catalog(42)
INVOICES = [(spec, inv) for spec in SPECS for inv in spec.invoices]


def test_200_invoices_with_the_planned_mix():
    assert len(SPECS) == 183
    assert len(INVOICES) == 200
    assert sum(1 for _, inv in INVOICES if not inv.intent) == 80
    per_code = Counter(code for _, inv in INVOICES for code in {i.code for i in inv.intent})
    assert per_code == {
        "PRICE_MISMATCH": 30, "QUANTITY_MISMATCH": 24, "TAX_MISMATCH": 20, "UNMATCHED_LINE": 20,
        "DUPLICATE_INVOICE": 18, "CURRENCY_MISMATCH": 16, "MISSING_PO": 16,
    }


def test_catalog_is_deterministic():
    assert build_catalog(42) == SPECS
    assert build_catalog(7) != SPECS


def test_dates_are_fixed_in_the_past_and_ordered():
    for spec, inv in INVOICES:
        assert FIRST_ORDER_DATE <= spec.order_date
        assert inv.invoice_date <= LAST_INVOICE_DATE
        for receipt in spec.receipts:
            assert spec.order_date <= receipt.receipt_date <= inv.invoice_date


def test_invoice_numbers_repeat_only_in_duplicate_scenarios():
    seen = defaultdict(list)
    for spec, inv in INVOICES:
        seen[(spec.template, inv.invoice_number)].append(spec.scenario)
    repeated = {scenarios[0] for scenarios in seen.values() if len(scenarios) > 1}
    assert repeated == {"duplicate_pair", "missing_po_duplicate"}


def test_every_code_appears_in_at_least_three_templates():
    templates = defaultdict(set)
    for spec, inv in INVOICES:
        for intent in inv.intent:
            templates[intent.code].add(spec.template)
    assert all(len(t) >= 3 for t in templates.values())


def test_some_invoices_span_two_pages():
    assert sum(1 for _, inv in INVOICES if len(inv.lines) > 12) >= 8
```

- [ ] **Step 2: Run them to verify they fail**

Run: `cd eval && uv run pytest tests/test_catalog.py -q`
Expected: `ImportError: cannot import name 'build_catalog'`.

- [ ] **Step 3: Append the catalog to `scenarios.py`**

```python
# ---- catalog --------------------------------------------------------------------------------

from golden.suppliers import SUPPLIERS, TEMPLATE_KEYS  # noqa: E402  (kept next to its only user)

# (scenario function, number of instances). Invoice counts: clean 80, single 96, combinations 24.
RECIPE: tuple[tuple[Callable[[Ctx], ScenarioSpec], int], ...] = (
    (clean_full, 50),
    (clean_partial_delivery, 10),
    (price_at_tolerance, 12),
    (partial_series_overbilling, 4),  # 3 invoices each: 2 clean + 1 QUANTITY_MISMATCH
    (price_above_tolerance, 18),
    (quantity_not_received, 14),
    (tax_rate_wrong, 14),
    (unmatched_freight, 14),
    (duplicate_pair, 6),  # 2 invoices each
    (currency_mismatch, 10),
    (missing_po, 10),
    (price_and_tax, 6),
    (missing_po_duplicate, 3),  # 2 invoices each
    (freight_and_price, 6),
    (currency_and_quantity, 6),
)


def build_catalog(seed: int) -> list[ScenarioSpec]:
    """All scenario instances in generation order. Templates rotate so every scenario covers them all."""
    counters = {key: 0 for key in TEMPLATE_KEYS}
    specs: list[ScenarioSpec] = []
    for r, (scenario, count) in enumerate(RECIPE):
        for i in range(count):
            supplier = SUPPLIERS[TEMPLATE_KEYS[(i + r) % len(TEMPLATE_KEYS)]]

            def next_number(s: Supplier = supplier) -> str:
                counters[s.key] += 1
                return s.invoice_format.format(counters[s.key])

            # One generator per instance, seeded by name: changing one scenario does not reshuffle the others.
            rng = random.Random(f"{seed}:{scenario.__name__}:{i}")
            specs.append(scenario(Ctx(rng, supplier, next_number)))
    return specs
```

Move the `from golden.suppliers import ...` line to the top import block instead of keeping it here (it is shown here only to make the dependency visible); remove the `noqa` comment.

- [ ] **Step 4: Run the tests**

Run: `cd eval && uv run pytest tests/test_catalog.py -q`
Expected: `6 passed`.

- [ ] **Step 5: Commit (user)**

Suggest: `git add eval/src/golden/scenarios.py eval/tests/test_catalog.py && git commit -m "feat(eval): scenario catalog with the 200-invoice mix"`

---

### Task 7: PDF templates

**Files:**
- Create: `eval/src/golden/templates/__init__.py`, `formats.py`, `base.py`, `nordbuero_de.py`, `acme_us.py`, `techsource_uk.py`, `alpenpack_de.py`, `bilingual.py`
- Test: `eval/tests/test_templates.py`

**Interfaces:**
- Consumes: `golden.model.PrintedInvoice, PrintedLine, Party`, `golden.suppliers.SUPPLIERS, BUYER`.
- Produces: `templates.STYLES: dict[str, Style]`, `templates.render(invoice: PrintedInvoice) -> bytes`; `Style` with `fmt_date(date) -> str`, `fmt_qty(Decimal) -> str`, `fmt_price(Decimal) -> str`, `fmt_rate(Decimal) -> str`, `fmt_money(Decimal) -> str`, `fmt_total(Decimal, currency) -> str`, `tax_per_line: bool`; `formats.de_number(value, min_decimals=0)`, `formats.en_number(value, min_decimals=0)`.

- [ ] **Step 1: Write the failing tests**

`eval/tests/test_templates.py`:

```python
import io
from datetime import date
from decimal import Decimal

import pdfplumber
import pytest

from golden.model import PrintedInvoice, PrintedLine
from golden.suppliers import BUYER, SUPPLIERS
from golden.templates import STYLES, render
from golden.templates.formats import de_number, en_number


def test_number_formatting():
    assert de_number(Decimal("1234.5"), 2) == "1.234,50"
    assert en_number(Decimal("1234.5"), 2) == "1,234.50"
    assert de_number(Decimal("12.4017"), 2) == "12,4017"
    assert de_number(Decimal("50.000")) == "50"
    assert en_number(Decimal("1234567.891"), 2) == "1,234,567.891"
    assert en_number(Decimal("0.86"), 2) == "0.86"


def test_dates_per_template():
    d = date(2026, 3, 4)
    assert STYLES["nordbuero_de"].fmt_date(d) == "04.03.2026"
    assert STYLES["acme_us"].fmt_date(d) == "Mar 4, 2026"
    assert STYLES["techsource_uk"].fmt_date(d) == "04/03/2026"
    assert STYLES["alpenpack_de"].fmt_date(d) == "4. März 2026"
    assert STYLES["bilingual"].fmt_date(d) == "2026-03-04"


def invoice(template: str, n_lines: int = 2) -> PrintedInvoice:
    lines = tuple(
        PrintedLine(f"Artikel {i}", Decimal("1234.5"), Decimal("12.4017"), Decimal("19"), Decimal("15309.90"))
        for i in range(1, n_lines + 1)
    )
    net = Decimal("15309.90") * n_lines
    tax = (net * Decimal("0.19")).quantize(Decimal("0.01"))
    return PrintedInvoice(
        template=template, supplier=SUPPLIERS[template].party, buyer=BUYER, invoice_number="X-2026-0042",
        invoice_date=date(2026, 3, 4), currency=SUPPLIERS[template].currency, po_number="PO-000017",
        lines=lines, net=net, tax=tax, gross=net + tax, tax_breakdown=((Decimal("19"), net, tax),),
    )


def pdf_text(data: bytes) -> tuple[str, int]:
    with pdfplumber.open(io.BytesIO(data)) as pdf:
        return " ".join((page.extract_text() or "") for page in pdf.pages), len(pdf.pages)


@pytest.mark.parametrize("template", sorted(STYLES))
def test_printed_values_are_extractable(template):
    inv = invoice(template)
    style = STYLES[template]
    text, _ = pdf_text(render(inv))
    text = " ".join(text.split())
    expected = [
        inv.invoice_number, inv.po_number, style.fmt_date(inv.invoice_date), inv.supplier.name,
        style.fmt_total(inv.gross, inv.currency), style.fmt_total(inv.net, inv.currency), style.fmt_rate(Decimal("19")),
        "Artikel 1", style.fmt_qty(Decimal("1234.5")), style.fmt_price(Decimal("12.4017")), style.fmt_money(Decimal("15309.90")),
    ]
    missing = [value for value in expected if value not in text]
    assert not missing, f"{template}: not found in PDF text: {missing}"


def test_long_invoice_spans_two_pages():
    _, pages = pdf_text(render(invoice("bilingual", n_lines=40)))
    assert pages >= 2


def test_render_is_byte_identical():
    assert render(invoice("nordbuero_de")) == render(invoice("nordbuero_de"))


def test_invoice_without_po_number():
    inv = invoice("acme_us")
    text, _ = pdf_text(render(PrintedInvoice(**{**inv.__dict__, "po_number": None})))
    assert "PO-000017" not in text
```

- [ ] **Step 2: Run them to verify they fail**

Run: `cd eval && uv run pytest tests/test_templates.py -q`
Expected: `ModuleNotFoundError: No module named 'golden.templates'`.

- [ ] **Step 3: Implement the templates**

`eval/src/golden/templates/formats.py`:

```python
"""Number formatting without rounding: every significant decimal is kept, padded to min_decimals."""

from decimal import Decimal


def _number(value: Decimal, decimal_sep: str, group_sep: str, min_decimals: int) -> str:
    sign = "-" if value < 0 else ""
    whole, _, frac = format(abs(value).normalize(), "f").partition(".")
    frac = frac.ljust(min_decimals, "0")
    groups = []
    while len(whole) > 3:
        groups.insert(0, whole[-3:])
        whole = whole[:-3]
    groups.insert(0, whole)
    return sign + group_sep.join(groups) + (decimal_sep + frac if frac else "")


def de_number(value: Decimal, min_decimals: int = 0) -> str:
    """1234.5 -> '1.234,5' (German: '.' groups thousands, ',' separates decimals)."""
    return _number(value, ",", ".", min_decimals)


def en_number(value: Decimal, min_decimals: int = 0) -> str:
    """1234.5 -> '1,234.5'."""
    return _number(value, ".", ",", min_decimals)
```

`eval/src/golden/templates/base.py`:

```python
"""Shared PDF rendering for all supplier templates (reportlab platypus: automatic page breaks)."""

from collections.abc import Callable
from dataclasses import dataclass
from datetime import date
from decimal import Decimal
from io import BytesIO
from xml.sax.saxutils import escape

from reportlab.lib import colors
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import getSampleStyleSheet
from reportlab.lib.units import mm
from reportlab.platypus import Paragraph, SimpleDocTemplate, Spacer, Table, TableStyle

from golden.model import Party, PrintedInvoice


@dataclass(frozen=True)
class Style:
    key: str
    title: str
    labels: dict[str, str]  # keys: invoice_no, date, po, vat_id, pos, description, qty, unit_price, tax, net,
    #                         subtotal, tax_total, on, total
    number: Callable[[Decimal, int], str]  # de_number or en_number
    fmt_date: Callable[[date], str]
    total_pattern: str  # e.g. "{amount} €" or "{currency} {amount}"; {symbol} = currency symbol
    tax_per_line: bool = True
    supplier_on_right: bool = False

    def fmt_qty(self, value: Decimal) -> str:
        return self.number(value, 0)

    def fmt_price(self, value: Decimal) -> str:
        return self.number(value, 2)

    def fmt_money(self, value: Decimal) -> str:
        return self.number(value, 2)

    def fmt_rate(self, value: Decimal) -> str:
        return f"{self.number(value, 0)} %"

    def fmt_total(self, value: Decimal, currency: str) -> str:
        symbol = {"EUR": "€", "USD": "$", "GBP": "£"}.get(currency, currency)
        return self.total_pattern.format(amount=self.fmt_money(value), currency=currency, symbol=symbol)


def _party(party: Party, vat_label: str | None) -> str:
    text = f"<b>{escape(party.name)}</b><br/>{escape(party.street)}<br/>{escape(party.city)}"
    return text + (f"<br/>{vat_label}: {escape(party.vat_id)}" if vat_label else "")


def render(inv: PrintedInvoice, style: Style) -> bytes:
    ss = getSampleStyleSheet()
    normal = ss["Normal"]
    labels = style.labels
    buffer = BytesIO()
    # invariant=1: no creation timestamp or random document id -> identical bytes for identical input
    doc = SimpleDocTemplate(
        buffer, pagesize=A4, leftMargin=18 * mm, rightMargin=18 * mm, topMargin=18 * mm, bottomMargin=18 * mm,
        title=f"{style.title} {inv.invoice_number}", author=inv.supplier.name, invariant=1,
    )

    supplier = Paragraph(_party(inv.supplier, labels["vat_id"]), normal)
    buyer = Paragraph(_party(inv.buyer, None), normal)
    top = Table([[buyer, supplier] if style.supplier_on_right else [supplier, buyer]], colWidths=[87 * mm, 87 * mm])
    top.setStyle(TableStyle([("VALIGN", (0, 0), (-1, -1), "TOP")]))

    meta_rows = [[labels["invoice_no"], inv.invoice_number], [labels["date"], style.fmt_date(inv.invoice_date)]]
    if inv.po_number:
        meta_rows.append([labels["po"], inv.po_number])
    meta = Table(meta_rows, hAlign="LEFT")
    meta.setStyle(TableStyle([("FONT", (0, 0), (0, -1), "Helvetica-Bold"), ("FONTSIZE", (0, 0), (-1, -1), 9)]))

    head = [labels["pos"], labels["description"], labels["qty"], labels["unit_price"]]
    head += [labels["tax"]] if style.tax_per_line else []
    head += [labels["net"]]
    rows = [head]
    for n, line in enumerate(inv.lines, 1):
        row = [str(n), line.description, style.fmt_qty(line.quantity), style.fmt_price(line.unit_price)]
        row += [style.fmt_rate(line.tax_rate)] if style.tax_per_line else []
        row += [style.fmt_money(line.net_amount)]
        rows.append(row)
    widths = [12 * mm, 72 * mm, 22 * mm, 26 * mm] + ([18 * mm] if style.tax_per_line else []) + [24 * mm]
    lines = Table(rows, colWidths=widths, repeatRows=1)
    lines.setStyle(TableStyle([
        ("FONT", (0, 0), (-1, 0), "Helvetica-Bold"),
        ("FONTSIZE", (0, 0), (-1, -1), 9),
        ("LINEBELOW", (0, 0), (-1, 0), 0.5, colors.black),
        ("ALIGN", (2, 0), (-1, -1), "RIGHT"),
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
    ]))

    total_rows = [[labels["subtotal"], style.fmt_total(inv.net, inv.currency)]]
    for rate, net, tax in inv.tax_breakdown:
        label = f"{labels['tax_total']} {style.fmt_rate(rate)} {labels['on']} {style.fmt_money(net)}"
        total_rows.append([label, style.fmt_total(tax, inv.currency)])
    total_rows.append([labels["total"], style.fmt_total(inv.gross, inv.currency)])
    totals = Table(total_rows, hAlign="RIGHT")
    totals.setStyle(TableStyle([
        ("ALIGN", (1, 0), (1, -1), "RIGHT"),
        ("FONTSIZE", (0, 0), (-1, -1), 9),
        ("FONT", (0, -1), (-1, -1), "Helvetica-Bold"),
        ("LINEABOVE", (0, -1), (-1, -1), 0.5, colors.black),
    ]))

    story = [top, Spacer(1, 10 * mm), Paragraph(f"<b>{style.title}</b>", ss["Heading1"]), meta,
             Spacer(1, 6 * mm), lines, Spacer(1, 6 * mm), totals]
    doc.build(story)
    return buffer.getvalue()
```

`eval/src/golden/templates/nordbuero_de.py`:

```python
from golden.templates.base import Style
from golden.templates.formats import de_number

STYLE = Style(
    key="nordbuero_de",
    title="Rechnung",
    labels={
        "invoice_no": "Rechnungsnr.", "date": "Datum", "po": "Bestellnummer", "vat_id": "USt-IdNr.",
        "pos": "Pos.", "description": "Bezeichnung", "qty": "Menge", "unit_price": "Einzelpreis",
        "tax": "MwSt.", "net": "Betrag", "subtotal": "Nettobetrag", "tax_total": "zzgl. MwSt.",
        "on": "auf", "total": "Rechnungsbetrag",
    },
    number=de_number,
    fmt_date=lambda d: d.strftime("%d.%m.%Y"),
    total_pattern="{amount} {symbol}",
)
```

`eval/src/golden/templates/acme_us.py`:

```python
from golden.templates.base import Style
from golden.templates.formats import en_number

MONTHS = ("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

STYLE = Style(
    key="acme_us",
    title="INVOICE",
    labels={
        "invoice_no": "Invoice No.", "date": "Invoice Date", "po": "Your order ref.", "vat_id": "EIN",
        "pos": "#", "description": "Item", "qty": "Qty", "unit_price": "Unit Price", "tax": "Tax",
        "net": "Amount", "subtotal": "Subtotal", "tax_total": "Tax", "on": "on", "total": "Total Due",
    },
    number=en_number,
    fmt_date=lambda d: f"{MONTHS[d.month - 1]} {d.day}, {d.year}",  # locale-independent
    total_pattern="{symbol}{amount}",
    supplier_on_right=True,
)
```

`eval/src/golden/templates/techsource_uk.py`:

```python
from golden.templates.base import Style
from golden.templates.formats import en_number

STYLE = Style(
    key="techsource_uk",
    title="Tax Invoice",
    labels={
        "invoice_no": "Invoice number", "date": "Date", "po": "Customer PO", "vat_id": "VAT Reg. No.",
        "pos": "Line", "description": "Description", "qty": "Quantity", "unit_price": "Unit price",
        "tax": "VAT", "net": "Net", "subtotal": "Total net", "tax_total": "VAT", "on": "on", "total": "Total",
    },
    number=en_number,
    fmt_date=lambda d: d.strftime("%d/%m/%Y"),  # day first: 04/03/2026 is 4 March
    total_pattern="{amount} {currency}",
)
```

`eval/src/golden/templates/alpenpack_de.py`:

```python
from golden.templates.base import Style
from golden.templates.formats import de_number

MONATE = ("Januar", "Februar", "März", "April", "Mai", "Juni", "Juli", "August", "September", "Oktober",
          "November", "Dezember")

STYLE = Style(
    key="alpenpack_de",
    title="RECHNUNG",
    labels={
        "invoice_no": "Rechnungs-Nr.", "date": "Rechnungsdatum", "po": "Ihre Bestellung", "vat_id": "USt-ID",
        "pos": "Nr.", "description": "Artikel", "qty": "Anzahl", "unit_price": "Preis/Stk.", "tax": "",
        "net": "Gesamt", "subtotal": "Summe netto", "tax_total": "Umsatzsteuer", "on": "auf",
        "total": "Zu zahlen",
    },
    number=de_number,
    fmt_date=lambda d: f"{d.day}. {MONATE[d.month - 1]} {d.year}",
    total_pattern="{currency} {amount}",
    tax_per_line=False,  # rates appear only in the VAT summary block
    supplier_on_right=True,
)
```

`eval/src/golden/templates/bilingual.py`:

```python
from golden.templates.base import Style
from golden.templates.formats import de_number

STYLE = Style(
    key="bilingual",
    title="Rechnung / Invoice",
    labels={
        "invoice_no": "Rechnungsnummer / Invoice no.", "date": "Datum / Date", "po": "Bestellung / Order",
        "vat_id": "USt-IdNr. / VAT ID", "pos": "Pos", "description": "Beschreibung / Description",
        "qty": "Menge / Qty", "unit_price": "Preis / Price", "tax": "USt / VAT", "net": "Netto / Net",
        "subtotal": "Netto / Net total", "tax_total": "USt / VAT", "on": "auf / on", "total": "Gesamt / Total",
    },
    number=de_number,
    fmt_date=lambda d: d.isoformat(),
    total_pattern="{amount} {currency}",
)
```

`eval/src/golden/templates/__init__.py`:

```python
"""The five supplier invoice layouts. render() picks the style by the invoice's template key."""

from golden.model import PrintedInvoice
from golden.templates import acme_us, alpenpack_de, base, bilingual, nordbuero_de, techsource_uk
from golden.templates.base import Style

STYLES: dict[str, Style] = {
    m.STYLE.key: m.STYLE for m in (nordbuero_de, acme_us, techsource_uk, alpenpack_de, bilingual)
}


def render(invoice: PrintedInvoice) -> bytes:
    return base.render(invoice, STYLES[invoice.template])
```

Note on the bilingual description column: `"Beschreibung / Description"` is wider than the 72 mm column header allows at 9 pt only if the font is larger; at 9 pt it fits. If the PDF check in Step 4 shows the header wrapped, shorten it to `"Beschreibung / Item"`.

- [ ] **Step 4: Run the tests**

Run: `cd eval && uv run pytest tests/test_templates.py -q`
Expected: `10 passed`. Then open one PDF per template by hand to check the layout:
`uv run python -c "from tests.test_templates import invoice; from golden.templates import render, STYLES; [open(f'../.superpowers/{k}.pdf','wb').write(render(invoice(k))) for k in STYLES]"`
(`.superpowers/` is git-ignored.) If `tests` is not importable, run the same snippet from a scratch script.

- [ ] **Step 5: Commit (user)**

Suggest: `git add eval/src/golden/templates eval/tests/test_templates.py && git commit -m "feat(eval): five supplier PDF templates"`

---

### Task 8: Stratified dev/test split — USER EXERCISE E3

**Files:**
- Create: `eval/src/golden/split.py` (Claude writes the skeleton)
- Test: `eval/tests/test_split.py` (Claude)

**Interfaces:**
- Produces: `Split = Literal["dev", "test"]`; `SplitItem(id: str, group: str, scenario: str)` (frozen dataclass); `assign_splits(items: list[SplitItem], dev_size: int, seed: int) -> dict[str, Split]`.

- [ ] **Step 1: Claude creates the skeleton**

`eval/src/golden/split.py`:

```python
"""Dev/test split of the golden dataset.

dev  (~50): you may look at these and tune prompts against them in M3.
test (~150): only scored for the final report.
"""

import random
from collections import defaultdict
from dataclasses import dataclass
from typing import Literal

Split = Literal["dev", "test"]


@dataclass(frozen=True)
class SplitItem:
    id: str  # "inv-0042"
    group: str  # invoices of one scenario instance share a group ("g-017")
    scenario: str  # e.g. "duplicate_pair"


def assign_splits(items: list[SplitItem], dev_size: int, seed: int) -> dict[str, Split]:
    """Map every item id to "dev" or "test".

    Rules (tests/test_split.py checks them):
      1. all invoices of one group land in the same split (no leakage),
      2. stratified: every scenario gets about the same dev share (dev_size / len(items)),
      3. about dev_size invoices in dev,
      4. deterministic for a given seed.
    """
    raise NotImplementedError("M2 exercise E3")
```

- [ ] **Step 2: Claude creates the tests**

`eval/tests/test_split.py`:

```python
from collections import Counter, defaultdict

from golden.split import SplitItem, assign_splits


def dataset_like() -> list[SplitItem]:
    """Same shape as the real catalog: (scenario, groups, invoices per group)."""
    shape = [("clean_full", 50, 1), ("clean_partial_delivery", 10, 1), ("price_at_tolerance", 12, 1),
             ("partial_series_overbilling", 4, 3), ("price_above_tolerance", 18, 1), ("quantity_not_received", 14, 1),
             ("tax_rate_wrong", 14, 1), ("unmatched_freight", 14, 1), ("duplicate_pair", 6, 2),
             ("currency_mismatch", 10, 1), ("missing_po", 10, 1), ("price_and_tax", 6, 1),
             ("missing_po_duplicate", 3, 2), ("freight_and_price", 6, 1), ("currency_and_quantity", 6, 1)]
    items, n, g = [], 0, 0
    for scenario, groups, size in shape:
        for _ in range(groups):
            g += 1
            for _ in range(size):
                n += 1
                items.append(SplitItem(f"inv-{n:04d}", f"g-{g:03d}", scenario))
    return items


ITEMS = dataset_like()


def test_every_item_gets_dev_or_test():
    splits = assign_splits(ITEMS, 50, 42)
    assert set(splits) == {i.id for i in ITEMS}
    assert set(splits.values()) == {"dev", "test"}


def test_groups_stay_together():
    splits = assign_splits(ITEMS, 50, 42)
    per_group = defaultdict(set)
    for item in ITEMS:
        per_group[item.group].add(splits[item.id])
    assert all(len(s) == 1 for s in per_group.values())


def test_dev_size_is_close_to_target():
    dev = sum(1 for s in assign_splits(ITEMS, 50, 42).values() if s == "dev")
    assert 45 <= dev <= 55


def test_stratified_every_scenario_in_both_splits():
    splits = assign_splits(ITEMS, 50, 42)
    seen = defaultdict(Counter)
    for item in ITEMS:
        seen[item.scenario][splits[item.id]] += 1
    for scenario, counts in seen.items():
        assert counts["dev"] >= 1 and counts["test"] >= 1, scenario


def test_deterministic_per_seed():
    assert assign_splits(ITEMS, 50, 42) == assign_splits(ITEMS, 50, 42)
    assert assign_splits(ITEMS, 50, 42) != assign_splits(ITEMS, 50, 7)
```

- [ ] **Step 3: Run them to see the starting point**

Run: `cd eval && uv run pytest tests/test_split.py -q`
Expected: `5 failed` with `NotImplementedError: M2 exercise E3`.

- [ ] **Step 4: HAND OVER TO THE USER — E3**

Tell the user: implement `assign_splits`. Hints:

- First collect the groups: `groups: dict[str, list[SplitItem]] = defaultdict(list)` (like `groupBy` in Kotlin).
- Then, per scenario, the list of its group ids. All items of a group have the same scenario.
- Target per scenario: `round(invoices_in_scenario * dev_size / len(items))`.
- Shuffle the group ids of each scenario with `rng = random.Random(seed)` and add whole groups to dev until the target is reached; the rest goes to test.
- Iterate scenarios and groups in **sorted** order before shuffling: dict/set order must not influence the result.
- Why must a group never be split? Write the answer as a one-line comment above your loop.

<details>
<summary>Reference solution (for review only)</summary>

```python
def assign_splits(items: list[SplitItem], dev_size: int, seed: int) -> dict[str, Split]:
    rng = random.Random(seed)
    groups: dict[str, list[SplitItem]] = defaultdict(list)
    for item in items:
        groups[item.group].append(item)
    by_scenario: dict[str, list[str]] = defaultdict(list)
    for group_id, members in groups.items():
        by_scenario[members[0].scenario].append(group_id)

    share = dev_size / len(items)
    result: dict[str, Split] = {}
    for scenario in sorted(by_scenario):
        group_ids = sorted(by_scenario[scenario])
        rng.shuffle(group_ids)
        target = round(sum(len(groups[g]) for g in group_ids) * share)
        taken = 0
        # A group is one business case (e.g. both copies of a duplicate): splitting it would leak test data into dev.
        for group_id in group_ids:
            split: Split = "dev" if taken < target else "test"
            if split == "dev":
                taken += len(groups[group_id])
            for item in groups[group_id]:
                result[item.id] = split
    return result
```
</details>

- [ ] **Step 5: Verify after the user's turn**

Run: `cd eval && uv run pytest tests/test_split.py -q`
Expected: `5 passed`.

- [ ] **Step 6: Commit (user)**

Suggest: `git add eval/src/golden/split.py eval/tests/test_split.py && git commit -m "feat(eval): stratified dev/test split (E3)"`

---

### Task 9: Generator, dataset card and the real run

Requires Tasks 1–8.

**Files:**
- Create: `eval/src/golden/generate.py`, `eval/src/golden/card.py`
- Test: `eval/tests/test_generate.py`
- Output: `eval/dataset/v1/**`

**Interfaces:**
- Consumes: `ErpClient`, `build_catalog`, `assign_splits`, `render`, `SUPPLIERS`, `BUYER`, label models, `money.amount`, `money.plain`.
- Produces: `generate(erp, seed: int, out: Path) -> Manifest`; CLI `uv run python -m golden.generate [--base-url URL] [--seed N] [--out DIR]`; `card.write_card(directory: Path, labels: list[Label], manifest: Manifest) -> None`.

- [ ] **Step 1: Write the failing tests**

`eval/tests/test_generate.py`:

```python
from pathlib import Path

import pytest

from golden.erp_client import ErpError
from golden.generate import generate


class FakeErp:
    """Just enough ERP for the safety checks: reports invoices or fails on the first write."""

    def __init__(self, invoices=(), fail_on_create=False):
        self._invoices = list(invoices)
        self._fail = fail_on_create

    def list_invoices(self):
        return self._invoices

    def create_supplier(self, *args):
        if self._fail:
            raise ErpError("POST /suppliers -> 500: boom")
        return 1


def test_refuses_non_empty_erp(tmp_path: Path):
    out = tmp_path / "v1"
    with pytest.raises(SystemExit, match="already has invoices"):
        generate(FakeErp(invoices=[{"id": 1}]), 42, out)
    assert not out.exists()


def test_failed_run_keeps_previous_dataset(tmp_path: Path):
    out = tmp_path / "v1"
    out.mkdir()
    (out / "manifest.json").write_text("previous", encoding="utf-8")
    with pytest.raises(ErpError):
        generate(FakeErp(fail_on_create=True), 42, out)
    assert (out / "manifest.json").read_text(encoding="utf-8") == "previous"
    assert [p.name for p in tmp_path.iterdir()] == ["v1"]  # no temp directory left behind
```

- [ ] **Step 2: Run them to verify they fail**

Run: `cd eval && uv run pytest tests/test_generate.py -q`
Expected: `ModuleNotFoundError: No module named 'golden.generate'`.

- [ ] **Step 3: Implement `card.py`**

`eval/src/golden/card.py`:

```python
"""Dataset card: what is in the dataset, how it was made, and how NOT to read its numbers."""

from collections import Counter
from pathlib import Path

from golden.labels import ExceptionCode, Label, Manifest


def _table(header: tuple[str, ...], rows: list[tuple]) -> str:
    lines = ["| " + " | ".join(header) + " |", "|" + "---|" * len(header)]
    lines += ["| " + " | ".join(str(cell) for cell in row) + " |" for row in rows]
    return "\n".join(lines)


def write_card(directory: Path, labels: list[Label], manifest: Manifest) -> None:
    dev = [label for label in labels if label.split == "dev"]
    test = [label for label in labels if label.split == "test"]

    def with_code(subset: list[Label], code: ExceptionCode) -> int:
        return sum(1 for label in subset if any(e.code == code for e in label.expected_exceptions))

    codes = [(code.value, with_code(labels, code), with_code(dev, code), with_code(test, code)) for code in ExceptionCode]
    clean = [("matched (no exception)", *(sum(1 for l in s if not l.expected_exceptions) for s in (labels, dev, test)))]
    templates = Counter(label.template for label in labels)
    flagged = Counter(label.template for label in labels if label.expected_exceptions)
    scenarios = Counter(label.scenario for label in labels)

    text = f"""# Golden dataset {manifest.dataset_version}

Synthetic supplier invoices for evaluating erpai's AI features. Generated by `eval/src/golden`
(generator {manifest.generator_version}, seed {manifest.seed}) against ERP commit `{manifest.erp_commit}`
on {manifest.generated_at:%Y-%m-%d}.

- **{len(labels)} invoices** — dev {len(dev)}, test {len(test)}
- Labels: printed fields (`fields`), intended and actual matcher exceptions (`intent`, `expected_exceptions`)
- `expected_exceptions` is the ERP's `GET /api/invoices/{{id}}/match` output — the ground truth.

## Exception codes (invoices with at least one)

{_table(("Code", "All", "Dev", "Test"), clean + codes)}

## Templates

{_table(("Template", "Invoices", "Clean", "Flagged"), [(t, n, n - flagged[t], flagged[t]) for t, n in sorted(templates.items())])}

## Scenarios

{_table(("Scenario", "Invoices"), sorted(scenarios.items()))}

## How to read results on this dataset

- **Stratified, not representative:** exceptions are over-represented (60 % flagged) so each code has
  at least 10 examples. Real accounts-payable data is mostly clean — accuracy here is not production accuracy.
- **Synthetic and digital:** clean text-layer PDFs from 5 layouts. Scans and photos come in M11.
- **Tune on dev, report on test.** Invoices of one scenario instance (`group`) never cross splits.
"""
    (directory / "README.md").write_text(text, encoding="utf-8")
```

- [ ] **Step 4: Implement `generate.py`**

`eval/src/golden/generate.py`:

```python
"""Generate the golden dataset.

    bash eval/scripts/golden-erp.sh          # fresh ERP on port 8081 (separate terminal)
    cd eval && uv run python -m golden.generate
"""

import argparse
import shutil
import subprocess
import tempfile
from dataclasses import dataclass
from datetime import UTC, date, datetime
from decimal import Decimal
from pathlib import Path

from golden.card import write_card
from golden.erp_client import ErpClient
from golden.labels import ErpRefs, ExpectedException, Intent, InvoiceFields, Label, LineFields, Manifest, Totals
from golden.model import PrintedInvoice, PrintedLine, ScenarioSpec
from golden.money import amount, plain
from golden.scenarios import build_catalog
from golden.split import SplitItem, assign_splits
from golden.suppliers import BUYER, SUPPLIERS
from golden.templates import render

DATASET_VERSION = "v1"
GENERATOR_VERSION = "1"
DEV_SIZE = 50
DEFAULT_OUT = Path(__file__).resolve().parents[2] / "dataset" / DATASET_VERSION


@dataclass(frozen=True)
class Created:
    """One invoice created in the ERP, in dataset order."""

    dataset_id: str
    group: str
    spec: ScenarioSpec
    invoice_index: int
    invoice_id: int
    po: dict | None
    line_po_item_ids: list[int | None]


def _canonical(value: Decimal) -> Decimal:
    """Drop the ERP's storage scale (50.000 -> 50) so labels show what a person would write."""
    return Decimal(plain(value))


def _erp_commit() -> str:
    try:
        return subprocess.run(["git", "describe", "--always", "--dirty"], capture_output=True, text=True,
                              check=True).stdout.strip()
    except (OSError, subprocess.CalledProcessError):
        return "unknown"


def _master_data(erp) -> tuple[dict[str, int], dict[str, int], int]:
    supplier_ids, product_ids = {}, {}
    for key, supplier in SUPPLIERS.items():
        supplier_ids[key] = erp.create_supplier(supplier.number, supplier.party.name, supplier.currency,
                                                supplier.party.vat_id)
        for product in supplier.products:
            product_ids[product.sku] = erp.create_product(product.sku, product.name)
    return supplier_ids, product_ids, erp.warehouse_id()


def _create_all(erp, specs: list[ScenarioSpec]) -> list[Created]:
    supplier_ids, product_ids, warehouse_id = _master_data(erp)
    created: list[Created] = []
    for g, spec in enumerate(specs, 1):
        supplier_id = supplier_ids[spec.template]
        po, item_ids = None, {}
        if spec.po_lines:
            po = erp.create_purchase_order(supplier_id, spec.order_date, [
                {"productId": product_ids[l.sku], "quantity": l.quantity, "unitPrice": l.unit_price, "taxRate": l.tax_rate}
                for l in spec.po_lines
            ])
            erp.approve(po["id"])
            item_ids = {item["lineNumber"]: item["id"] for item in po["items"]}
            for receipt in spec.receipts:
                items = [{"purchaseOrderItemId": item_ids[n], "quantity": q}
                         for n, q in enumerate(receipt.quantities, 1) if q > 0]
                erp.receive(po["id"], warehouse_id, receipt.receipt_date, items)
        for k, inv in enumerate(spec.invoices):
            po_item_ids = [item_ids[l.po_line] if inv.with_po and l.po_line else None for l in inv.lines]
            body = {
                "supplierId": supplier_id, "invoiceNumber": inv.invoice_number, "invoiceDate": inv.invoice_date,
                "currency": inv.currency, "purchaseOrderId": po["id"] if inv.with_po else None,
                "items": [
                    {"purchaseOrderItemId": item_id, "description": l.description, "quantity": l.quantity,
                     "unitPrice": l.unit_price, "taxRate": l.tax_rate}
                    for l, item_id in zip(inv.lines, po_item_ids)
                ],
            }
            invoice = erp.create_invoice(body)
            created.append(Created(f"inv-{len(created) + 1:04d}", f"g-{g:03d}", spec, k, invoice["id"],
                                   po if inv.with_po else None, po_item_ids))
    return created


def _label(erp, c: Created) -> tuple[Label, PrintedInvoice]:
    """Built only after ALL invoices exist: a duplicate's first copy is flagged by the second one."""
    invoice = erp.get_invoice(c.invoice_id)
    match = erp.match(c.invoice_id)
    spec_invoice = c.spec.invoices[c.invoice_index]
    supplier = SUPPLIERS[c.spec.template]
    totals = invoice["totals"]
    printed = PrintedInvoice(
        template=c.spec.template, supplier=supplier.party, buyer=BUYER,
        invoice_number=invoice["invoiceNumber"], invoice_date=date.fromisoformat(invoice["invoiceDate"]),
        currency=invoice["currency"], po_number=c.po["poNumber"] if c.po else None,
        lines=tuple(PrintedLine(i["description"], _canonical(i["quantity"]), _canonical(i["unitPrice"]),
                                _canonical(i["taxRate"]), amount(i["netAmount"])) for i in invoice["items"]),
        net=amount(totals["subtotal"]), tax=amount(totals["taxAmount"]), gross=amount(totals["total"]),
        tax_breakdown=tuple((_canonical(b["taxRate"]), amount(b["netAmount"]), amount(b["taxAmount"]))
                            for b in totals["taxBreakdown"]),
    )
    label = Label(
        id=c.dataset_id, template=c.spec.template, scenario=c.spec.scenario, group=c.group,
        pdf=f"invoices/{c.dataset_id}.pdf",
        fields=InvoiceFields(
            supplier_name=supplier.party.name, invoice_number=printed.invoice_number,
            invoice_date=printed.invoice_date, currency=printed.currency, po_number=printed.po_number,
            lines=[LineFields(description=l.description, quantity=l.quantity, unit_price=l.unit_price,
                              tax_rate=l.tax_rate, net_amount=l.net_amount) for l in printed.lines],
            totals=Totals(net=printed.net, tax=printed.tax, gross=printed.gross),
        ),
        intent=[Intent(code=i.code, line=i.line) for i in spec_invoice.intent],
        expected_exceptions=[ExpectedException.model_validate(e) for e in match["exceptions"]],
        erp=ErpRefs(invoice_id=c.invoice_id, purchase_order_id=c.po["id"] if c.po else None,
                    line_po_item_ids=c.line_po_item_ids),
    )
    return label, printed


def generate(erp, seed: int, out: Path) -> Manifest:
    if erp.list_invoices():
        raise SystemExit("ERP already has invoices - start a fresh one with eval/scripts/golden-erp.sh")

    out.parent.mkdir(parents=True, exist_ok=True)
    tmp = Path(tempfile.mkdtemp(prefix=".golden-", dir=out.parent))  # same drive -> rename is atomic
    try:
        created = _create_all(erp, build_catalog(seed))
        built = [_label(erp, c) for c in created]
        splits = assign_splits([SplitItem(l.id, l.group, l.scenario) for l, _ in built], DEV_SIZE, seed)

        (tmp / "invoices").mkdir()
        (tmp / "labels").mkdir()
        labels = []
        for label, printed in built:
            label = label.model_copy(update={"split": splits[label.id]})
            labels.append(label)
            (tmp / label.pdf).write_bytes(render(printed))
            (tmp / "labels" / f"{label.id}.json").write_text(label.model_dump_json(indent=2) + "\n", encoding="utf-8")

        manifest = Manifest(
            dataset_version=DATASET_VERSION, seed=seed, generated_at=datetime.now(UTC).replace(microsecond=0),
            erp_commit=_erp_commit(), generator_version=GENERATOR_VERSION,
            counts={"invoices": len(labels), "groups": len({c.group for c in created}),
                    "dev": sum(1 for l in labels if l.split == "dev"),
                    "test": sum(1 for l in labels if l.split == "test")},
            splits={s: [l.id for l in labels if l.split == s] for s in ("dev", "test")},
        )
        (tmp / "manifest.json").write_text(manifest.model_dump_json(indent=2) + "\n", encoding="utf-8")
        write_card(tmp, labels, manifest)

        if out.exists():
            shutil.rmtree(out)
        tmp.rename(out)
        return manifest
    finally:
        if tmp.exists():
            shutil.rmtree(tmp)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--base-url", default="http://localhost:8081")
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--out", type=Path, default=DEFAULT_OUT)
    args = parser.parse_args()
    manifest = generate(ErpClient(args.base_url), args.seed, args.out)
    print(f"Wrote {manifest.counts['invoices']} invoices to {args.out} "
          f"(dev {manifest.counts['dev']}, test {manifest.counts['test']}).")


if __name__ == "__main__":
    main()
```

- [ ] **Step 5: Run the safety tests**

Run: `cd eval && uv run pytest tests/test_generate.py -q`
Expected: `2 passed`.

- [ ] **Step 6: Generate the real dataset**

Precondition: the golden ERP from Task 3 Step 6 runs on port 8081 with an **empty** database (restart `bash eval/scripts/golden-erp.sh` if anything was created in it).
Run: `cd eval && uv run python -m golden.generate`
Expected: `Wrote 200 invoices to ...\eval\dataset\v1 (dev 52, test 148).` (dev between 45 and 55).
Then: `ls eval/dataset/v1/invoices | wc -l` → `200`; `ls eval/dataset/v1/labels | wc -l` → `200`.

- [ ] **Step 7: Check reproducibility once**

Restart `golden-erp.sh` (fresh DB), regenerate into a scratch dir:
`cd eval && uv run python -m golden.generate --out ../.superpowers/v1-again`
Run: `diff -r eval/dataset/v1/labels .superpowers/v1-again/labels && cmp eval/dataset/v1/invoices/inv-0001.pdf .superpowers/v1-again/invoices/inv-0001.pdf && echo SAME`
Expected: `SAME` (ERP ids are identical because both runs start from an empty database; `manifest.json` differs only in `generated_at`). Delete `.superpowers/v1-again` afterwards.

- [ ] **Step 8: Commit (user)**

Suggest: `git add eval/src/golden/generate.py eval/src/golden/card.py eval/tests/test_generate.py eval/dataset/v1 && git commit -m "feat(eval): golden dataset generator and dataset v1"`

---

### Task 10: Dataset checks (E4) and docs

**Files:**
- Create: `eval/tests/test_dataset.py` (Claude: fixtures + checks 2–4; user: checks 1 and 5), `eval/README.md`
- Modify: `README.md` (repo root), `docs/superpowers/specs/2026-10-10-m2-golden-dataset-design.md` (record rulings)

**Interfaces:**
- Consumes: `golden.labels.Label, Manifest`, `golden.templates.STYLES`, `golden.money.line_net`, dataset files.

- [ ] **Step 1: Claude creates `test_dataset.py` with the E4 stubs**

`eval/tests/test_dataset.py`:

```python
"""Checks over the finished dataset in dataset/v1. They test the DATA, not the generator code."""

import io
from collections import Counter, defaultdict
from decimal import Decimal
from pathlib import Path

import pdfplumber
import pytest

from golden.labels import ExceptionCode, Label, Manifest
from golden.money import line_net
from golden.templates import STYLES

DATASET = Path(__file__).resolve().parents[1] / "dataset" / "v1"
pytestmark = pytest.mark.skipif(not (DATASET / "manifest.json").exists(), reason="dataset not generated yet")


@pytest.fixture(scope="module")
def labels() -> list[Label]:
    return [Label.model_validate_json(p.read_text(encoding="utf-8")) for p in sorted((DATASET / "labels").glob("*.json"))]


@pytest.fixture(scope="module")
def manifest() -> Manifest:
    return Manifest.model_validate_json((DATASET / "manifest.json").read_text(encoding="utf-8"))


# ---- E4: your exercise ----------------------------------------------------------------------


def test_intent_matches_erp(labels):
    """Check 1: for every invoice, intent and expected_exceptions name the same (code, line) pairs,
    in the same order. Report ALL mismatching invoices in the assertion message, not just the first."""
    pytest.fail("M2 exercise E4: implement check 1")


def test_no_leakage_between_dev_and_test(labels, manifest):
    """Check 5: dev and test (from the manifest) are disjoint and together cover all label ids; each
    label's own `split` agrees with the manifest; all invoices of a group share one split; dev has 45-55."""
    pytest.fail("M2 exercise E4: implement check 5")


# ---- prepared checks ------------------------------------------------------------------------


def test_labels_are_internally_consistent(labels):
    """Check 2 (the Pydantic validators already enforce it on load; this states it explicitly)."""
    assert len(labels) == 200
    for label in labels:
        f = label.fields
        assert all(l.net_amount == line_net(l.quantity, l.unit_price) for l in f.lines), label.id
        assert f.totals.net == sum((l.net_amount for l in f.lines), Decimal(0)), label.id
        assert f.totals.gross == f.totals.net + f.totals.tax, label.id


def test_every_label_value_is_printed_on_its_pdf(labels):
    """Check 3: an extractor can only be blamed for values that are actually on the page."""
    problems = []
    for label in labels:
        style = STYLES[label.template]
        f = label.fields
        with pdfplumber.open(io.BytesIO((DATASET / label.pdf).read_bytes())) as pdf:
            text = " ".join(" ".join((page.extract_text() or "") for page in pdf.pages).split())
        expected = [f.invoice_number, style.fmt_date(f.invoice_date), f.supplier_name,
                    style.fmt_total(f.totals.net, f.currency), style.fmt_total(f.totals.gross, f.currency)]
        expected += [f.po_number] if f.po_number else []
        for line in f.lines:
            expected += [line.description, style.fmt_qty(line.quantity), style.fmt_price(line.unit_price),
                         style.fmt_rate(line.tax_rate), style.fmt_money(line.net_amount)]
        missing = [value for value in expected if value not in text]
        if missing:
            problems.append((label.id, label.template, missing))
    assert not problems, "\n".join(map(str, problems))


def test_coverage(labels):
    """Check 4: enough examples per code, and every template has clean and flagged invoices."""
    per_code = Counter(code for l in labels for code in {e.code for e in l.expected_exceptions})
    assert all(per_code[code] >= 10 for code in ExceptionCode), per_code
    per_template = defaultdict(Counter)
    for l in labels:
        per_template[l.template]["flagged" if l.expected_exceptions else "clean"] += 1
    assert len(per_template) == 5
    assert all(c["clean"] > 0 and c["flagged"] > 0 for c in per_template.values()), per_template
```

- [ ] **Step 2: Run the prepared checks**

Run: `cd eval && uv run pytest tests/test_dataset.py -q`
Expected: 3 passed (checks 2–4), 2 failed with `M2 exercise E4`. If check 3 reports missing values, fix the template or formatter (not the label) and regenerate (Task 9 Step 6).

- [ ] **Step 3: HAND OVER TO THE USER — E4**

Tell the user: implement the two stubbed checks. Hints:

- Turn each side into a comparable list: `[(i.code, i.line) for i in label.intent]` and `[(e.code, e.lineNumber) for e in label.expected_exceptions]`. `ExceptionCode` is a `StrEnum`, so it compares equal to the string.
- Collect mismatches in a list and assert it is empty at the end: `assert not mismatches, "\n".join(map(str, mismatches))` — one failing run then shows every bad label (better than stopping at the first).
- Sets: `dev & test` (intersection), `dev | test` (union) — like Kotlin `intersect` / `union`.
- Grouping: `defaultdict(set)` keyed by `label.group`, collecting `label.split`.
- Afterwards, prove the checks work: temporarily change one label's `intent` code in `dataset/v1/labels/` and watch check 1 fail with that id; then `git checkout` the file back.

<details>
<summary>Reference solution (for review only)</summary>

```python
def test_intent_matches_erp(labels):
    mismatches = []
    for label in labels:
        intended = [(i.code, i.line) for i in label.intent]
        actual = [(e.code, e.lineNumber) for e in label.expected_exceptions]
        if intended != actual:
            mismatches.append((label.id, label.scenario, intended, actual))
    assert not mismatches, "\n".join(map(str, mismatches))


def test_no_leakage_between_dev_and_test(labels, manifest):
    dev, test = set(manifest.splits["dev"]), set(manifest.splits["test"])
    assert not dev & test
    assert dev | test == {label.id for label in labels}
    assert all(label.split == ("dev" if label.id in dev else "test") for label in labels)
    splits_per_group = defaultdict(set)
    for label in labels:
        splits_per_group[label.group].add(label.split)
    assert all(len(splits) == 1 for splits in splits_per_group.values())
    assert 45 <= len(dev) <= 55
```
</details>

- [ ] **Step 4: Verify after the user's turn**

Run: `cd eval && uv run pytest -q`
Expected: everything passes (all unit tests + 5 dataset checks). If check 1 fails, read the reported (id, scenario, intended, actual) tuples: a mismatch is a real finding — either a scenario function's intent is wrong (fix the scenario, regenerate) or the ERP behaves differently than assumed (discuss before changing anything).

- [ ] **Step 5: Write `eval/README.md`**

```markdown
# eval — golden dataset (M2)

Synthetic, labelled supplier invoices for measuring erpai's AI features. The dataset itself lives in
[`dataset/v1/`](dataset/v1/README.md) and is committed, so evals need neither Docker nor the backend.

## Layout

- `src/golden/` — generator: `scenarios.py` (what to test), `erp_client.py` (creates the data in the
  ERP), `templates/` (5 supplier PDF layouts), `labels.py` (label format), `split.py` (dev/test)
- `tests/` — unit tests and `test_dataset.py` (checks over the finished dataset)
- `dataset/v1/` — `invoices/*.pdf`, `labels/*.json`, `manifest.json`, `README.md` (dataset card)

## Run the checks

    cd eval
    uv sync
    uv run pytest

## Regenerate

Labels come from the ERP's three-way matcher, so generation needs a fresh ERP:

    bash eval/scripts/golden-erp.sh                  # terminal 1: empty DB erpai_golden, backend on :8081
    cd eval && uv run python -m golden.generate      # terminal 2

The generator refuses to run against an ERP that already has invoices, and replaces `dataset/v1/`
only when the whole run succeeded. If the matching rules change, generate `v2` instead of overwriting
`v1`, so earlier eval reports stay comparable.
```

- [ ] **Step 6: Update the root README status table**

In `README.md`, add a row after "Three-way invoice matching":

```markdown
| Golden dataset (eval/, 200 labelled invoices) | ✅ | – |
```

- [ ] **Step 7: Record the plan-time rulings in the spec**

In `docs/superpowers/specs/2026-10-10-m2-golden-dataset-design.md`:
- Layout: remove `faker` from the dependency list (addresses are fixed fictional data in `suppliers.py`).
- Layout: add `suppliers.py`, `model.py`, `money.py` to the tree.
- Label format: `"group": "g-042"` instead of `null`, and the sentence: "`group` is always set: the scenario instance id; invoices of one group share a split."
- Generation run step 1: "starts the backend **on port 8081** with …".
- Generation run step 3: "~40 products" instead of "~25".

- [ ] **Step 8: Commit (user)**

Suggest: `git add eval/tests/test_dataset.py eval/README.md README.md docs/superpowers/specs/2026-10-10-m2-golden-dataset-design.md && git commit -m "test(eval): dataset checks (E4) and M2 docs"`

Then open the PR `m2-golden-dataset` → `main` on GitHub (user).
