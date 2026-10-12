"""Decimal helpers that round exactly like the ERP (backend shared/money: MoneyRounding, DocumentTotals).

Why this file exists: the golden dataset stores the *correct* invoice amounts.
If Python rounded even one cent differently from the Kotlin backend, the labels
would disagree with the ERP and every eval score in M3 would be wrong.
"""

# Decimal = exact base-10 numbers (Kotlin: BigDecimal). Never use float for money:
# float(0.1) + float(0.2) == 0.30000000000000004.
# ROUND_HALF_UP = Kotlin RoundingMode.HALF_UP (1.005 -> 1.01).
from decimal import ROUND_HALF_UP, Decimal

# Module-level constant (UPPERCASE by convention, like a Kotlin top-level `val`).
# It is used as a "template" for 2 decimal places when rounding.
CENT = Decimal("0.01")


def amount(value: Decimal) -> Decimal:
    """Round to cents, half-up — MoneyRounding.amount."""
    # quantize(CENT) = "give me the same number of decimals as CENT" (2).
    # Kotlin: value.setScale(2, RoundingMode.HALF_UP)
    return value.quantize(CENT, rounding=ROUND_HALF_UP)


def line_net(quantity: Decimal, unit_price: Decimal) -> Decimal:
    """Quantity x unit price, rounded to cents — DocumentTotals.lineNet."""
    # Decimal supports * directly (Kotlin: quantity.multiply(unitPrice)).
    # Round once, at the end — exactly like the ERP does.
    return amount(quantity * unit_price)


def plain(value: Decimal) -> str:
    """42.0000 -> '42', 0.50 -> '0.5' (the Kotlin `.plain()` helper); never scientific notation."""
    # normalize() strips trailing zeros (Kotlin: stripTrailingZeros()),
    # but may produce "5E+1" for 50. format(..., "f") writes it as a plain
    # number again (Kotlin: toPlainString()).
    return format(value.normalize(), "f")
