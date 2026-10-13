"""Template: US industrial supplier. Mar 4, 2026, $1,234.50, supplier address on the right."""

from golden.templates.base import Style
from golden.templates.formats import en_number

# Month names written out ourselves: strftime("%b") would depend on the computer's language setting.
MONTHS = ("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

# STYLE: American invoice — English words, symbol before the amount, swapped address blocks.
STYLE = Style(
    key="acme_us",
    title="INVOICE",
    labels={
        "invoice_no": "Invoice No.", "date": "Invoice Date", "po": "Your order ref.", "vat_id": "EIN",
        "pos": "#", "description": "Item", "qty": "Qty", "unit_price": "Unit Price", "tax": "Tax",
        "net": "Amount", "subtotal": "Subtotal", "tax_total": "Tax", "on": "on", "total": "Total Due",
    },
    number=en_number,
    fmt_date=lambda d: f"{MONTHS[d.month - 1]} {d.day}, {d.year}",  # locale-independent
    total_pattern="{symbol}{amount}",  # $1,234.50
    supplier_on_right=True,
)
