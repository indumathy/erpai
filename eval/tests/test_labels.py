# Tests for the label format (golden/labels.py). These define "done" for exercise E1.
import json
from datetime import date
from decimal import Decimal

import pytest
from pydantic import ValidationError

from golden.labels import InvoiceFields, Label, LineFields, Totals


# Test-data builders: a valid line / invoice as plain dicts (= the JSON shape).
# **overrides collects keyword arguments into a dict (Kotlin: vararg of pairs);
# `data | overrides` merges two dicts, right side wins (Kotlin: data + overrides).
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
    # JSON strings become real Decimal and date objects.
    f = InvoiceFields.model_validate(fields())
    assert f.lines[0].unit_price == Decimal("4.15")
    assert isinstance(f.lines[0].unit_price, Decimal)
    assert f.invoice_date == date(2026, 3, 14)
    assert f.totals.gross == Decimal("49.39")


def test_line_net_must_equal_quantity_times_price():
    # pytest.raises = JUnit assertThrows: the block must raise ValidationError.
    with pytest.raises(ValidationError):
        LineFields.model_validate(line(net_amount="41.49"))


def test_line_net_uses_half_up_rounding_like_the_erp():
    # 3 x 0.335 = 1.005 -> 1.01 (half-up), so 1.00 must be rejected.
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
    # Invoices without a purchase order exist (MISSING_PO scenario).
    assert InvoiceFields.model_validate(fields(po_number=None)).po_number is None


def test_invoice_needs_at_least_one_line():
    with pytest.raises(ValidationError):
        InvoiceFields.model_validate(fields(lines=[], totals={"net": "0.00", "tax": "0.00", "gross": "0.00"}))


def test_unknown_keys_are_rejected():
    # A typo in a label must fail, not be silently ignored.
    with pytest.raises(ValidationError):
        LineFields.model_validate(line(colour="red"))


def test_decimals_are_written_as_strings():
    # Strings keep money exact in JSON ("4.15", never the float 4.15).
    dumped = json.loads(InvoiceFields.model_validate(fields()).model_dump_json())
    assert dumped["lines"][0]["unit_price"] == "4.15"
    assert dumped["totals"]["net"] == "41.50"


def test_full_label_round_trips_through_json():
    # object -> JSON -> object gives the same object back (nothing lost).
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
