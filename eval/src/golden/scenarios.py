"""Scenario catalog: what each golden-dataset invoice is meant to test.

A scenario function takes a Ctx and returns a ScenarioSpec. `intent` is what the scenario MEANS to
trigger; the ERP's match result becomes the label, and tests/test_dataset.py checks that both agree.

Pattern of every scenario:  clean story  ->  change one thing  ->  add_intent(...)  ->  named(...)
Everything is immutable: "changing" always means building a copy (Kotlin: data class .copy(...)).
"""

import random
from collections.abc import Callable
from dataclasses import dataclass, replace  # replace(obj, field=x) = Kotlin obj.copy(field = x)
from datetime import date, timedelta
from decimal import ROUND_FLOOR, ROUND_HALF_UP, Decimal

from golden.model import Intent, InvLine, InvoiceSpec, PoLine, Receipt, ScenarioSpec, Supplier

# Date window of the whole dataset (fixed -> reproducible).
FIRST_ORDER_DATE = date(2026, 1, 5)
LAST_ORDER_DATE = date(2026, 8, 15)
LAST_INVOICE_DATE = date(2026, 9, 30)

# All even, so every quantity splits into two whole halves (duplicate_pair).
QUANTITIES = tuple(Decimal(q) for q in (2, 4, 6, 8, 10, 12, 20, 24, 40, 50, 60, 80, 100, 120, 200, 250))

# "The wrong one" for currency / tax scenarios: always different from the original.
OTHER_CURRENCY = {"EUR": "USD", "USD": "EUR", "GBP": "EUR"}
OTHER_TAX_RATE = {Decimal(19): Decimal(7), Decimal(7): Decimal(19), Decimal(0): Decimal(19), Decimal(20): Decimal(5)}


# INPUT: what a scenario function may use.
@dataclass
class Ctx:
    """What a scenario function may use: its own random generator, its supplier, fresh invoice numbers."""

    rng: random.Random  # seeded -> same story every run
    supplier: Supplier
    next_invoice_number: Callable[[], str]  # a function with no args returning str (Kotlin: () -> String)


# ---- building blocks ------------------------------------------------------------------------


# HELPER: a correct story — PO, everything received, invoice at PO prices. Start of every scenario.
def clean(ctx: Ctx, n_lines: int | None = None, quantities: list[Decimal] | None = None) -> ScenarioSpec:
    """A PO, fully received, billed exactly at PO prices — the starting point of every scenario."""
    rng, supplier = ctx.rng, ctx.supplier
    # how many lines: from `quantities`, else `n_lines`, else random 1..5
    n = len(quantities) if quantities else (n_lines or rng.randint(1, min(5, len(supplier.products))))
    products = rng.sample(supplier.products, n)  # n different products (Kotlin: shuffled().take(n))
    qtys = list(quantities) if quantities else [rng.choice(QUANTITIES) for _ in products]
    po_lines = tuple(PoLine(p.sku, q, p.unit_price, p.tax_rate) for p, q in zip(products, qtys))

    # dates: order -> receipt 2-10 days later -> invoice 0-14 days after that
    order_date = FIRST_ORDER_DATE + timedelta(days=rng.randint(0, (LAST_ORDER_DATE - FIRST_ORDER_DATE).days))
    receipt_date = order_date + timedelta(days=rng.randint(2, 10))
    invoice_date = receipt_date + timedelta(days=rng.randint(0, 14))

    # invoice line n points to PO line n and copies quantity, price and tax
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
    receipt = Receipt(receipt_date, tuple(qtys))  # everything ordered arrived
    return ScenarioSpec("clean", supplier.key, order_date, po_lines, (receipt,), (invoice,))


# HELPER: set the scenario name.
def named(spec: ScenarioSpec, scenario: str) -> ScenarioSpec:
    return replace(spec, scenario=scenario)


# HELPER: copy with some fields of one invoice changed, e.g. replace_invoice(spec, currency="USD").
def replace_invoice(spec: ScenarioSpec, index: int = 0, **changes) -> ScenarioSpec:
    """Copy of `spec` with fields of invoice `index` changed (dataclasses are immutable)."""
    # **changes = any keyword arguments, collected into a dict and passed on to replace(...)
    invoices = list(spec.invoices)
    invoices[index] = replace(invoices[index], **changes)
    return replace(spec, invoices=tuple(invoices))


