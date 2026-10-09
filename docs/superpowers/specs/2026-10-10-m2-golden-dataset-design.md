# M2 Golden Dataset — Design

Date: 2026-10-10 · Milestone: M2 (Stage 1 — LLM application basics) · Status: draft for review

## Purpose

M2 produces the **golden dataset**: ~200 synthetic supplier-invoice PDFs, each with verified labels.
It is the measuring stick for every later AI milestone:

- **M3** grades field extraction (classic PDF parsing vs LLM) against `fields`.
- **M5** grades product/PO-line matching against `erp.line_po_item_ids`.
- **M7 / M10** grade explanations and the agent against `expected_exceptions`.

M2 itself calls no LLM. Its proof is the committed dataset plus a generated dataset card.

Learning goals for the user: Python (uv, Pydantic, pytest), synthetic data design, and eval thinking —
label quality, near-miss cases, stratification, dev/test leakage.

## Decisions

| # | Decision | Why |
|---|---|---|
| D1 | **Hybrid labelling (approach C):** each scenario declares an `intent`; the generator creates the data through the ERP REST API; `expected_exceptions` = the ERP's `GET /api/invoices/{id}/match` output; a test asserts intent == ERP result. | One source of truth (the M1 Kotlin matcher) and generator bugs are caught automatically. |
| D2 | **5 supplier templates** with different languages, number/date formats and layouts. | A single layout makes the M3 regex baseline trivially perfect and the comparison meaningless. |
| D3 | **Fresh, separate ERP database** `erpai_golden` for generation; the generator refuses to run if the ERP already has invoices. | Labels depend on existing data (duplicates, earlier-invoiced quantities). The demo database stays untouched. |
| D4 | **Deterministic:** one seed (`42`), all dates anchored to fixed past dates (2026-01-05 … 2026-09-30), never relative to today. | Same seed → same dataset; regeneration is reproducible. |
| D5 | **Dataset committed to git** under `eval/dataset/v1/`. | M3 evals run without Docker or the backend. A regeneration with different rules becomes `v2`. |
| D6 | **Only the REST API**, never direct DB access. | The generator is an API client like the later MCP server and agent. |
| D7 | **All amounts and quantities are strings in JSON, parsed as `Decimal`.** | Float rounding would make correct extractions look wrong. |
| D8 | **Stratified mix** (40 % clean) and a **dev/test split** (~50 / ~150) recorded in the manifest. | Each exception code gets ≥10 examples; prompts are tuned on dev and only reported on test. |

Out of scope: scans/photos (M11), LLM calls (M3), random layouts, changes to the ERP backend.

## Layout

```
eval/
  pyproject.toml          # uv project; deps: httpx, pydantic, reportlab, faker, pdfplumber; dev: pytest
  README.md               # how to regenerate and how to run the checks
  scripts/
    golden-erp.sh         # recreate DB erpai_golden in the erpai-postgres container, start backend against it
  src/golden/
    erp_client.py         # thin httpx wrapper: suppliers, products, warehouses, POs, receipts, invoices, match
    scenarios.py          # scenario catalog → ScenarioSpec list (with intent)              [user: E2]
    labels.py             # Pydantic models for label JSON, manifest                         [user: E1]
    split.py              # stratified dev/test assignment                                   [user: E3]
    templates/            # one module per supplier template: render(invoice) -> bytes
    generate.py           # orchestration; entry point `uv run python -m golden.generate`
    card.py               # writes dataset/v1/README.md (dataset card) from labels + manifest
  tests/
    test_labels.py        # unit tests for labels.py validators
    test_dataset.py       # checks over the finished dataset (see "Dataset checks")       [user: E4 = checks 1, 5]
  dataset/v1/
    manifest.json
    README.md             # dataset card (generated)
    invoices/inv-0001.pdf …
    labels/inv-0001.json …
```

## Generation run

1. `eval/scripts/golden-erp.sh` drops and recreates database `erpai_golden` in the `erpai-postgres`
   Docker container (port 5433) and starts the backend with
   `SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5433/erpai_golden` (user/password `erpai`).
   Flyway creates the schema. The backend's normal database is not touched.
2. `generate.py` checks `GET /api/invoices` is empty, otherwise aborts with a clear message.
3. Master data: 5 suppliers (one per template, with currency), ~25 products, one warehouse.
4. `scenarios.py` builds the ordered list of `ScenarioSpec`s from the seed. Each spec describes the PO,
   goods receipts and one or more invoices (series for partial invoicing, pairs for duplicates), the
   mutations applied to what the supplier "prints", and the intent.
5. For each spec, in order: create PO → approve → goods receipt(s) → create invoice(s) →
   `GET /match` per invoice. Order matters because "already invoiced" counts earlier invoices (lower id).
6. Render each invoice's PDF with the spec's template from the same data that was posted.
7. Write `labels/inv-NNNN.json`, then `split.py` assigns splits, then `manifest.json` and the dataset card.

Invoice IDs in the dataset (`inv-0001`…) are stable and independent of ERP database ids; ERP ids are
kept only under `erp` for provenance.

## Label format

