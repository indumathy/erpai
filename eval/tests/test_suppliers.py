# Tests for the fixed master data (buyer, 5 suppliers, their products) in golden/suppliers.py.
# These are "data sanity" tests: they guard the rules the PDF generator and the ERP rely on.
from decimal import Decimal

from golden.suppliers import BUYER, SUPPLIERS, TEMPLATE_KEYS


def test_one_supplier_per_template():
    # Each of the 5 PDF layouts belongs to exactly one supplier.
    assert set(SUPPLIERS) == set(TEMPLATE_KEYS)
    assert len(TEMPLATE_KEYS) == 5
    # The dict key and the supplier's own key must agree (no copy-paste mistakes).
    assert all(s.key == key for key, s in SUPPLIERS.items())


def test_prices_have_even_cents():
    # Even cents make ±0.5 % exactly representable with 4 decimals (price_at_tolerance near misses).
    # e.g. 4.18 * 1.005 = 4.2009 (4 decimals), but 4.17 * 1.005 = 4.19085 (5 decimals -> ERP would round).
    for supplier in SUPPLIERS.values():
        for p in supplier.products:
            cents = p.unit_price * 100
            # Whole number of cents, and that number is even. ", p.sku" = message shown on failure.
            assert cents == cents.to_integral_value() and cents % 2 == 0, p.sku


def test_names_fit_on_one_table_line():
    # Long names would wrap in the PDF table and make text extraction (M3) ambiguous.
    for supplier in SUPPLIERS.values():
        for p in supplier.products:
            assert len(p.name) <= 38, p.name


def test_skus_are_unique_and_valid_for_the_erp():
    # List comprehension with two loops = flatMap over suppliers -> products.
    skus = [p.sku for s in SUPPLIERS.values() for p in s.products]
    # A set drops duplicates, so equal length means all SKUs are unique.
    assert len(skus) == len(set(skus))
    # Only letters, digits and dashes (e.g. "NB-PAPER-A4").
    assert all(sku.replace("-", "").isalnum() for sku in skus)


def test_bilingual_supplier_can_fill_long_invoices():
    # The "many lines" scenarios need a supplier with a big catalogue.
    assert len(SUPPLIERS["bilingual"].products) >= 16


def test_currencies_and_buyer():
    # Set comprehension {...}: the distinct currencies across all suppliers.
    assert {s.currency for s in SUPPLIERS.values()} == {"EUR", "USD", "GBP"}
    # A non-empty string is "truthy" in Python, so this checks the buyer has a name.
    assert BUYER.name
    # Tax rates must be Decimal, never float (same reason as money.py).
    assert all(isinstance(p.tax_rate, Decimal) for s in SUPPLIERS.values() for p in s.products)