# HELPER: copy with some fields of one invoice LINE changed, e.g. replace_line(spec, 2, tax_rate=Decimal(7)).
def replace_line(spec: ScenarioSpec, line: int, invoice: int = 0, **changes) -> ScenarioSpec:
    """Copy of `spec` with fields of invoice line `line` (1-based) of invoice `invoice` changed."""
    lines = list(spec.invoices[invoice].lines)
    lines[line - 1] = replace(lines[line - 1], **changes)
    return replace_invoice(spec, invoice, lines=tuple(lines))


# HELPER: record the expected exception(s), sorted like the ERP matcher returns them.
def add_intent(spec: ScenarioSpec, *intents: Intent, invoice: int = 0) -> ScenarioSpec:
    """Add intents to one invoice, kept in the matcher's output order."""
    # *intents = varargs (Kotlin: vararg intents: Intent)
    merged = spec.invoices[invoice].intent + intents
    return replace_invoice(spec, invoice, intent=tuple(sorted(merged, key=Intent.sort_key)))


# HELPER: a random line number (1-based) of the first invoice.
def pick_line(ctx: Ctx, spec: ScenarioSpec) -> int:
    """A random line number (1-based) of the first invoice."""
    return ctx.rng.randint(1, len(spec.invoices[0].lines))


# HELPER: a smaller quantity (50-90 %), for partial deliveries.
def part_of(ctx: Ctx, quantity: Decimal) -> Decimal:
    """50-90 % of `quantity`, rounded down: at least 1 and always less than `quantity` (which is >= 2)."""
    pct = ctx.rng.choice((50, 60, 75, 80, 90))
    return max(Decimal(1), (quantity * pct / 100).to_integral_value(rounding=ROUND_FLOOR))


# HELPER: an extra freight line that was never ordered (not linked to a PO line).
def freight_line(ctx: Ctx) -> InvLine:
    """A charge nobody ordered: not linked to any PO line."""
    price = Decimal(ctx.rng.choice((25, 38, 45, 60, 85, 120)))
    return InvLine(None, "Freight / Fracht", Decimal(1), price, ctx.supplier.products[0].tax_rate)


# HELPER (internal): copy with other received quantities.
def _with_received(spec: ScenarioSpec, quantities: tuple[Decimal, ...]) -> ScenarioSpec:
    return replace(spec, receipts=(replace(spec.receipts[0], quantities=quantities),))


# ---- scenarios built by Claude --------------------------------------------------------------


# SCENARIO: clean (no exception). Long 13-16 line invoices for the bilingual supplier.
def clean_full(ctx: Ctx) -> ScenarioSpec:
    """Clean. Bilingual invoices are long (13-16 lines) so the table runs over a page break."""
    n_lines = ctx.rng.randint(13, 16) if ctx.supplier.key == "bilingual" else None
    return named(clean(ctx, n_lines=n_lines), "clean_full")


# SCENARIO: clean (no exception). Only part arrived, and only that part is billed.
def clean_partial_delivery(ctx: Ctx) -> ScenarioSpec:
    """Clean: only part of each line arrived, and exactly that part is billed."""
    spec = clean(ctx)
    received = tuple(part_of(ctx, q) for q in spec.receipts[0].quantities)
    spec = _with_received(spec, received)
    for n, quantity in enumerate(received, 1):
        spec = replace_line(spec, n, quantity=quantity)
    return named(spec, "clean_partial_delivery")


# SCENARIO: one line partly delivered but billed in full -> QUANTITY_MISMATCH.
def quantity_not_received(ctx: Ctx) -> ScenarioSpec:
    """QUANTITY_MISMATCH: one line only partly delivered but billed in full."""
    spec = clean(ctx)
    n = pick_line(ctx, spec)
    received = list(spec.receipts[0].quantities)
    received[n - 1] = part_of(ctx, received[n - 1])
    spec = _with_received(spec, tuple(received))
    return named(add_intent(spec, Intent("QUANTITY_MISMATCH", n)), "quantity_not_received")


# SCENARIO: one line with a different VAT rate than ordered -> TAX_MISMATCH.
def tax_rate_wrong(ctx: Ctx) -> ScenarioSpec:
    """TAX_MISMATCH: one line billed with another VAT rate than ordered."""
    spec = clean(ctx)
    n = pick_line(ctx, spec)
    wrong = OTHER_TAX_RATE[spec.invoices[0].lines[n - 1].tax_rate]
    return named(add_intent(replace_line(spec, n, tax_rate=wrong), Intent("TAX_MISMATCH", n)), "tax_rate_wrong")