```json
{
  "id": "inv-0042",
  "split": "test",
  "template": "nordbuero_de",
  "scenario": "price_above_tolerance",
  "group": null,
  "pdf": "invoices/inv-0042.pdf",
  "fields": {
    "supplier_name": "Nordbüro GmbH",
    "invoice_number": "NB-2026-0815",
    "invoice_date": "2026-03-14",
    "currency": "EUR",
    "po_number": "PO-000017",
    "lines": [
      { "description": "Kopierpapier A4, 500 Bl.", "quantity": "50", "unit_price": "4.15",
        "tax_rate": "19", "net_amount": "207.50" }
    ],
    "totals": { "net": "207.50", "tax": "39.43", "gross": "246.93" }
  },
  "intent": [ { "code": "PRICE_MISMATCH", "line": 1 } ],
  "expected_exceptions": [
    { "code": "PRICE_MISMATCH", "lineNumber": 1, "expected": "3.89", "actual": "4.15", "message": "…" }
  ],
  "erp": { "invoice_id": 311, "purchase_order_id": 17, "line_po_item_ids": [52] }
}
```

- `fields` contains **only what is printed** on the PDF, in canonical form (ISO dates, `.` decimal
  separator, no thousands separator, no currency symbols). `po_number` is `null` when not printed.
- `intent` is ordered like the matcher's output (header first, then by line, then by code order).
- `expected_exceptions` is copied verbatim from the ERP response (`[]` = matched).
- `group` links invoices that must stay in the same split (duplicate pairs, partial-invoicing series).
- `erp.line_po_item_ids[i]` is the PO line of invoice line `i+1`, or `null` (e.g. freight).

`manifest.json`: `dataset_version`, `seed`, `generated_at`, `erp_commit` (git hash of the repo at
generation), `generator_version`, counts, and the list of ids per split.

## Scenario mix (200 invoices)

| Group | Count | Content |
|---|---|---|
| Clean | 80 | normal invoices; partial deliveries billed correctly; **near misses that must pass**: price exactly ±0.5 %, tax `19.00` vs `19`, quantity exactly equal to received |
| Single exception | 96 | PRICE_MISMATCH 18 · QUANTITY_MISMATCH 18 · TAX_MISMATCH 14 · UNMATCHED_LINE 14 · DUPLICATE_INVOICE 12 · CURRENCY_MISMATCH 10 · MISSING_PO 10 |
| Combinations | 24 | price + tax on one line; partial-invoicing series ending in over-billing; MISSING_PO + DUPLICATE_INVOICE; unmatched freight + price; currency + quantity |

Every code appears in at least 3 of the 5 templates. Invoices have 1–8 lines; `bilingual` invoices with
>12 lines span two pages (~10 such invoices). Counts are invoices, so a duplicate pair contributes 2.

## Templates

Each template is a module with `render(invoice: PrintedInvoice) -> bytes` using reportlab (pure
Python, real text layer, no system dependencies). Supplier addresses and VAT IDs come from Faker with
the dataset seed.

| Template | Language / formats | Extraction difficulty |
|---|---|---|
| `nordbuero_de` | German · `14.03.2026` · `1.234,56 €` | German labels, decimal comma |
| `acme_us` | English · `Mar 4, 2026` · `$1,234.56` · USD | PO printed as "Your order ref." |
| `techsource_uk` | English · `04/03/2026` (day first) · "VAT" | ambiguous day/month order |
| `alpenpack_de` | German · VAT summary block instead of per-line rate | per-line tax rate must be inferred |
| `bilingual` | "Rechnung / Invoice" · ISO dates · multi-page | table continues over a page break |

## Dataset checks (`uv run pytest`)

1. **Intent == ERP** — for every label, `[(code, line)]` of `intent` equals that of `expected_exceptions`. *(user: E4)*
2. **Internally consistent** — line `net_amount` = quantity × unit_price (half-up, 2 places); totals add up.
3. **Extractable** — every value in `fields` appears in the PDF text (pdfplumber) in the template's printed format.
4. **Coverage** — each code ≥10 invoices; each template has clean and flagged invoices; total = 200.
5. **No leakage** — dev and test are disjoint and cover all ids; all invoices of a `group` share a split; split sizes 50 ± 5 / 150 ± 5. *(user: E4)*

`test_labels.py` unit-tests the Pydantic validators (E1) independently of a generated dataset.

## User exercises

| # | File | Task | Concept |
|---|---|---|---|
| E1 | `labels.py` | Pydantic models for the label format with `Decimal` fields and validators (line net, totals, canonical date) | structured data contracts — the same models become M3's LLM output schema |
| E2 | `scenarios.py` | 4–5 scenario functions on a prepared skeleton (price deviation incl. near miss, partial-invoicing over-billing, duplicate pair, unmatched freight, missing PO) | designing test cases that discriminate |
| E3 | `split.py` | stratified dev/test split by scenario group, keeping `group`s together | stratification, leakage |
| E4 | `test_dataset.py` | checks 1 and 5 | validating labels before trusting them |

Claude builds everything else, leaves all changes uncommitted, and suggests commit messages per step.

## Error handling

- ERP unreachable or not empty → abort before writing anything.
- Any non-2xx ERP response → abort with the scenario id and response body; no partial dataset is
  written (generation writes to a temp directory and moves it to `dataset/v1/` only on success).
- Intent ≠ ERP result is **not** a generation error (the run completes); it is reported by check 1 so
  all mismatches are visible at once.

## Testing

- Unit: `test_labels.py` (validators), small tests for number/date formatting per template.
- Dataset: `test_dataset.py` checks 1–5 over `dataset/v1/`.
- Manual: open one PDF per template and compare with its label.

## Done when

- `dataset/v1/` contains 200 PDFs + 200 labels + manifest + dataset card, committed by the user.
- `uv run pytest` in `eval/` is green.
- Dataset card shows counts per code, template and split, plus seed and ERP commit.
