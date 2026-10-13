# Safety tests for the generator (golden/generate.py). No real ERP: a tiny fake is enough, because
# these tests only check what happens BEFORE and WHEN something goes wrong.
from pathlib import Path

import pytest

from golden.erp_client import ErpError
from golden.generate import generate


# FAKE: just enough ERP for the safety checks — reports invoices, or fails on the first write.
# Python needs no interface: any object with the right methods works ("duck typing").
class FakeErp:
    """Just enough ERP for the safety checks: reports invoices or fails on the first write."""

    def __init__(self, invoices=(), fail_on_create=False):
        self._invoices = list(invoices)
        self._fail = fail_on_create

    def list_invoices(self):
        return self._invoices

    def create_supplier(self, *args):
        if self._fail:
            raise ErpError("POST /suppliers -> 500: boom")
        return 1


# TEST: an ERP that already has invoices is refused, and nothing is written.
# tmp_path = pytest gives each test its own empty temp folder (JUnit: @TempDir).
def test_refuses_non_empty_erp(tmp_path: Path):
    out = tmp_path / "v1"  # Path / "x" = Kotlin path.resolve("x")
    with pytest.raises(SystemExit, match="already has invoices"):
        generate(FakeErp(invoices=[{"id": 1}]), 42, out)
    assert not out.exists()


# TEST: if the ERP fails halfway, the previous dataset stays untouched and no temp folder is left.
def test_failed_run_keeps_previous_dataset(tmp_path: Path):
    out = tmp_path / "v1"
    out.mkdir()
    (out / "manifest.json").write_text("previous", encoding="utf-8")
    with pytest.raises(ErpError):
        generate(FakeErp(fail_on_create=True), 42, out)
    assert (out / "manifest.json").read_text(encoding="utf-8") == "previous"
    assert [p.name for p in tmp_path.iterdir()] == ["v1"]  # no temp directory left behind