# SCENARIO: same numbers, other currency -> CURRENCY_MISMATCH.
def currency_mismatch(ctx: Ctx) -> ScenarioSpec:
    """CURRENCY_MISMATCH: same numbers, other currency (the matcher then skips the price rule)."""
    spec = replace_invoice(clean(ctx), currency=OTHER_CURRENCY[ctx.supplier.currency])
    return named(add_intent(spec, Intent("CURRENCY_MISMATCH")), "currency_mismatch")


# SCENARIO: invoice without any purchase order -> MISSING_PO.
def missing_po(ctx: Ctx) -> ScenarioSpec:
    """MISSING_PO: goods billed without any purchase order."""
    spec = clean(ctx)
    lines = tuple(replace(line, po_line=None) for line in spec.invoices[0].lines)
    spec = replace(replace_invoice(spec, with_po=False, lines=lines), po_lines=(), receipts=())
    return named(add_intent(spec, Intent("MISSING_PO")), "missing_po")


# SCENARIO (combination): wrong price AND wrong VAT on the same line -> PRICE_MISMATCH + TAX_MISMATCH.
def price_and_tax(ctx: Ctx) -> ScenarioSpec:
    """PRICE_MISMATCH + TAX_MISMATCH on the same line."""
    spec = price_above_tolerance(ctx)  # uses your E2 function
    n = spec.invoices[0].intent[0].line
    wrong = OTHER_TAX_RATE[spec.invoices[0].lines[n - 1].tax_rate]
    return named(add_intent(replace_line(spec, n, tax_rate=wrong), Intent("TAX_MISMATCH", n)), "price_and_tax")


# SCENARIO (combination): wrong price + an unordered freight line -> PRICE_MISMATCH + UNMATCHED_LINE.
def freight_and_price(ctx: Ctx) -> ScenarioSpec:
    """PRICE_MISMATCH on one line plus an unordered freight line."""
    spec = price_above_tolerance(ctx)  # uses your E2 function
    lines = spec.invoices[0].lines + (freight_line(ctx),)
    spec = replace_invoice(spec, lines=lines)
    return named(add_intent(spec, Intent("UNMATCHED_LINE", len(lines))), "freight_and_price")


# SCENARIO (combination): invoice without PO, sent twice -> MISSING_PO + DUPLICATE_INVOICE on both.
def missing_po_duplicate(ctx: Ctx) -> ScenarioSpec:
    """MISSING_PO + DUPLICATE_INVOICE: an invoice without PO, sent twice."""
    spec = missing_po(ctx)
    first = spec.invoices[0]
    copy = replace(first, invoice_date=first.invoice_date + timedelta(days=ctx.rng.randint(3, 10)))
    spec = replace(spec, invoices=(first, copy))
    spec = add_intent(spec, Intent("DUPLICATE_INVOICE"), invoice=0)
    spec = add_intent(spec, Intent("DUPLICATE_INVOICE"), invoice=1)
    return named(spec, "missing_po_duplicate")


# SCENARIO (combination): other currency + billed before everything arrived -> CURRENCY_MISMATCH + QUANTITY_MISMATCH.
def currency_and_quantity(ctx: Ctx) -> ScenarioSpec:
    """CURRENCY_MISMATCH + QUANTITY_MISMATCH: billed in another currency before everything arrived."""
    spec = replace_invoice(quantity_not_received(ctx), currency=OTHER_CURRENCY[ctx.supplier.currency])
    return named(add_intent(spec, Intent("CURRENCY_MISMATCH")), "currency_and_quantity")


# ---- E2: your exercise ----------------------------------------------------------------------
#
# Each function starts from clean(ctx) (or clean(ctx, quantities=[...])), changes what the supplier
# "prints", adds the intent with add_intent(...), and returns named(spec, "<function name>").
# tests/test_scenarios_exercise.py tells you when you are done.


# E2 (yours): one line priced 1-10 % off -> PRICE_MISMATCH.
def price_above_tolerance(ctx: Ctx) -> ScenarioSpec:
    """PRICE_MISMATCH: one line billed 1, 2, 3, 5, 7 or 10 % above or below the PO price,
    rounded to cents — always clearly outside the 0.5 % tolerance."""
    spec = clean(ctx)
    n = pick_line(ctx, spec)
    po_price = spec.po_lines[n - 1].unit_price
    # e.g. +3 % or -7 %: a size and a direction, both from the seeded rng
    pct = Decimal(ctx.rng.choice((1, 2, 3, 5, 7, 10))) * ctx.rng.choice((1, -1))
    # suppliers print whole cents, so round like the ERP (half-up)
    new_price = (po_price * (100 + pct) / 100).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)
    spec = replace_line(spec, n, unit_price=new_price)
    return named(add_intent(spec, Intent("PRICE_MISMATCH", n)), "price_above_tolerance")


