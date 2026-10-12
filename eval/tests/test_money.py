from decimal import Decimal

from golden.money import amount, line_net, plain


def test_amount_rounds_half_up_to_cents():
    assert amount(Decimal("1.005")) == Decimal("1.01")
    assert amount(Decimal("1.004")) == Decimal("1.00")
    assert str(amount(Decimal("7"))) == "7.00"


def test_line_net_matches_erp_document_totals():
    assert line_net(Decimal("3"), Decimal("0.335")) == Decimal("1.01")
    assert line_net(Decimal("50"), Decimal("4.18")) == Decimal("209.00")


def test_plain_strips_scale_without_scientific_notation():
    assert plain(Decimal("50.000")) == "50"
    assert plain(Decimal("21.9000")) == "21.9"
    assert plain(Decimal("100")) == "100"
    assert plain(Decimal("0.50")) == "0.5"
