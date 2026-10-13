"""Shared PDF rendering for all supplier templates (reportlab platypus: automatic page breaks).

Layout of every invoice (the Style only changes words, formats and small layout switches):

    [ supplier address ]   [ buyer address ]      (swapped if supplier_on_right)
    TITLE
    Invoice no. / Date / PO
    Pos | Description | Qty | Unit price | (Tax) | Net      <- header repeats on every page
    ...
                                   Subtotal / VAT x % on y / Total
"""

from collections.abc import Callable
from dataclasses import dataclass
from datetime import date
from decimal import Decimal
from io import BytesIO
from xml.sax.saxutils import escape  # "&" -> "&amp;" etc.: Paragraph text is mini-HTML

# reportlab "platypus" = high-level layout: you build a list of blocks (a "story"), it flows them onto pages.
from reportlab.lib import colors
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import getSampleStyleSheet
from reportlab.lib.units import mm
from reportlab.platypus import Paragraph, SimpleDocTemplate, Spacer, Table, TableStyle

from golden.model import Party, PrintedInvoice


# STYLE: everything that differs between suppliers — words, number/date format, small layout switches.
@dataclass(frozen=True)
class Style:
    key: str
    title: str
    labels: dict[str, str]  # keys: invoice_no, date, po, vat_id, pos, description, qty, unit_price, tax, net,
    #                         subtotal, tax_total, on, total
    number: Callable[[Decimal, int], str]  # de_number or en_number (a function stored in a field)
    fmt_date: Callable[[date], str]
    total_pattern: str  # e.g. "{amount} {symbol}" or "{currency} {amount}"; {symbol} = currency symbol
    tax_per_line: bool = True  # False: no VAT column, rates only in the totals block
    supplier_on_right: bool = False

    # FORMAT: quantity — no forced decimals (50, 1.234,5).
    def fmt_qty(self, value: Decimal) -> str:
        return self.number(value, 0)

    # FORMAT: unit price — at least 2 decimals, more if the ERP has them (12,4017).
    def fmt_price(self, value: Decimal) -> str:
        return self.number(value, 2)

    # FORMAT: an amount (line net, totals) — 2 decimals.
    def fmt_money(self, value: Decimal) -> str:
        return self.number(value, 2)

    # FORMAT: tax rate, "19 %".
    def fmt_rate(self, value: Decimal) -> str:
        return f"{self.number(value, 0)} %"

    # FORMAT: amount with currency in the template's pattern: "1.234,50 €", "$1,234.50", "EUR 1.234,50".
    def fmt_total(self, value: Decimal, currency: str) -> str:
        symbol = {"EUR": "€", "USD": "$", "GBP": "£"}.get(currency, currency)  # .get(k, default) = getOrDefault
        return self.total_pattern.format(amount=self.fmt_money(value), currency=currency, symbol=symbol)


# HELPER: an address block as mini-HTML (bold name, line breaks, optional VAT id line).
def _party(party: Party, vat_label: str | None) -> str:
    text = f"<b>{escape(party.name)}</b><br/>{escape(party.street)}<br/>{escape(party.city)}"
    return text + (f"<br/>{vat_label}: {escape(party.vat_id)}" if vat_label else "")


# RENDER: draws one invoice in the given style and returns the PDF as bytes.
def render(inv: PrintedInvoice, style: Style) -> bytes:
    ss = getSampleStyleSheet()  # reportlab's default text styles (Normal, Heading1, ...)
    normal = ss["Normal"]
    labels = style.labels
    buffer = BytesIO()  # in-memory file (Kotlin: ByteArrayOutputStream)
    # invariant=1: no creation timestamp or random document id -> identical bytes for identical input
    doc = SimpleDocTemplate(
        buffer, pagesize=A4, leftMargin=18 * mm, rightMargin=18 * mm, topMargin=18 * mm, bottomMargin=18 * mm,
        title=f"{style.title} {inv.invoice_number}", author=inv.supplier.name, invariant=1,
    )

    # 1. the two addresses side by side (only the supplier shows a VAT id)
    supplier = Paragraph(_party(inv.supplier, labels["vat_id"]), normal)
    buyer = Paragraph(_party(inv.buyer, None), normal)
    top = Table([[buyer, supplier] if style.supplier_on_right else [supplier, buyer]], colWidths=[87 * mm, 87 * mm])
    top.setStyle(TableStyle([("VALIGN", (0, 0), (-1, -1), "TOP")]))

    # 2. invoice number, date and (if any) PO number — labels in bold
    meta_rows = [[labels["invoice_no"], inv.invoice_number], [labels["date"], style.fmt_date(inv.invoice_date)]]
    if inv.po_number:  # None or "" -> no PO row
        meta_rows.append([labels["po"], inv.po_number])
    meta = Table(meta_rows, hAlign="LEFT")
    meta.setStyle(TableStyle([("FONT", (0, 0), (0, -1), "Helvetica-Bold"), ("FONTSIZE", (0, 0), (-1, -1), 9)]))

    # 3. the line table; the VAT column only if the style wants it
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
    lines = Table(rows, colWidths=widths, repeatRows=1)  # repeatRows=1: header again on every new page
    # TableStyle cells are (column, row); -1 = last. (0, 0)-(-1, 0) = the whole header row.
    lines.setStyle(TableStyle([
        ("FONT", (0, 0), (-1, 0), "Helvetica-Bold"),
        ("FONTSIZE", (0, 0), (-1, -1), 9),
        ("LINEBELOW", (0, 0), (-1, 0), 0.5, colors.black),
        ("ALIGN", (2, 0), (-1, -1), "RIGHT"),  # numbers right-aligned
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
    ]))

    # 4. totals: net, one VAT row per rate ("VAT 19 % on 1.000,00"), gross in bold
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

    # 5. stack the blocks top to bottom; reportlab breaks pages where needed
    story = [top, Spacer(1, 10 * mm), Paragraph(f"<b>{style.title}</b>", ss["Heading1"]), meta,
             Spacer(1, 6 * mm), lines, Spacer(1, 6 * mm), totals]
    doc.build(story)
    return buffer.getvalue()
