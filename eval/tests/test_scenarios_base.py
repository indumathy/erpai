# Tests for the scenario building blocks and Claude's scenario functions (golden/scenarios.py).
# Pure data, no ERP: each test checks that the generated story really matches its intent.
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

# Every parametrised test runs once per seed: 25 different random stories per scenario.
SEEDS = range(25)


# HELPER: builds the input every scenario needs (random generator + supplier + invoice numbers).
def ctx(seed: int, key: str | None = None) -> Ctx:
    numbers = itertools.count(1)  # endless counter 1, 2, 3, ... (Kotlin: generateSequence(1) { it + 1 }.iterator())
    supplier = SUPPLIERS[key or TEMPLATE_KEYS[seed % len(TEMPLATE_KEYS)]]
    # lambda = a small anonymous function (Kotlin: { "T-${numbers.next()}" }).
    return Ctx(random.Random(seed), supplier, lambda: f"T-{next(numbers)}")


# @pytest.mark.parametrize = JUnit @ParameterizedTest + @ValueSource: the test runs once per seed.

# TEST: a clean story bills exactly what was ordered and received — no exception.
@pytest.mark.parametrize("seed", SEEDS)
def test_clean_bills_exactly_what_was_ordered_and_received(seed):
    spec = clean(ctx(seed))
    inv = spec.invoices[0]
    assert inv.intent == () and inv.with_po
    # everything ordered was received
    assert [l.quantity for l in spec.po_lines] == list(spec.receipts[0].quantities)
    # every invoice line points to its PO line and copies quantity, price and tax exactly
    for n, line in enumerate(inv.lines, 1):  # enumerate(..., 1) = withIndex() starting at 1
        po = spec.po_lines[line.po_line - 1]
        assert line.po_line == n
        assert (line.quantity, line.unit_price, line.tax_rate) == (po.quantity, po.unit_price, po.tax_rate)
    # dates in a realistic order: order <= receipt <= invoice <= end of the dataset period
    assert spec.order_date <= spec.receipts[0].receipt_date <= inv.invoice_date <= LAST_INVOICE_DATE


# TEST: we can force the quantities (needed for E2 scenarios).
def test_clean_with_explicit_quantities():
    spec = clean(ctx(1), quantities=[Decimal(100)])
    assert [l.quantity for l in spec.po_lines] == [Decimal(100)]


# TEST: long invoices (13-16 lines, page break) only for the bilingual supplier.
@pytest.mark.parametrize("seed", SEEDS)
def test_clean_full_is_long_only_for_bilingual(seed):
    spec = clean_full(ctx(seed))
    assert spec.scenario == "clean_full"
    if spec.template == "bilingual":
        assert 13 <= len(spec.invoices[0].lines) <= 16
    else:
        assert len(spec.invoices[0].lines) <= 5


# TEST: only part arrived, and only that part is billed — still clean.
@pytest.mark.parametrize("seed", SEEDS)
def test_partial_delivery_bills_what_arrived(seed):
    spec = clean_partial_delivery(ctx(seed))
    received = spec.receipts[0].quantities
    assert [l.quantity for l in spec.invoices[0].lines] == list(received)
    # zip pairs two lists element by element (Kotlin: received.zip(poLines))
    assert all(0 < r < po.quantity for r, po in zip(received, spec.po_lines))
    assert spec.invoices[0].intent == ()


# TEST: exactly one line was short-delivered, and the intent names that line.
@pytest.mark.parametrize("seed", SEEDS)
def test_quantity_not_received(seed):
    spec = quantity_not_received(ctx(seed))
    # (intent,) = ... unpacks a 1-element tuple and fails if there is not exactly one intent.
    (intent,) = spec.invoices[0].intent
    # line numbers where less arrived than ordered — must be exactly one, the intent's line
    short = [n for n, (r, po) in enumerate(zip(spec.receipts[0].quantities, spec.po_lines), 1) if r < po.quantity]
    assert intent == Intent("QUANTITY_MISMATCH", short[0]) and len(short) == 1


# TEST: the intent's line really has a different VAT rate than the PO.
@pytest.mark.parametrize("seed", SEEDS)
def test_tax_rate_wrong(seed):
    spec = tax_rate_wrong(ctx(seed))
    (intent,) = spec.invoices[0].intent
    line = spec.invoices[0].lines[intent.line - 1]
    assert intent.code == "TAX_MISMATCH"
    assert line.tax_rate != spec.po_lines[line.po_line - 1].tax_rate


# TEST: the invoice uses another currency than the supplier.
@pytest.mark.parametrize("seed", SEEDS)
def test_currency_mismatch(seed):
    c = ctx(seed)
    spec = currency_mismatch(c)
    assert spec.invoices[0].currency != c.supplier.currency
    assert spec.invoices[0].intent == (Intent("CURRENCY_MISMATCH"),)  # (x,) = 1-element tuple


# TEST: no PO, no receipt, no line linked — only MISSING_PO.
@pytest.mark.parametrize("seed", SEEDS)
def test_missing_po_has_no_purchase_order_at_all(seed):
    spec = missing_po(ctx(seed))
    inv = spec.invoices[0]
    assert spec.po_lines == () and spec.receipts == ()
    assert not inv.with_po and all(l.po_line is None for l in inv.lines)
    assert inv.intent == (Intent("MISSING_PO"),)
