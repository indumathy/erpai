"""Thin client for the erpai REST API — only the calls the generator needs.

Field names mirror the backend request DTOs (CreateSupplierRequest, CreatePurchaseOrderRequest, ...).
Responses are returned as plain dicts; every JSON number arrives as Decimal.
"""

import json
from datetime import date
from decimal import Decimal
from typing import Any  # Any = "any type" (Kotlin: Any?)

import httpx  # HTTP client library (Kotlin/Spring: RestClient / WebClient)

from golden.money import plain


# An exception class with only a docstring: no body needed (Kotlin: class ErpError(msg: String) : RuntimeException(msg)).
class ErpError(RuntimeError):
    """Non-2xx response from the ERP; the message carries method, path, status and body."""


def _jsonable(value: Any) -> Any:
    """Decimals and dates as strings: exact on the wire (Jackson reads "4.2009" into a BigDecimal)."""
    # isinstance = Kotlin `is`. Walks nested dicts/lists recursively and converts the leaves.
    if isinstance(value, Decimal):
        # shortest exact form: "4.20090" -> "4.2009", "100" stays "100" (never "1E+2").
        # Decimal keeps trailing zeros from multiplication (4.18 x 1.005 = 4.20090, scale 5),
        # and the ERP's @Digits(fraction = 4) counts them.
        return plain(value)
    if isinstance(value, date):
        return value.isoformat()  # date(2026, 3, 1) -> "2026-03-01"
    if isinstance(value, dict):
        return {key: _jsonable(item) for key, item in value.items()}
    if isinstance(value, (list, tuple)):
        return [_jsonable(item) for item in value]
    return value  # str, int, bool, None stay as they are


class ErpClient:
    # __init__ = constructor. `transport` lets tests plug in httpx.MockTransport instead of the network.
    def __init__(self, base_url: str = "http://localhost:8081", transport: httpx.BaseTransport | None = None) -> None:
        # Leading underscore: internal field (Kotlin: private val http).
        self._http = httpx.Client(base_url=f"{base_url.rstrip('/')}/api", transport=transport, timeout=30)

    def _call(self, method: str, path: str, body: Any = None) -> Any:
        """One place for sending, error handling and Decimal-safe parsing."""
        response = self._http.request(method, path, json=None if body is None else _jsonable(body))
        if response.is_error:  # any 4xx / 5xx
            raise ErpError(f"{method} {path} -> {response.status_code}: {response.text}")
        # parse_float=Decimal: 21.9000 stays Decimal("21.9000") and never becomes a binary float
        # (Jackson: USE_BIG_DECIMAL_FOR_FLOATS). Empty body (e.g. approve) -> None.
        return json.loads(response.text, parse_float=Decimal) if response.content else None

    def list_invoices(self) -> list[dict]:
        return self._call("GET", "/invoices")

    def create_supplier(self, number: str, name: str, currency: str, vat_id: str) -> int:
        body = {"supplierNumber": number, "name": name, "vatId": vat_id, "currency": currency, "paymentTermsDays": 30}
        return self._call("POST", "/suppliers", body)["id"]

    def create_product(self, sku: str, name: str) -> int:
        return self._call("POST", "/products", {"sku": sku, "name": name, "unitOfMeasure": "PIECE"})["id"]

    def warehouse_id(self, code: str = "MAIN") -> int:
        # next(... for ... if ...) = first match (Kotlin: warehouses.first { it.code == code }.id).
        # MAIN is created by the Flyway migration V4__create_inventory.sql.
        return next(w["id"] for w in self._call("GET", "/warehouses") if w["code"] == code)

    def create_purchase_order(self, supplier_id: int, order_date: date, items: list[dict]) -> dict:
        return self._call("POST", "/purchase-orders", {"supplierId": supplier_id, "orderDate": order_date, "items": items})

    def approve(self, po_id: int) -> None:
        # Goods can only be received for an approved PO.
        self._call("POST", f"/purchase-orders/{po_id}/approve")

    def receive(self, po_id: int, warehouse_id: int, receipt_date: date, items: list[dict]) -> None:
        body = {"purchaseOrderId": po_id, "warehouseId": warehouse_id, "receiptDate": receipt_date, "items": items}
        self._call("POST", "/goods-receipts", body)

    def create_invoice(self, body: dict) -> dict:
        return self._call("POST", "/invoices", body)

    def get_invoice(self, invoice_id: int) -> dict:
        return self._call("GET", f"/invoices/{invoice_id}")

    def match(self, invoice_id: int) -> dict:
        # The three-way match result from M1 — the source of expected_exceptions in every label.
        return self._call("GET", f"/invoices/{invoice_id}/match")
