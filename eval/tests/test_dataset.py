"""Checks over the finished dataset in dataset/v1. They test the DATA, not the generator code.

No ERP needed: they read the committed labels and PDFs. Run them after every regeneration.
"""

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
# skip the whole file (not fail) on a fresh checkout without a dataset (JUnit: @EnabledIf / assumeTrue)
pytestmark = pytest.mark.skipif(not (DATASET / "manifest.json").exists(), reason="dataset not generated yet")


# FIXTURE: all 200 labels, loaded once per file (scope="module"). A test gets it by naming a
# parameter `labels` (Kotlin/JUnit: a @BeforeAll-loaded field, injected by name).
@pytest.fixture(scope="module")
def labels() -> list[Label]:
    return [Label.model_validate_json(p.read_text(encoding="utf-8")) for p in sorted((DATASET / "labels").glob("*.json"))]


# FIXTURE: the manifest (counts + official dev/test id lists).
@pytest.fixture(scope="module")
def manifest() -> Manifest:
    return Manifest.model_validate_json((DATASET / "manifest.json").read_text(encoding="utf-8"))


# ---- E4: your exercise ----------------------------------------------------------------------


# CHECK 1 (E4, yours): what we planned == what the ERP reported, for every invoice.
def test_intent_matches_erp(labels):
    """Check 1: for every invoice, intent and expected_exceptions name the same (code, line) pairs,
    in the same order. Report ALL mismatching invoices in the assertion message, not just the first."""
    pytest.fail("M2 exercise E4: implement check 1")


# CHECK 5 (E4, yours): no leakage between dev and test.
def test_no_leakage_between_dev_and_test(labels, manifest):
    """Check 5: dev and test (from the manifest) are disjoint and together cover all label ids; each
    label's own `split` agrees with the manifest; all invoices of a group share one split; dev has 45-55."""
    pytest.fail("M2 exercise E4: implement check 5")


# ---- prepared checks ------------------------------------------------------------------------


# CHECK 2: every label adds up (line net = qty x price, net = sum of lines, gross = net + tax).
def test_labels_are_internally_consistent(labels):
    """Check 2 (the Pydantic validators already enforce it on load; this states it explicitly)."""
    assert len(labels) == 200
    for label in labels:
        f = label.fields
        assert all(l.net_amount == line_net(l.quantity, l.unit_price) for l in f.lines), label.id
        assert f.totals.net == sum((l.net_amount for l in f.lines), Decimal(0)), label.id
        assert f.totals.gross == f.totals.net + f.totals.tax, label.id


# CHECK 3: every value in a label really appears in its PDF's text (formatted the template's way).
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
    assert not problems, "\n".join(map(str, problems))  # list ALL problems, not just the first


# CHECK 4: enough examples per exception code, and every template has clean and flagged invoices.
def test_coverage(labels):
    """Check 4: enough examples per code, and every template has clean and flagged invoices."""
    per_code = Counter(code for l in labels for code in {e.code for e in l.expected_exceptions})
    assert all(per_code[code] >= 10 for code in ExceptionCode), per_code
    per_template = defaultdict(Counter)
    for l in labels:
        per_template[l.template]["flagged" if l.expected_exceptions else "clean"] += 1
    assert len(per_template) == 5
    assert all(c["clean"] > 0 and c["flagged"] > 0 for c in per_template.values()), per_template

