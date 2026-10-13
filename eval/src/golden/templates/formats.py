"""Number formatting without rounding: every significant decimal is kept, padded to min_decimals.

German: 1.234,5   (dot groups thousands, comma separates decimals)
English: 1,234.5  (the other way round)
"""

from decimal import Decimal


# HELPER: the shared logic — split into whole/fraction, group the whole part in threes, glue together.
def _number(value: Decimal, decimal_sep: str, group_sep: str, min_decimals: int) -> str:
    sign = "-" if value < 0 else ""
    # normalize() drops trailing zeros (50.000 -> 50); "f" avoids scientific notation (5E+1).
    # partition(".") splits at the first dot into (before, ".", after) — Kotlin: substringBefore/After.
    whole, _, frac = format(abs(value).normalize(), "f").partition(".")
    frac = frac.ljust(min_decimals, "0")  # pad on the right: "5" -> "50" (Kotlin: padEnd(2, '0'))
    # group the whole part in threes from the right: "1234567" -> ["1", "234", "567"]
    groups = []
    while len(whole) > 3:
        groups.insert(0, whole[-3:])  # last three characters (Kotlin: takeLast(3))
        whole = whole[:-3]            # everything but the last three (Kotlin: dropLast(3))
    groups.insert(0, whole)
    return sign + group_sep.join(groups) + (decimal_sep + frac if frac else "")


# FORMAT: German style, 1234.5 -> "1.234,5" (with min_decimals=2: "1.234,50").
def de_number(value: Decimal, min_decimals: int = 0) -> str:
    """1234.5 -> '1.234,5' (German: '.' groups thousands, ',' separates decimals)."""
    return _number(value, ",", ".", min_decimals)


# FORMAT: English style, 1234.5 -> "1,234.5" (with min_decimals=2: "1,234.50").
def en_number(value: Decimal, min_decimals: int = 0) -> str:
    """1234.5 -> '1,234.5'."""
    return _number(value, ".", ",", min_decimals)
