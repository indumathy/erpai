"""The five fictional suppliers of the golden dataset — one per PDF template — and the buyer.

Fixed master data (like a seed script): the generator picks products from here,
creates them in the ERP and prints them on the invoice PDFs.
"""

from decimal import Decimal

from golden.model import Party, Product, Supplier

# Our own company: the buyer printed on every invoice.
BUYER = Party("erpai Demo GmbH", "Musterstraße 1", "20095 Hamburg, Germany", "DE123456789")


# Leading underscore = "internal helper, don't use outside this file" (Python has no `private`).
# Takes prices as strings so Decimal is exact (Decimal("4.18"), never Decimal(4.18)).
def _p(sku: str, name: str, price: str, tax: str) -> Product:
    return Product(sku, name, Decimal(price), Decimal(tax))


# Rules (checked by tests/test_suppliers.py):
#   - prices in even cents   -> ±0.5 % price scenarios stay exact with 4 decimals
#   - names <= 38 characters -> one table line on the PDF
#   - SKUs unique, letters/digits/dashes only
# Each supplier covers something different for the extractor in M3:
#   nordbuero_de  German, EUR, two VAT rates (19 % / 7 %)
#   acme_us       English, USD, no VAT (0 %)
#   techsource_uk English, GBP, 20 % VAT
#   alpenpack_de  German, EUR, mixed VAT rates
#   bilingual     German/English, EUR, big catalogue for long invoices
#
# Dict comprehension {key: value for ...} builds a map keyed by supplier key
# (Kotlin: listOf(...).associateBy { it.key }).
SUPPLIERS: dict[str, Supplier] = {
    s.key: s
    for s in (
        Supplier(
            "nordbuero_de", "GS-NB",
            Party("Nordbüro GmbH", "Speicherstraße 12", "28195 Bremen", "DE281947365"),
            "EUR", "NB-2026-{:04d}",  # {:04d} = number padded to 4 digits -> NB-2026-0007
            (
                _p("NB-PAPIER-A4", "Kopierpapier A4, 500 Blatt", "4.18", "19"),
                _p("NB-TONER-26X", "Toner HP 26X schwarz", "89.50", "19"),
                _p("NB-ORDNER-80", "Ordner A4, 80 mm, blau", "2.36", "19"),
                _p("NB-KULI-BL", "Kugelschreiber blau, 10 Stück", "3.90", "19"),
                _p("NB-HEFT-A5", "Notizheft A5 kariert", "1.24", "19"),
                _p("NB-FACHBUCH", "Fachbuch Buchhaltung 2026", "39.90", "7"),
            ),
        ),
        Supplier(
            "acme_us", "GS-ACME",
            Party("Acme Industrial Supply Inc.", "1200 Harbor Blvd", "Oakland, CA 94607, USA", "US94-1234567"),
            "USD", "INV-{:06d}",
            (
                _p("AC-BOLT-M8", "Hex bolt M8x40, box of 100", "12.48", "0"),
                _p("AC-NUT-M8", "Hex nut M8, box of 100", "6.24", "0"),
                _p("AC-GLOVE-L", "Work gloves, size L", "3.86", "0"),
                _p("AC-TAPE-48", "Packing tape 48 mm x 66 m", "2.14", "0"),
                _p("AC-DRILL-18V", "Cordless drill 18V", "129.00", "0"),
                _p("AC-SAFETY-GL", "Safety glasses, clear", "5.62", "0"),
            ),
        ),
        Supplier(
            "techsource_uk", "GS-TS",
            Party("TechSource Ltd", "45 Kingsway", "London WC2B 6TE, United Kingdom", "GB123456789"),
            "GBP", "TS/26/{:05d}",
            (
                _p("TS-MON-27", "27in monitor, QHD", "218.00", "20"),
                _p("TS-KB-UK", "Keyboard, UK layout", "24.90", "20"),
                _p("TS-MOUSE", "Wireless mouse", "14.36", "20"),
                _p("TS-DOCK-USBC", "USB-C docking station", "112.50", "20"),
                _p("TS-CABLE-HDMI", "HDMI cable 2 m", "6.98", "20"),
                _p("TS-HEADSET", "Headset with microphone", "39.98", "20"),
            ),
        ),
        Supplier(
            "alpenpack_de", "GS-AP",
            Party("Alpenpack Verpackungen GmbH", "Industriestraße 7", "83022 Rosenheim", "DE314159265"),
            "EUR", "AP-R-{:05d}",
            (
                _p("AP-KARTON-M", "Faltkarton 400x300x200 mm", "0.86", "19"),
                _p("AP-KARTON-L", "Faltkarton 600x400x400 mm", "1.42", "19"),
                _p("AP-PALETTE", "Europalette 1200x800 mm", "14.20", "19"),
                _p("AP-FOLIE-23", "Stretchfolie 23 my, 300 m", "9.64", "19"),
                _p("AP-BOX-LM", "Lebensmittelbox 1 l, 50 Stück", "7.48", "7"),
                _p("AP-BEUTEL-LM", "Brotbeutel Papier, 1000 Stück", "18.26", "7"),
            ),
        ),
        Supplier(
            "bilingual", "GS-HL",
            Party("Hanse Logistik & Bedarf GmbH", "Am Sandtorkai 40", "20457 Hamburg", "DE271828182"),
            "EUR", "RE-2026-{:04d}",
            (
                _p("HL-01", "Lagerregal 2000x1000x500 mm", "148.00", "19"),
                _p("HL-02", "Kunststoffbox 60 l", "12.84", "19"),
                _p("HL-03", "Gabelhubwagen 2500 kg", "389.00", "19"),
                _p("HL-04", "Klebeband braun 50 mm", "1.98", "19"),
                _p("HL-05", "Etiketten 100x150 mm, 500 Stk.", "8.46", "19"),
                _p("HL-06", "Handscanner Barcode", "96.40", "19"),
                _p("HL-07", "Sicherheitsschuhe S3, Gr. 42", "54.90", "19"),
                _p("HL-08", "Warnweste gelb", "3.28", "19"),
                _p("HL-09", "Erste-Hilfe-Kasten DIN 13157", "32.60", "19"),
                _p("HL-10", "Kaffee Bohnen 1 kg", "16.98", "7"),
                _p("HL-11", "Mineralwasser 12 x 1 l", "5.94", "7"),
                _p("HL-12", "Zucker 1 kg", "1.58", "7"),
                _p("HL-13", "Kantinen-Snackbox", "4.72", "7"),
                _p("HL-14", "Reinigungstücher, 100 Stk.", "6.36", "19"),
                _p("HL-15", "Müllsäcke 120 l, 25 Stk.", "7.12", "19"),
                _p("HL-16", "Paletten-Etikettendrucker", "412.00", "19"),
            ),
        ),
    )
}

# tuple(dict) = the dict's keys, in insertion order (Python dicts keep order, like LinkedHashMap).
TEMPLATE_KEYS: tuple[str, ...] = tuple(SUPPLIERS)
