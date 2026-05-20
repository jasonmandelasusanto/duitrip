import pytest
from app.services.settlement import calculate_balances, simplify_debts


# ── calculate_balances ────────────────────────────────────────────────────────

def _expense(paid_by: str, total: float, splits: list[tuple[str, float]]) -> dict:
    return {
        "paidBy": paid_by,
        "amountInDestinationCurrency": total,
        "splits": [{"userId": uid, "amountInDestinationCurrency": amt} for uid, amt in splits],
    }


def _settlement(from_uid: str, to_uid: str, amount: float) -> dict:
    return {"fromUserId": from_uid, "toUserId": to_uid, "amountInDestinationCurrency": amount}


class TestCalculateBalances:
    def test_single_expense_two_members_equal_split(self):
        exp = _expense("alice", 100, [("alice", 50), ("bob", 50)])
        b = calculate_balances([exp], [])
        assert abs(b["alice"] - 50) < 0.01   # paid 100, owes 50 → +50
        assert abs(b["bob"] - (-50)) < 0.01  # paid 0, owes 50 → -50

    def test_three_members_one_payer(self):
        exp = _expense("alice", 90, [("alice", 30), ("bob", 30), ("carol", 30)])
        b = calculate_balances([exp], [])
        assert abs(b["alice"] - 60) < 0.01   # paid 90, owes 30 → +60
        assert abs(b["bob"] - (-30)) < 0.01
        assert abs(b["carol"] - (-30)) < 0.01

    def test_multiple_expenses(self):
        exps = [
            _expense("alice", 100, [("alice", 50), ("bob", 50)]),
            _expense("bob", 60, [("alice", 30), ("bob", 30)]),
        ]
        b = calculate_balances(exps, [])
        # alice: paid 100, owes 50+30=80 → +20
        # bob:   paid 60, owes 50+30=80 → -20
        assert abs(b["alice"] - 20) < 0.01
        assert abs(b["bob"] - (-20)) < 0.01

    def test_settlement_reduces_debt(self):
        exp = _expense("alice", 100, [("alice", 50), ("bob", 50)])
        stl = _settlement("bob", "alice", 50)
        b = calculate_balances([exp], [stl])
        assert abs(b["alice"]) < 0.01
        assert abs(b["bob"]) < 0.01

    def test_partial_settlement(self):
        exp = _expense("alice", 100, [("alice", 50), ("bob", 50)])
        stl = _settlement("bob", "alice", 30)
        b = calculate_balances([exp], [stl])
        assert abs(b["alice"] - 20) < 0.01
        assert abs(b["bob"] - (-20)) < 0.01

    def test_empty_inputs(self):
        assert calculate_balances([], []) == {}

    def test_net_zero_group(self):
        """Total credits must equal total debits across all members."""
        exps = [
            _expense("alice", 90, [("alice", 30), ("bob", 30), ("carol", 30)]),
            _expense("bob", 60, [("alice", 20), ("bob", 20), ("carol", 20)]),
        ]
        b = calculate_balances(exps, [])
        total = sum(b.values())
        assert abs(total) < 0.01


# ── simplify_debts ────────────────────────────────────────────────────────────

class TestSimplifyDebts:
    def test_two_members(self):
        txs = simplify_debts({"alice": 50, "bob": -50})
        assert len(txs) == 1
        assert txs[0]["from"] == "bob"
        assert txs[0]["to"] == "alice"
        assert abs(txs[0]["amount"] - 50) < 0.01

    def test_three_members_one_owed(self):
        # alice +80, bob -40, carol -40
        txs = simplify_debts({"alice": 80, "bob": -40, "carol": -40})
        assert len(txs) == 2
        total_transferred = sum(t["amount"] for t in txs)
        assert abs(total_transferred - 80) < 0.01
        assert all(t["to"] == "alice" for t in txs)

    def test_already_settled_produces_no_transactions(self):
        txs = simplify_debts({"alice": 0, "bob": 0})
        assert txs == []

    def test_minimal_transaction_count(self):
        # 4-person: alice owed by bob AND carol, dave overpaid — should minimise txs
        balances = {"alice": 60, "bob": -20, "carol": -20, "dave": -20}
        txs = simplify_debts(balances)
        total_transferred = sum(t["amount"] for t in txs)
        assert abs(total_transferred - 60) < 0.01
        assert len(txs) <= 3

    def test_amounts_are_rounded_to_cents(self):
        txs = simplify_debts({"alice": 33.333, "bob": -33.333})
        assert txs[0]["amount"] == round(txs[0]["amount"], 2)

    def test_net_zero_group_no_transactions(self):
        txs = simplify_debts({"alice": 0.001, "bob": -0.001})
        # Both under the 0.005 threshold — should produce no transactions
        assert txs == []
