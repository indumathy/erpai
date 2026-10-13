"""Generate the golden dataset.

    bash eval/scripts/golden-erp.sh          # fresh ERP on port 8081 (separate terminal)
    cd eval && uv run python -m golden.generate

Flow: catalog (183 stories) -> create everything in the ERP -> ask /match for every invoice
-> render PDF + build label -> dev/test split -> write dataset/v1 via a temp folder (all or nothing).
"""

import argparse
import shutil
import subprocess
import tempfile
from dataclasses import dataclass
from datetime import UTC, date, datetime
from decimal import Decimal
from pathlib import Path

from golden.card import write_card
from golden.erp_client import ErpClient
from golden.labels import ErpRefs, ExpectedException, Intent, InvoiceFields, Label, LineFields, Manifest, Totals
from golden.model import PrintedInvoice, PrintedLine, ScenarioSpec
from golden.money import amount, plain
from golden.scenarios import build_catalog
from golden.split import SplitItem, assign_splits
from golden.suppliers import BUYER, SUPPLIERS
from golden.templates import render

DATASET_VERSION = "v1"
GENERATOR_VERSION = "1"
DEV_SIZE = 50
# eval/dataset/v1 — parents[2] walks up from src/golden/generate.py to eval/ (Kotlin: path.parent.parent...)
DEFAULT_OUT = Path(__file__).resolve().parents[2] / "dataset" / DATASET_VERSION


# RECORD: one invoice created in the ERP, in dataset order (what we need later to label it).
@dataclass(frozen=True)
class Created:
    """One invoice created in the ERP, in dataset order."""

    dataset_id: str  # inv-0001
    group: str  # g-001 (one story)
    spec: ScenarioSpec
    invoice_index: int  # which invoice of the story (0, 1, 2)
    invoice_id: int  # ERP id
    po: dict | None  # the ERP's PO response, None for invoices without PO
    line_po_item_ids: list[int | None]


# HELPER: drop the ERP's storage scale (50.000 -> 50) so labels show what a person would write.
def _canonical(value: Decimal) -> Decimal:
    """Drop the ERP's storage scale (50.000 -> 50) so labels show what a person would write."""
    return Decimal(plain(value))


# HELPER: the current git commit (e.g. "b81257d" or "b81257d-dirty"), recorded in the manifest.
def _erp_commit() -> str:
    try:
        # subprocess.run = run a shell command (Kotlin: ProcessBuilder)
        return subprocess.run(["git", "describe", "--always", "--dirty"], capture_output=True, text=True,
                              check=True).stdout.strip()
    except (OSError, subprocess.CalledProcessError):  # catching two exception types at once
        return "unknown"


# STEP A: create the 5 suppliers and their 40 products; returns their ERP ids and the warehouse id.
def _master_data(erp) -> tuple[dict[str, int], dict[str, int], int]:
    supplier_ids, product_ids = {}, {}
    for key, supplier in SUPPLIERS.items():
        supplier_ids[key] = erp.create_supplier(supplier.number, supplier.party.name, supplier.currency,
                                                supplier.party.vat_id)
        for product in supplier.products:
            product_ids[product.sku] = erp.create_product(product.sku, product.name)
    return supplier_ids, product_ids, erp.warehouse_id()


# STEP B: for every story — PO, approve, goods receipt(s), invoice(s) — in the ERP.
def _create_all(erp, specs: list[ScenarioSpec]) -> list[Created]:
    supplier_ids, product_ids, warehouse_id = _master_data(erp)
    created: list[Created] = []
    for g, spec in enumerate(specs, 1):
        supplier_id = supplier_ids[spec.template]
        po, item_ids = None, {}
        if spec.po_lines:  # empty for missing_po stories
            po = erp.create_purchase_order(supplier_id, spec.order_date, [
                {"productId": product_ids[l.sku], "quantity": l.quantity, "unitPrice": l.unit_price, "taxRate": l.tax_rate}
                for l in spec.po_lines
            ])
            erp.approve(po["id"])  # goods can only be received on an approved PO
            item_ids = {item["lineNumber"]: item["id"] for item in po["items"]}  # PO line number -> ERP item id
            for receipt in spec.receipts:
                items = [{"purchaseOrderItemId": item_ids[n], "quantity": q}
                         for n, q in enumerate(receipt.quantities, 1) if q > 0]
                erp.receive(po["id"], warehouse_id, receipt.receipt_date, items)
        for k, inv in enumerate(spec.invoices):
            # link each invoice line to its PO item (None for freight lines and invoices without PO)
            po_item_ids = [item_ids[l.po_line] if inv.with_po and l.po_line else None for l in inv.lines]
            body = {
                "supplierId": supplier_id, "invoiceNumber": inv.invoice_number, "invoiceDate": inv.invoice_date,
                "currency": inv.currency, "purchaseOrderId": po["id"] if inv.with_po else None,
                "items": [
                    {"purchaseOrderItemId": item_id, "description": l.description, "quantity": l.quantity,
                     "unitPrice": l.unit_price, "taxRate": l.tax_rate}
                    for l, item_id in zip(inv.lines, po_item_ids)
                ],
            }
            invoice = erp.create_invoice(body)
            created.append(Created(f"inv-{len(created) + 1:04d}", f"g-{g:03d}", spec, k, invoice["id"],
                                   po if inv.with_po else None, po_item_ids))
    return created


