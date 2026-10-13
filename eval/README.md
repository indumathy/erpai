# eval — golden dataset (M2)

Synthetic, labelled supplier invoices for measuring erpai's AI features. The dataset itself lives in
[`dataset/v1/`](dataset/v1/README.md) and is committed, so evals need neither the database nor the backend.

Each invoice has a PDF and a JSON label:

- `fields` — what the PDF shows (supplier, number, date, currency, PO, lines, totals): the answer key
  for extraction (M3)
- `intent` — the exception(s) the scenario was built to cause
- `expected_exceptions` — what the ERP's three-way matcher actually reported
  (`GET /api/invoices/{id}/match`): the ground truth
- `split` — `dev` (tune on these) or `test` (report on these)

## Layout

- `src/golden/` — the generator
  - `scenarios.py` — what to test: 15 scenario functions and the catalog (183 stories → 200 invoices)
  - `erp_client.py` — creates suppliers, POs, receipts and invoices in the ERP over REST
  - `templates/` — 5 supplier PDF layouts (German, US, UK, German without VAT column, bilingual)
  - `labels.py` — label format (pydantic), `split.py` — stratified dev/test split
  - `generate.py` — runs everything, `card.py` — writes the dataset card
  - `model.py`, `suppliers.py`, `money.py` — data classes, master data, ERP-identical rounding
- `tests/` — unit tests, and `test_dataset.py` (5 checks over the finished dataset)
- `scripts/golden-erp.sh` — starts a separate ERP for generation
- `dataset/v1/` — `invoices/*.pdf`, `labels/*.json`, `manifest.json`, `README.md` (dataset card)

## Run the checks

    cd eval
    uv sync
    uv run pytest

No backend needed. `test_dataset.py` checks the committed data: intent equals the ERP result, labels add
up, every label value is printed on its PDF, coverage per code and template, no dev/test leakage.

## Regenerate

Labels come from the ERP's matcher, so generation needs a fresh, empty ERP. Use Git Bash for the script
(in PowerShell, `bash` starts WSL):

    bash eval/scripts/golden-erp.sh                  # terminal 1: recreates DB erpai_golden, backend on :8081
    cd eval && uv run python -m golden.generate      # terminal 2

`golden-erp.sh` uses the local PostgreSQL on port 5432 (user `vacos`, like `backend/application.yml`)
with its own database `erpai_golden`, so the normal ERP's data is never touched. Override with
`GOLDEN_DB_HOST`, `GOLDEN_DB_PORT`, `GOLDEN_DB_USER`, `GOLDEN_DB_PASSWORD`, `GOLDEN_PSQL`, `GOLDEN_JAVA_HOME`.

Safety and reproducibility:

- The generator refuses to run against an ERP that already has invoices (ids would shift).
- It writes into a temp folder and replaces `dataset/v1/` only when the whole run succeeded.
- Same seed + empty ERP → byte-identical labels and PDFs; only `generated_at` in `manifest.json` changes.
- If the matching rules change, generate `v2` instead of overwriting `v1`, so earlier eval reports stay
  comparable.
