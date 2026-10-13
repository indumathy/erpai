"""Template: German packaging supplier. 4. März 2026, EUR 1.234,50, NO VAT column per line."""

from golden.templates.base import Style
from golden.templates.formats import de_number

MONATE = ("Januar", "Februar", "März", "April", "Mai", "Juni", "Juli", "August", "September", "Oktober",
          "November", "Dezember")

# STYLE: German invoice with a harder layout — the tax rate of a line is not printed next to it,
# only the VAT summary ("Umsatzsteuer 7 % auf ...") shows the rates.
STYLE = Style(
    key="alpenpack_de",
    title="RECHNUNG",
    labels={
        "invoice_no": "Rechnungs-Nr.", "date": "Rechnungsdatum", "po": "Ihre Bestellung", "vat_id": "USt-ID",
        "pos": "Nr.", "description": "Artikel", "qty": "Anzahl", "unit_price": "Preis/Stk.", "tax": "",
        "net": "Gesamt", "subtotal": "Summe netto", "tax_total": "Umsatzsteuer", "on": "auf",
        "total": "Zu zahlen",
    },
    number=de_number,
    fmt_date=lambda d: f"{d.day}. {MONATE[d.month - 1]} {d.year}",  # 4. März 2026
    total_pattern="{currency} {amount}",  # EUR 1.234,50
    tax_per_line=False,  # rates appear only in the VAT summary block
    supplier_on_right=True,
)