# STEP C: read one invoice + its /match result back from the ERP and turn them into a label and a PDF model.
def _label(erp, c: Created) -> tuple[Label, PrintedInvoice]:
    """Built only after ALL invoices exist: a duplicate's first copy is flagged by the second one."""
    invoice = erp.get_invoice(c.invoice_id)
    match = erp.match(c.invoice_id)
    spec_invoice = c.spec.invoices[c.invoice_index]
    supplier = SUPPLIERS[c.spec.template]
    totals = invoice["totals"]
    # what the PDF shows: the ERP's stored values (not our spec), so PDF and label can never disagree
    printed = PrintedInvoice(
        template=c.spec.template, supplier=supplier.party, buyer=BUYER,
        invoice_number=invoice["invoiceNumber"], invoice_date=date.fromisoformat(invoice["invoiceDate"]),
        currency=invoice["currency"], po_number=c.po["poNumber"] if c.po else None,
        lines=tuple(PrintedLine(i["description"], _canonical(i["quantity"]), _canonical(i["unitPrice"]),
                                _canonical(i["taxRate"]), amount(i["netAmount"])) for i in invoice["items"]),
        net=amount(totals["subtotal"]), tax=amount(totals["taxAmount"]), gross=amount(totals["total"]),
        tax_breakdown=tuple((_canonical(b["taxRate"]), amount(b["netAmount"]), amount(b["taxAmount"]))
                            for b in totals["taxBreakdown"]),
    )
    # the label: pydantic validates it here (line nets, totals, currency format) — a bad label fails loudly
    label = Label(
        id=c.dataset_id, template=c.spec.template, scenario=c.spec.scenario, group=c.group,
        pdf=f"invoices/{c.dataset_id}.pdf",
        fields=InvoiceFields(
            supplier_name=supplier.party.name, invoice_number=printed.invoice_number,
            invoice_date=printed.invoice_date, currency=printed.currency, po_number=printed.po_number,
            lines=[LineFields(description=l.description, quantity=l.quantity, unit_price=l.unit_price,
                              tax_rate=l.tax_rate, net_amount=l.net_amount) for l in printed.lines],
            totals=Totals(net=printed.net, tax=printed.tax, gross=printed.gross),
        ),
        intent=[Intent(code=i.code, line=i.line) for i in spec_invoice.intent],  # what we planned
        expected_exceptions=[ExpectedException.model_validate(e) for e in match["exceptions"]],  # what the ERP said
        erp=ErpRefs(invoice_id=c.invoice_id, purchase_order_id=c.po["id"] if c.po else None,
                    line_po_item_ids=c.line_po_item_ids),
    )
    return label, printed


# MAIN FLOW: everything above, then write the dataset — all or nothing.
def generate(erp, seed: int, out: Path) -> Manifest:
    # safety 1: a used ERP would give other ids and accidental duplicates
    if erp.list_invoices():
        raise SystemExit("ERP already has invoices - start a fresh one with eval/scripts/golden-erp.sh")

    # safety 2: write into a temp folder next to `out`; only at the very end replace `out` with it.
    # Same parent folder -> same drive -> the final rename is atomic. ".golden-*" is git-ignored.
    out.parent.mkdir(parents=True, exist_ok=True)
    tmp = Path(tempfile.mkdtemp(prefix=".golden-", dir=out.parent))
    try:  # try/finally = Kotlin try { } finally { }
        created = _create_all(erp, build_catalog(seed))
        built = [_label(erp, c) for c in created]
        splits = assign_splits([SplitItem(l.id, l.group, l.scenario) for l, _ in built], DEV_SIZE, seed)

        (tmp / "invoices").mkdir()
        (tmp / "labels").mkdir()
        labels = []
        for label, printed in built:
            label = label.model_copy(update={"split": splits[label.id]})  # pydantic's .copy(split = ...)
            labels.append(label)
            (tmp / label.pdf).write_bytes(render(printed))
            (tmp / "labels" / f"{label.id}.json").write_text(label.model_dump_json(indent=2) + "\n", encoding="utf-8")

        manifest = Manifest(
            dataset_version=DATASET_VERSION, seed=seed, generated_at=datetime.now(UTC).replace(microsecond=0),
            erp_commit=_erp_commit(), generator_version=GENERATOR_VERSION,
            counts={"invoices": len(labels), "groups": len({c.group for c in created}),
                    "dev": sum(1 for l in labels if l.split == "dev"),
                    "test": sum(1 for l in labels if l.split == "test")},
            splits={s: [l.id for l in labels if l.split == s] for s in ("dev", "test")},
        )
        (tmp / "manifest.json").write_text(manifest.model_dump_json(indent=2) + "\n", encoding="utf-8")
        write_card(tmp, labels, manifest)

        # success: swap the new dataset in
        if out.exists():
            shutil.rmtree(out)  # rm -rf
        tmp.rename(out)
        return manifest
    finally:
        # on failure the temp folder is still there -> remove it; the old `out` was never touched
        if tmp.exists():
            shutil.rmtree(tmp)


# CLI: `uv run python -m golden.generate [--base-url URL] [--seed N] [--out DIR]`
def main() -> None:
    # argparse = command-line options (Kotlin/Java: picocli / kotlinx-cli)
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--base-url", default="http://localhost:8081")
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--out", type=Path, default=DEFAULT_OUT)
    args = parser.parse_args()
    manifest = generate(ErpClient(args.base_url), args.seed, args.out)
    print(f"Wrote {manifest.counts['invoices']} invoices to {args.out} "
          f"(dev {manifest.counts['dev']}, test {manifest.counts['test']}).")


# Runs main() only when started as a program, not when imported by tests (Kotlin: fun main()).
if __name__ == "__main__":
    main()
