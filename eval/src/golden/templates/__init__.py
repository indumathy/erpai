"""The five supplier invoice layouts. render() picks the style by the invoice's template key.

__init__.py makes this folder a package; whatever is defined here is importable as `golden.templates.X`
(Kotlin: the package's public API, e.g. `import golden.templates.render`).
"""

from golden.model import PrintedInvoice
from golden.templates import acme_us, alpenpack_de, base, bilingual, nordbuero_de, techsource_uk
from golden.templates.base import Style

# REGISTRY: template key -> Style, e.g. STYLES["acme_us"] (Kotlin: listOf(...).associateBy { it.key }).
STYLES: dict[str, Style] = {
    m.STYLE.key: m.STYLE for m in (nordbuero_de, acme_us, techsource_uk, alpenpack_de, bilingual)
}


# RENDER: the one entry point — invoice in, PDF bytes out, in its supplier's layout.
def render(invoice: PrintedInvoice) -> bytes:
    return base.render(invoice, STYLES[invoice.template])
