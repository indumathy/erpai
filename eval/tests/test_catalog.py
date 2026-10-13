# Tests for the scenario catalog (RECIPE + build_catalog in golden/scenarios.py):
# does seed 42 produce the planned 200-invoice mix, reproducibly?
from collections import Counter, defaultdict

from golden.scenarios import FIRST_ORDER_DATE, LAST_INVOICE_DATE, build_catalog

# Built once for all tests in this file (module-level, like a companion-object val).
SPECS = build_catalog(42)
INVOICES = [(spec, inv) for spec in SPECS for inv in spec.invoices]  # flatten: (story, invoice) pairs


# TEST: 183 stories, 200 invoices, 80 clean, and each exception code as often as planned.
def test_200_invoices_with_the_planned_mix():
    assert len(SPECS) == 183
    assert len(INVOICES) == 200
    assert sum(1 for _, inv in INVOICES if not inv.intent) == 80  # empty tuple counts as False
    # Counter = counts occurrences (Kotlin: groupingBy { it }.eachCount()); a code counts once per invoice
    per_code = Counter(code for _, inv in INVOICES for code in {i.code for i in inv.intent})
    assert per_code == {
        "PRICE_MISMATCH": 30, "QUANTITY_MISMATCH": 24, "TAX_MISMATCH": 20, "UNMATCHED_LINE": 20,
        "DUPLICATE_INVOICE": 18, "CURRENCY_MISMATCH": 16, "MISSING_PO": 16,
    }


# TEST: same seed -> identical catalog; different seed -> different catalog.
def test_catalog_is_deterministic():
    assert build_catalog(42) == SPECS
    assert build_catalog(7) != SPECS


# TEST: all dates inside the fixed window, and order <= receipt <= invoice.
def test_dates_are_fixed_in_the_past_and_ordered():
    for spec, inv in INVOICES:
        assert FIRST_ORDER_DATE <= spec.order_date
        assert inv.invoice_date <= LAST_INVOICE_DATE
        for receipt in spec.receipts:
            assert spec.order_date <= receipt.receipt_date <= inv.invoice_date


# TEST: an invoice number is reused only where we WANT a duplicate.
def test_invoice_numbers_repeat_only_in_duplicate_scenarios():
    seen = defaultdict(list)  # dict that creates an empty list for a new key (Kotlin: getOrPut(key) { mutableListOf() })
    for spec, inv in INVOICES:
        seen[(spec.template, inv.invoice_number)].append(spec.scenario)
    repeated = {scenarios[0] for scenarios in seen.values() if len(scenarios) > 1}
    assert repeated == {"duplicate_pair", "missing_po_duplicate"}


# TEST: every exception code shows up in at least 3 different PDF layouts.
def test_every_code_appears_in_at_least_three_templates():
    templates = defaultdict(set)
    for spec, inv in INVOICES:
        for intent in inv.intent:
            templates[intent.code].add(spec.template)
    assert all(len(t) >= 3 for t in templates.values())


# TEST: at least 8 long invoices (more than 12 lines -> table runs onto a second PDF page).
def test_some_invoices_span_two_pages():
    assert sum(1 for _, inv in INVOICES if len(inv.lines) > 12) >= 8
