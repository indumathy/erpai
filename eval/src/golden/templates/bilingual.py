"""Template: Hamburg logistics supplier. German/English labels, 2026-03-04, long tables over 2 pages."""

from golden.templates.base import Style
from golden.templates.formats import de_number

# STYLE: bilingual labels ("Menge / Qty") with German numbers — mixed signals for the reader.
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
    fmt_date=lambda d: d.isoformat(),  # 2026-03-04
    total_pattern="{amount} {currency}",  # 1.234,50 EUR
)
