"""Template: German office supplier. 04.03.2026, 1.234,50 €, VAT column per line."""

from golden.templates.base import Style
from golden.templates.formats import de_number

# STYLE: classic German invoice — the "easy" baseline layout.
STYLE = Style(
    key="nordbuero_de",
    title="Rechnung",
    labels={
        "invoice_no": "Rechnungsnr.", "date": "Datum", "po": "Bestellnummer", "vat_id": "USt-IdNr.",
        "pos": "Pos.", "description": "Bezeichnung", "qty": "Menge", "unit_price": "Einzelpreis",
        "tax": "MwSt.", "net": "Betrag", "subtotal": "Nettobetrag", "tax_total": "zzgl. MwSt.",
        "on": "auf", "total": "Rechnungsbetrag",
    },
    number=de_number,
    fmt_date=lambda d: d.strftime("%d.%m.%Y"),  # strftime = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    total_pattern="{amount} {symbol}",  # 1.234,50 €
)
