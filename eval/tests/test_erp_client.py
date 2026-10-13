# Tests for golden/erp_client.py. No real ERP needed: httpx.MockTransport answers every
# request with a canned response (Kotlin/Spring: MockRestServiceServer / WireMock).
import json
from datetime import date
from decimal import Decimal

import httpx
import pytest

from golden.erp_client import ErpClient, ErpError


def client_with(handler) -> ErpClient:
    # handler = a function request -> response; the client never opens a network connection.
    return ErpClient("http://erp.test", transport=httpx.MockTransport(handler))


def test_response_numbers_are_decimals():
    # The ERP sends 21.9000 as a JSON number; it must arrive as Decimal("21.9000"), never as float.
    def handler(request):
        return httpx.Response(200, text='{"id": 1, "items": [{"unitPrice": 21.9000, "quantity": 50.000}]}')

    invoice = client_with(handler).get_invoice(1)
    assert invoice["items"][0]["unitPrice"] == Decimal("21.9000")
    assert isinstance(invoice["items"][0]["quantity"], Decimal)


def test_request_body_sends_decimals_and_dates_as_exact_strings():
    # Captures what the client sends, then checks path and JSON body (CreatePurchaseOrderRequest).
    seen = {}

    def handler(request):
        seen["path"] = request.url.path
        seen["body"] = json.loads(request.content)
        return httpx.Response(201, text='{"id": 5, "items": []}')

    client_with(handler).create_purchase_order(
        3, date(2026, 3, 1), [{"productId": 9, "quantity": Decimal("12"), "unitPrice": Decimal("4.2009"), "taxRate": Decimal("19")}]
    )
    assert seen["path"] == "/api/purchase-orders"
    assert seen["body"] == {
        "supplierId": 3, "orderDate": "2026-03-01",
        "items": [{"productId": 9, "quantity": "12", "unitPrice": "4.2009", "taxRate": "19"}],
    }


# TEST: trailing zeros are dropped before sending — 4.18 x 1.005 = Decimal("4.20090") has scale 5,
# and the ERP's @Digits(fraction = 4) would reject "4.20090" although the value has only 4 decimals.
def test_request_decimals_are_sent_without_trailing_zeros():
    seen = {}

    def handler(request):
        seen["body"] = json.loads(request.content)
        return httpx.Response(201, text='{"id": 5}')

    client_with(handler).create_invoice({"items": [{"unitPrice": Decimal("4.18") * Decimal("1.005"), "quantity": Decimal("100")}]})
    assert seen["body"]["items"][0] == {"unitPrice": "4.2009", "quantity": "100"}


def test_error_response_raises_with_status_and_body():
    # A 4xx/5xx must fail loudly with method, path, status and the ERP's error text.
    def handler(request):
        return httpx.Response(422, text='{"detail":"Receipt date lies in the future"}')

    # match= is a regex searched in the exception message.
    with pytest.raises(ErpError, match=r"POST /goods-receipts -> 422: .*future"):
        client_with(handler).receive(1, 1, date(2026, 3, 1), [{"purchaseOrderItemId": 1, "quantity": Decimal(1)}])


def test_warehouse_id_finds_the_default_warehouse():
    def handler(request):
        return httpx.Response(200, text='[{"id": 4, "code": "HAM"}, {"id": 1, "code": "MAIN"}]')

    assert client_with(handler).warehouse_id() == 1
