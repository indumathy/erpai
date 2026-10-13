# Tests for the five PDF templates (golden/templates/). They render a PDF, then read its text back
# with pdfplumber — the same library the classic parser will use in M3.
import io
from datetime import date
from decimal import Decimal

import pdfplumber
import pytest

from golden.model import PrintedInvoice, PrintedLine
from golden.suppliers import BUYER, SUPPLIERS
from golden.templates import STYLES, render
from golden.templates.formats import de_number, en_number


# TEST: German vs English number format; decimals are kept, never rounded away.
def test_number_formatting():
    assert de_number(Decimal("1234.5"), 2) == "1.234,50"
    assert en_number(Decimal("1234.5"), 2) == "1,234.50"
    assert de_number(Decimal("12.4017"), 2) == "12,4017"  # 4-decimal price stays exact
    assert de_number(Decimal("50.000")) == "50"
    assert en_number(Decimal("1234567.891"), 2) == "1,234,567.891"
    assert en_number(Decimal("0.86"), 2) == "0.86"


# TEST: each template prints the same date in its own format.
def test_dates_per_template():
    d = date(2026, 3, 4)
    assert STYLES["nordbuero_de"].fmt_date(d) == "04.03.2026"
    assert STYLES["acme_us"].fmt_date(d) == "Mar 4, 2026"
    assert STYLES["techsource_uk"].fmt_date(d) == "04/03/2026"
    assert STYLES["alpenpack_de"].fmt_date(d) == "4. März 2026"
    assert STYLES["bilingual"].fmt_date(d) == "2026-03-04"


# HELPER: a sample invoice for one template, with tricky numbers (thousands, 4-decimal price).
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


# HELPER: all text of a PDF (as one string) and its page count.
def pdf_text(data: bytes) -> tuple[str, int]:
    # `with` = Kotlin `use { }`: the PDF is closed automatically
    with pdfplumber.open(io.BytesIO(data)) as pdf:
        return " ".join((page.extract_text() or "") for page in pdf.pages), len(pdf.pages)


# TEST: every important value really appears in the PDF text, formatted the template's way.
@pytest.mark.parametrize("template", sorted(STYLES))
def test_printed_values_are_extractable(template):
    inv = invoice(template)
    style = STYLES[template]
    text, _ = pdf_text(render(inv))
    text = " ".join(text.split())  # collapse line breaks and double spaces
    expected = [
        inv.invoice_number, inv.po_number, style.fmt_date(inv.invoice_date), inv.supplier.name,
        style.fmt_total(inv.gross, inv.currency), style.fmt_total(inv.net, inv.currency), style.fmt_rate(Decimal("19")),
        "Artikel 1", style.fmt_qty(Decimal("1234.5")), style.fmt_price(Decimal("12.4017")), style.fmt_money(Decimal("15309.90")),
    ]
    missing = [value for value in expected if value not in text]
    assert not missing, f"{template}: not found in PDF text: {missing}"


# TEST: a 40-line invoice breaks onto a second page.
def test_long_invoice_spans_two_pages():
    _, pages = pdf_text(render(invoice("bilingual", n_lines=40)))
    assert pages >= 2


# TEST: same input -> byte-identical PDF (no timestamp inside), so the dataset is reproducible.
def test_render_is_byte_identical():
    assert render(invoice("nordbuero_de")) == render(invoice("nordbuero_de"))


# TEST: invoices without a purchase order print no PO line.
def test_invoice_without_po_number():
    inv = invoice("acme_us")
    # {**a, "k": v} = copy of dict a with one key replaced (Kotlin: map + ("k" to v))
    text, _ = pdf_text(render(PrintedInvoice(**{**inv.__dict__, "po_number": None})))
    assert "PO-000017" not in text