# E2 (yours): one line exactly 0.5 % off -> must stay CLEAN (near miss).
def price_at_tolerance(ctx: Ctx) -> ScenarioSpec:
    """Near miss that must stay CLEAN: one line exactly 0.5 % above or below the PO price."""
    spec = clean(ctx)
    n = pick_line(ctx, spec)
    factor = Decimal("1.005") if ctx.rng.random() < 0.5 else Decimal("0.995")  # Kotlin: if (...) a else b
    # NO rounding: even-cent prices x 1.005 have at most 4 decimals (4.18 -> 4.2009), which the ERP stores
    # exactly. The ERP rule is strictly "> 0.5 %", so this line is on the edge but still clean -> no intent.
    spec = replace_line(spec, n, unit_price=spec.po_lines[n - 1].unit_price * factor)
    return named(spec, "price_at_tolerance")


# E2 (yours): clean invoice + freight line at the end -> UNMATCHED_LINE.
def unmatched_freight(ctx: Ctx) -> ScenarioSpec:
    """UNMATCHED_LINE: a clean invoice plus a freight line at the end."""
    spec = clean(ctx)
    lines = spec.invoices[0].lines + (freight_line(ctx),)  # tuple + 1-element tuple (note the comma)
    spec = replace_invoice(spec, lines=lines)
    return named(add_intent(spec, Intent("UNMATCHED_LINE", len(lines))), "unmatched_freight")


# E2 (yours): two invoices with the same number, each billing half -> DUPLICATE_INVOICE on both.
def duplicate_pair(ctx: Ctx) -> ScenarioSpec:
    """DUPLICATE_INVOICE on both invoices: the supplier bills each line in two halves but reuses the
    same invoice number for the second invoice (3-10 days later). Quantities still add up, so the
    duplicate is the ONLY exception."""
    # Why halves? A second FULL copy would bill more than was received, so the ERP would also report
    # QUANTITY_MISMATCH on every line — and the scenario would no longer test the duplicate rule alone.
    spec = clean(ctx)
    first = spec.invoices[0]
    # every line billed with half the quantity (QUANTITIES are all even -> whole halves)
    first = replace(first, lines=tuple(replace(line, quantity=line.quantity / 2) for line in first.lines))
    # second invoice: same number and same lines, 3-10 days later
    second = replace(first, invoice_date=first.invoice_date + timedelta(days=ctx.rng.randint(3, 10)))
    spec = replace(spec, invoices=(first, second))
    spec = add_intent(spec, Intent("DUPLICATE_INVOICE"), invoice=0)
    spec = add_intent(spec, Intent("DUPLICATE_INVOICE"), invoice=1)
    return named(spec, "duplicate_pair")


# E2 (yours): three invoices 60 % + 40 % + a few extra units -> QUANTITY_MISMATCH on the third.
def partial_series_overbilling(ctx: Ctx) -> ScenarioSpec:
    """A single-line PO of 50, 100 or 200 units, fully received, billed in three invoices with fresh
    numbers: 60 %, then 40 % (7 days later), then 1-5 extra units (14 days after the first).
    The first two are clean; the third gets QUANTITY_MISMATCH on line 1."""
    spec = clean(ctx, quantities=[ctx.rng.choice((Decimal(50), Decimal(100), Decimal(200)))])
    first = spec.invoices[0]
    line = first.lines[0]
    q = line.quantity

    # local helper function (Kotlin: a local fun): a copy of the first invoice with one changed line
    def bill(quantity: Decimal, days: int, number: str) -> InvoiceSpec:
        return replace(first, invoice_number=number, invoice_date=first.invoice_date + timedelta(days=days),
                       lines=(replace(line, quantity=quantity),))

    invoices = (
        bill(q * 3 / 5, 0, first.invoice_number),                         # 60 %  -> clean
        bill(q * 2 / 5, 7, ctx.next_invoice_number()),                   # 40 %  -> clean, PO now fully billed
        bill(Decimal(ctx.rng.randint(1, 5)), 14, ctx.next_invoice_number()),  # extra -> over-billed
    )
    spec = replace(spec, invoices=invoices)
    return named(add_intent(spec, Intent("QUANTITY_MISMATCH", 1), invoice=2), "partial_series_overbilling")
