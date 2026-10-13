"""Template: UK IT supplier. 04/03/2026 (day first!), 1,234.50 GBP, "VAT" wording."""

from golden.templates.base import Style
from golden.templates.formats import en_number

# STYLE: British invoice — English numbers but European day-first dates (the US/UK date trap).
STYLE = Style(
    key="techsource_uk",
    title="Tax Invoice",
    labels={
        "invoice_no": "Invoice number", "date": "Date", "po": "Customer PO", "vat_id": "VAT Reg. No.",
        "pos": "Line", "description": "Description", "qty": "Quantity", "unit_price": "Unit price",
        "tax": "VAT", "net": "Net", "subtotal": "Total net", "tax_total": "VAT", "on": "on", "total": "Total",
    },
    number=en_number,
    fmt_date=lambda d: d.strftime("%d/%m/%Y"),  # day first: 04/03/2026 is 4 March
    total_pattern="{amount} {currency}",  # 1,234.50 GBP
)
