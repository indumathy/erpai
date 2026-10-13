# Tests for the five E2 scenario functions in golden/scenarios.py.
# Same idea as test_scenarios_base.py: 25 random stories per scenario, each must match its intent.
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


# HELPER: input for a scenario (seeded random generator, supplier rotating over the 5, invoice numbers T-1, ...).
def ctx(seed: int) -> Ctx:
    numbers = itertools.count(1)
    return Ctx(random.Random(seed), SUPPLIERS[TEMPLATE_KEYS[seed % 5]], lambda: f"T-{next(numbers)}")


# HELPER: (line number, line) of every invoice line whose price differs from its PO line.
def changed_prices(spec):
    inv = spec.invoices[0]
    return [(n, l) for n, l in enumerate(inv.lines, 1) if l.unit_price != spec.po_lines[l.po_line - 1].unit_price]


# TEST: exactly one line priced more than 0.5 % off, in whole cents -> PRICE_MISMATCH on that line.
@pytest.mark.parametrize("seed", SEEDS)
def test_price_above_tolerance(seed):
    spec = price_above_tolerance(ctx(seed))
    assert spec.scenario == "price_above_tolerance"
    [(n, line)] = changed_prices(spec)  # unpacking fails unless exactly one line changed
    po_price = spec.po_lines[line.po_line - 1].unit_price
    assert abs(line.unit_price - po_price) * 100 > Decimal("0.5") * po_price
    assert line.unit_price == line.unit_price.quantize(Decimal("0.01"))
    assert spec.invoices[0].intent == (Intent("PRICE_MISMATCH", n),)


# TEST: exactly one line priced EXACTLY 0.5 % off -> must stay clean (no intent).
@pytest.mark.parametrize("seed", SEEDS)
def test_price_at_tolerance_is_exactly_half_a_percent_and_clean(seed):
    spec = price_at_tolerance(ctx(seed))
    assert spec.scenario == "price_at_tolerance"
    [(n, line)] = changed_prices(spec)
    po_price = spec.po_lines[line.po_line - 1].unit_price
    assert abs(line.unit_price - po_price) * 1000 == po_price * 5
    assert spec.invoices[0].intent == ()


# TEST: last line is an unlinked freight line, all others stay linked -> UNMATCHED_LINE on the last line.
@pytest.mark.parametrize("seed", SEEDS)
def test_unmatched_freight(seed):
    spec = unmatched_freight(ctx(seed))
    inv = spec.invoices[0]
    assert spec.scenario == "unmatched_freight"
    assert inv.lines[-1].po_line is None  # [-1] = last element (Kotlin: last())
    assert all(l.po_line == n for n, l in enumerate(inv.lines[:-1], 1))  # [:-1] = all but last (dropLast(1))
    assert inv.intent == (Intent("UNMATCHED_LINE", len(inv.lines)),)


# TEST: two invoices, same number, later date, halves add up to the PO -> only DUPLICATE_INVOICE on both.
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


# TEST: 60 % + 40 % (clean) then 1-5 extra units -> QUANTITY_MISMATCH only on the third invoice.
@pytest.mark.parametrize("seed", SEEDS)
def test_partial_series_overbilling(seed):
    spec = partial_series_overbilling(ctx(seed))
    first, second, third = spec.invoices
    assert spec.scenario == "partial_series_overbilling"
    assert len(spec.po_lines) == 1 and spec.po_lines[0].quantity in (50, 100, 200)
    assert len({i.invoice_number for i in spec.invoices}) == 3  # three different numbers
    assert first.invoice_date < second.invoice_date < third.invoice_date
    q = spec.po_lines[0].quantity
    assert first.lines[0].quantity + second.lines[0].quantity == q == spec.receipts[0].quantities[0]
    assert 1 <= third.lines[0].quantity <= 5
    assert (first.intent, second.intent, third.intent) == ((), (), (Intent("QUANTITY_MISMATCH", 1),))
