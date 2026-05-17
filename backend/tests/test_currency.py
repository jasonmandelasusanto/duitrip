import pytest
from app.services.currency import (
    calculate_equal_splits,
    calculate_percentage_splits,
    calculate_exact_splits,
)


def test_equal_splits_basic():
    splits = calculate_equal_splits(100.0, ["a", "b", "c"], "a")
    assert len(splits) == 3
    total = sum(s["amountInDestinationCurrency"] for s in splits)
    assert abs(total - 100.0) < 0.01


def test_equal_splits_remainder_to_payer():
    # 10 / 3 = 3.33 each, remainder 0.01 goes to payer
    splits = calculate_equal_splits(10.0, ["payer", "b", "c"], "payer")
    payer_split = next(s for s in splits if s["userId"] == "payer")
    others = [s for s in splits if s["userId"] != "payer"]
    # Payer gets a bit more due to remainder
    assert payer_split["amountInDestinationCurrency"] >= others[0]["amountInDestinationCurrency"]
    total = sum(s["amountInDestinationCurrency"] for s in splits)
    assert abs(total - 10.0) < 0.005


def test_equal_splits_two_members():
    splits = calculate_equal_splits(100.0, ["a", "b"], "a")
    assert splits[0]["amountInDestinationCurrency"] == 50.0
    assert splits[1]["amountInDestinationCurrency"] == 50.0


def test_percentage_splits_sum_100():
    inputs = [
        {"userId": "a", "percentage": 50},
        {"userId": "b", "percentage": 30},
        {"userId": "c", "percentage": 20},
    ]
    splits = calculate_percentage_splits(200.0, inputs, "a")
    assert len(splits) == 3
    total = sum(s["amountInDestinationCurrency"] for s in splits)
    assert abs(total - 200.0) < 0.01


def test_percentage_splits_invalid_sum():
    inputs = [{"userId": "a", "percentage": 60}, {"userId": "b", "percentage": 60}]
    with pytest.raises(ValueError):
        calculate_percentage_splits(100.0, inputs, "a")


def test_exact_splits_valid():
    inputs = [
        {"userId": "a", "exactAmount": 50.0, "exactCurrency": "SGD"},
        {"userId": "b", "exactAmount": 50.0, "exactCurrency": "SGD"},
    ]
    rates = {"SGD": 1.0}
    splits = calculate_exact_splits(100.0, inputs, rates, "SGD", "a")
    total = sum(s["amountInDestinationCurrency"] for s in splits)
    assert abs(total - 100.0) < 0.01


def test_exact_splits_mismatch_raises():
    inputs = [
        {"userId": "a", "exactAmount": 30.0, "exactCurrency": "SGD"},
        {"userId": "b", "exactAmount": 30.0, "exactCurrency": "SGD"},
    ]
    rates = {"SGD": 1.0}
    with pytest.raises(ValueError):
        calculate_exact_splits(100.0, inputs, rates, "SGD", "a")


def test_settlement_simplification():
    from app.services.settlement import calculate_balances, simplify_debts

    expenses = [
        {"paidBy": "alice", "amountInDestinationCurrency": 100, "splits": [
            {"userId": "alice", "amountInDestinationCurrency": 50},
            {"userId": "bob", "amountInDestinationCurrency": 50},
        ]},
    ]
    balances = calculate_balances(expenses, [])
    assert balances["alice"] == 50.0  # paid 100, owes 50 → net +50
    assert balances["bob"] == -50.0   # paid 0, owes 50 → net -50

    transactions = simplify_debts(balances)
    assert len(transactions) == 1
    assert transactions[0]["from"] == "bob"
    assert transactions[0]["to"] == "alice"
    assert abs(transactions[0]["amount"] - 50.0) < 0.01
