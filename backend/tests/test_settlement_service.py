import pytest
from app.services.settlement import calculate_balances, simplify_debts, bilateral_debts
from app.services.currency import calculate_equal_splits

# ── Yogyakarta integration fixture ────────────────────────────────────────────
# Real trip data (names anonymised). alpha=payer1, beta=payer2, gamma=payer3.
# Amounts are in IDR (trip currency). SGD amounts already converted in source CSV.
# Grand total: 24,474,748.23 IDR  |  3 members equal-split throughout.
_YOGYA_MEMBERS = ["alpha", "beta", "gamma"]

_YOGYA_RAW = [
    #  payer    amount (IDR)     description
    ("beta",   356399.00),   # Breakfast Hyatt
    ("beta",   198000.00),   # Soto & Sop Cak Nur
    ("alpha",  300000.00),   # Driver last day
    ("gamma",  400000.00),   # Extra Bed
    ("alpha",   82280.00),   # Hyatt Chicken Porridge
    ("alpha",  472222.22),   # Go car from Changi (SGD converted)
    ("gamma",  350000.00),   # Longue Cordodia
    ("alpha",   45000.00),   # Laundry
    ("alpha",   42879.00),   # Klepon Gendhis
    ("alpha",  171900.00),   # Lunch at jejamuran
    ("alpha",   40000.00),   # Borobudur Museum Tickets
    ("alpha",  356399.00),   # Breakfast Hyatt
    ("beta",  1591688.00),   # Private tour candi
    ("alpha",  191900.00),   # Madam Tan Obelix
    ("alpha",   50000.00),   # Matcha On The Rock
    ("gamma",   35000.00),   # Go Car To Malioboro
    ("alpha",  365611.00),   # Dinner at Hyatt Seafood
    ("gamma",  150000.00),   # Train to YIA
    ("alpha",  356399.00),   # Breakfast Hyatt
    ("alpha",  700000.00),   # Driver 1 day + Park
    ("alpha",  100000.00),   # On the rock tickets
    ("gamma",  100000.00),   # Obelix Entry Tickets
    ("alpha",   45000.00),   # Laundry
    ("gamma",  400000.00),   # Massage deWave
    ("alpha",   48000.00),   # Go Car to Hyatt
    ("alpha",  211750.00),   # Dinner Angkringan Hyatt
    ("gamma",  356399.00),   # Breakfast 1 day
    ("gamma",  300000.00),   # Wayang kulit Arjuna
    ("gamma",  299079.00),   # Lunch Bale Raos
    ("gamma",   60500.00),   # Gocar to Bale Raos
    ("alpha",   75000.00),   # UGM Goodie Bag
    ("alpha",   70000.00),   # Pukis Mirota
    ("alpha",  391666.67),   # Taxi to Changi Airport (SGD converted)
    ("alpha",   81628.00),   # Cafe Snack Hyatt
    ("alpha",   75000.00),   # Train Ticket YIA to Tugu
    ("alpha",   90000.00),   # Dinner Hyatt Angkringan
    ("alpha",   69500.00),   # Go Car to Hotel + Tips
    ("alpha",  712916.67),   # 20kg Scoot Baggage (SGD converted)
    ("gamma", 7552215.00),   # Hyatt Regency Hotel
    ("alpha", 7180416.67),   # Flight Ticket Round-Trip (SGD converted)
]


def _build_yogya_expenses() -> list[dict]:
    """Build expense dicts from raw data using the real split calculator."""
    expenses = []
    for payer, total in _YOGYA_RAW:
        raw_splits = calculate_equal_splits(total, _YOGYA_MEMBERS, payer)
        expenses.append({
            "paidBy": payer,
            "amountInDestinationCurrency": total,
            "splits": [
                {"userId": s["userId"], "amountInDestinationCurrency": s["amountInDestinationCurrency"]}
                for s in raw_splits
            ],
        })
    return expenses


# ── helpers ───────────────────────────────────────────────────────────────────

def _expense(paid_by: str, total: float, splits: list[tuple[str, float]]) -> dict:
    return {
        "paidBy": paid_by,
        "amountInDestinationCurrency": total,
        "splits": [{"userId": uid, "amountInDestinationCurrency": amt} for uid, amt in splits],
    }


def _settlement(from_uid: str, to_uid: str, amount: float) -> dict:
    return {"fromUserId": from_uid, "toUserId": to_uid, "amountInDestinationCurrency": amount}


def _net_zero(balances: dict) -> bool:
    """Invariant: sum of all balances must be 0 (money is conserved)."""
    return abs(sum(balances.values())) < 0.01


def _total_transferred(txs: list) -> float:
    return sum(t["amount"] for t in txs)


# ── calculate_balances ────────────────────────────────────────────────────────

class TestCalculateBalances:
    # ── basic cases (payer in splits) ─────────────────────────────────────────

    def test_single_expense_two_members_equal_split(self):
        exp = _expense("alice", 100, [("alice", 50), ("bob", 50)])
        b = calculate_balances([exp], [])
        assert abs(b["alice"] - 50) < 0.01   # paid 100, owes 50 → +50
        assert abs(b["bob"] - (-50)) < 0.01  # paid 0, owes 50 → -50
        assert _net_zero(b)

    def test_three_members_one_payer(self):
        exp = _expense("alice", 90, [("alice", 30), ("bob", 30), ("carol", 30)])
        b = calculate_balances([exp], [])
        assert abs(b["alice"] - 60) < 0.01
        assert abs(b["bob"] - (-30)) < 0.01
        assert abs(b["carol"] - (-30)) < 0.01
        assert _net_zero(b)

    def test_multiple_expenses(self):
        exps = [
            _expense("alice", 100, [("alice", 50), ("bob", 50)]),
            _expense("bob", 60, [("alice", 30), ("bob", 30)]),
        ]
        b = calculate_balances(exps, [])
        # alice: paid 100, owes 50+30=80 → +20
        # bob:   paid 60,  owes 50+30=80 → -20
        assert abs(b["alice"] - 20) < 0.01
        assert abs(b["bob"] - (-20)) < 0.01
        assert _net_zero(b)

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

    def test_net_zero_invariant_multi_expense(self):
        exps = [
            _expense("alice", 90, [("alice", 30), ("bob", 30), ("carol", 30)]),
            _expense("bob", 60, [("alice", 20), ("bob", 20), ("carol", 20)]),
        ]
        b = calculate_balances(exps, [])
        assert _net_zero(b)

    # ── payer NOT in splits (new "Split Between" feature) ─────────────────────

    def test_payer_not_in_splits_two_members(self):
        """Alice paid 100 for Bob only. Bob owes Alice 100."""
        exp = _expense("alice", 100, [("bob", 100)])
        b = calculate_balances([exp], [])
        assert abs(b["alice"] - 100) < 0.01   # paid 100, no debit → +100
        assert abs(b["bob"] - (-100)) < 0.01  # owes 100 → -100
        assert _net_zero(b)

    def test_payer_not_in_splits_three_members_two_in_split(self):
        """Alice paid 100 for Bob and Carol equally. Both owe Alice 50."""
        splits = calculate_equal_splits(100, ["bob", "carol"], "alice")
        exp = _expense("alice", 100, [(s["userId"], s["amountInDestinationCurrency"]) for s in splits])
        b = calculate_balances([exp], [])
        assert abs(b["alice"] - 100) < 0.01
        assert abs(b["bob"] - (-50)) < 0.01
        assert abs(b["carol"] - (-50)) < 0.01
        assert _net_zero(b)

    def test_payer_not_in_splits_uneven_total(self):
        """Alice paid 100 for Bob and Carol. Total must distribute fully (remainder handled)."""
        # 100 / 3 would be uneven, but here we have 2 recipients
        splits = calculate_equal_splits(101, ["bob", "carol"], "alice")
        total_split = sum(s["amountInDestinationCurrency"] for s in splits)
        assert abs(total_split - 101) < 0.01  # splits must sum to total

        exp = _expense("alice", 101, [(s["userId"], s["amountInDestinationCurrency"]) for s in splits])
        b = calculate_balances([exp], [])
        assert _net_zero(b)
        assert b["alice"] > 0  # alice is owed money

    def test_payer_not_in_splits_three_recipients_odd_remainder(self):
        """Payer pays 100 for 3 others; remainder must land on a split member, not vanish."""
        splits = calculate_equal_splits(100, ["bob", "carol", "dave"], "alice")
        total_split = sum(s["amountInDestinationCurrency"] for s in splits)
        assert abs(total_split - 100) < 0.01

        exp = _expense("alice", 100, [(s["userId"], s["amountInDestinationCurrency"]) for s in splits])
        b = calculate_balances([exp], [])
        assert _net_zero(b)

    def test_payer_not_in_splits_large_idr_amount(self):
        """Realistic IDR scenario: payer advances 1,591,688 IDR for 3 others."""
        total = 1_591_688.0
        splits = calculate_equal_splits(total, ["bob", "carol", "dave"], "alice")
        total_split = sum(s["amountInDestinationCurrency"] for s in splits)
        assert abs(total_split - total) < 0.01

        exp = _expense("alice", total, [(s["userId"], s["amountInDestinationCurrency"]) for s in splits])
        b = calculate_balances([exp], [])
        assert _net_zero(b)
        assert abs(b["alice"] - total) < 0.01

    def test_mixed_payer_in_and_not_in_splits(self):
        """
        Expense 1: Alice paid 90 split equally among Alice+Bob+Carol (payer in splits).
        Expense 2: Alice paid 60 for Bob only (payer NOT in splits).
        """
        exp1 = _expense("alice", 90, [("alice", 30), ("bob", 30), ("carol", 30)])
        splits2 = calculate_equal_splits(60, ["bob"], "alice")
        exp2 = _expense("alice", 60, [(s["userId"], s["amountInDestinationCurrency"]) for s in splits2])

        b = calculate_balances([exp1, exp2], [])
        # alice: +90+60 - 30(own split) = +120
        # bob: -30-60 = -90
        # carol: -30
        assert abs(b["alice"] - 120) < 0.01
        assert abs(b["bob"] - (-90)) < 0.01
        assert abs(b["carol"] - (-30)) < 0.01
        assert _net_zero(b)

    def test_settlement_with_payer_not_in_splits(self):
        """Partial settlement after payer-not-in-splits expense."""
        exp = _expense("alice", 100, [("bob", 100)])
        stl = _settlement("bob", "alice", 40)
        b = calculate_balances([exp], [stl])
        assert abs(b["alice"] - 60) < 0.01
        assert abs(b["bob"] - (-60)) < 0.01
        assert _net_zero(b)

    def test_full_settlement_with_payer_not_in_splits(self):
        exp = _expense("alice", 100, [("bob", 100)])
        stl = _settlement("bob", "alice", 100)
        b = calculate_balances([exp], [stl])
        assert abs(b["alice"]) < 0.01
        assert abs(b["bob"]) < 0.01

    def test_ghost_payer_not_in_splits(self):
        """Ghost member pays for real user — common import scenario."""
        exp = _expense("ghost_aaa", 45000, [("jason", 45000)])
        b = calculate_balances([exp], [])
        assert abs(b["ghost_aaa"] - 45000) < 0.01
        assert abs(b["jason"] - (-45000)) < 0.01
        assert _net_zero(b)

    def test_cross_payments_payer_not_in_splits(self):
        """
        Alice pays 100 for Bob only.
        Bob pays 60 for Alice only.
        Net: Alice owed 40, Bob owes 40.
        """
        exp1 = _expense("alice", 100, [("bob", 100)])
        exp2 = _expense("bob", 60, [("alice", 60)])
        b = calculate_balances([exp1, exp2], [])
        assert abs(b["alice"] - 40) < 0.01
        assert abs(b["bob"] - (-40)) < 0.01
        assert _net_zero(b)

    def test_three_way_payer_not_in_splits(self):
        """
        Jason paid 300 for Ghany and Fredy (3 people trip, Jason not consuming).
        Ghany owes Jason 150, Fredy owes Jason 150.
        """
        splits = calculate_equal_splits(300, ["ghany", "fredy"], "jason")
        exp = _expense("jason", 300, [(s["userId"], s["amountInDestinationCurrency"]) for s in splits])
        b = calculate_balances([exp], [])
        assert abs(b["jason"] - 300) < 0.01
        assert abs(b["ghany"] - (-150)) < 0.01
        assert abs(b["fredy"] - (-150)) < 0.01
        assert _net_zero(b)


# ── simplify_debts ────────────────────────────────────────────────────────────

class TestSimplifyDebts:
    def test_two_members(self):
        txs = simplify_debts({"alice": 50, "bob": -50})
        assert len(txs) == 1
        assert txs[0]["from"] == "bob"
        assert txs[0]["to"] == "alice"
        assert abs(txs[0]["amount"] - 50) < 0.01

    def test_three_members_one_owed(self):
        txs = simplify_debts({"alice": 80, "bob": -40, "carol": -40})
        assert len(txs) == 2
        assert abs(_total_transferred(txs) - 80) < 0.01
        assert all(t["to"] == "alice" for t in txs)

    def test_already_settled_produces_no_transactions(self):
        txs = simplify_debts({"alice": 0, "bob": 0})
        assert txs == []

    def test_minimal_transaction_count(self):
        balances = {"alice": 60, "bob": -20, "carol": -20, "dave": -20}
        txs = simplify_debts(balances)
        assert abs(_total_transferred(txs) - 60) < 0.01
        assert len(txs) <= 3

    def test_amounts_are_rounded_to_cents(self):
        txs = simplify_debts({"alice": 33.333, "bob": -33.333})
        assert txs[0]["amount"] == round(txs[0]["amount"], 2)

    def test_net_zero_group_no_transactions(self):
        txs = simplify_debts({"alice": 0.001, "bob": -0.001})
        assert txs == []

    def test_payer_not_in_splits_produces_correct_transaction(self):
        """From calculate_balances: alice +100, bob -100 → bob pays alice 100."""
        txs = simplify_debts({"alice": 100, "bob": -100})
        assert len(txs) == 1
        assert txs[0]["from"] == "bob"
        assert txs[0]["to"] == "alice"
        assert abs(txs[0]["amount"] - 100) < 0.01

    def test_three_recipients_owe_payer(self):
        """Jason paid for Ghany and Fredy; both owe Jason."""
        txs = simplify_debts({"jason": 300, "ghany": -150, "fredy": -150})
        assert len(txs) == 2
        assert abs(_total_transferred(txs) - 300) < 0.01
        assert all(t["to"] == "jason" for t in txs)

    def test_complex_four_person_settlement(self):
        """
        Four people, several expenses. Verify total transferred equals total owed.
        alice: +120, bob: -90, carol: -30, dave: 0
        """
        balances = {"alice": 120, "bob": -90, "carol": -30, "dave": 0}
        txs = simplify_debts(balances)
        assert abs(_total_transferred(txs) - 120) < 0.01
        assert all(t["to"] == "alice" for t in txs)

    def test_cross_debt_minimised(self):
        """
        alice: +40, bob: -40 (cross-payments already netted by calculate_balances).
        Should be 1 transaction, not 2.
        """
        txs = simplify_debts({"alice": 40, "bob": -40})
        assert len(txs) == 1


# ── calculate_equal_splits (remainder invariant) ──────────────────────────────

class TestEqualSplitRemainder:
    def test_payer_in_splits_gets_remainder(self):
        splits = calculate_equal_splits(100, ["alice", "bob", "carol"], "alice")
        total = sum(s["amountInDestinationCurrency"] for s in splits)
        assert abs(total - 100) < 0.01

    def test_payer_not_in_splits_first_member_gets_remainder(self):
        splits = calculate_equal_splits(100, ["bob", "carol", "dave"], "alice")
        total = sum(s["amountInDestinationCurrency"] for s in splits)
        assert abs(total - 100) < 0.01

    def test_single_member_payer_not_in_splits(self):
        splits = calculate_equal_splits(45.8, ["bob"], "alice")
        assert len(splits) == 1
        assert abs(splits[0]["amountInDestinationCurrency"] - 45.8) < 0.01

    def test_large_idr_amount_three_members_no_remainder_lost(self):
        total = 7_800_000.0
        splits = calculate_equal_splits(total, ["ghany", "fredy", "jason"], "danny")
        assert abs(sum(s["amountInDestinationCurrency"] for s in splits) - total) < 0.01

    def test_odd_remainder_distributed(self):
        # 10 / 3 = 3.33 each, remainder 0.01 must land on someone
        splits = calculate_equal_splits(10, ["bob", "carol", "dave"], "alice")
        assert abs(sum(s["amountInDestinationCurrency"] for s in splits) - 10) < 0.01


# ── Yogyakarta real-trip integration tests ────────────────────────────────────

class TestYogyakartaIntegration:
    """
    End-to-end settlement test using anonymised real trip data (Yogyakarta).
    3 members (alpha, beta, gamma), all expenses split equally.

    Expected (computed from calculate_equal_splits applied to every expense):
      Grand total   : 24,474,748.23 IDR
      alpha paid    : 12,325,468.23 IDR  → balance ≈ +4,167,218.71 (owed)
      beta  paid    :  2,146,087.00 IDR  → balance ≈ -6,012,162.32 (owes)
      gamma paid    : 10,003,193.00 IDR  → balance ≈ +1,844,943.61 (owed)
      Transactions  : beta → alpha 4,167,218.71  |  beta → gamma 1,844,943.61
    """

    def test_grand_total(self):
        assert abs(sum(a for _, a in _YOGYA_RAW) - 24_474_748.23) < 0.01

    def test_paid_totals_per_member(self):
        paid = {"alpha": 0.0, "beta": 0.0, "gamma": 0.0}
        for payer, amt in _YOGYA_RAW:
            paid[payer] += amt
        assert abs(paid["alpha"] - 12_325_468.23) < 0.01
        assert abs(paid["beta"]  -  2_146_087.00) < 0.01
        assert abs(paid["gamma"] - 10_003_193.00) < 0.01

    def test_net_zero_invariant(self):
        expenses = _build_yogya_expenses()
        b = calculate_balances(expenses, [])
        assert _net_zero(b), f"Balances do not net to zero: {b}"

    def test_balance_directions(self):
        """alpha and gamma are owed money; beta owes money."""
        expenses = _build_yogya_expenses()
        b = calculate_balances(expenses, [])
        assert b["alpha"] > 0, "alpha should be owed money"
        assert b["beta"]  < 0, "beta should owe money"
        assert b["gamma"] > 0, "gamma should be owed money"

    def test_balance_amounts(self):
        expenses = _build_yogya_expenses()
        b = calculate_balances(expenses, [])
        # Allow ±5 IDR tolerance for accumulated floor2 rounding across 40 expenses
        assert abs(b["alpha"] -  4_167_218.71) < 5
        assert abs(b["beta"]  - (-6_012_162.32)) < 5
        assert abs(b["gamma"] -  1_844_943.61) < 5

    def test_settlement_transaction_count(self):
        """One debtor (beta), two creditors → exactly 2 transactions."""
        expenses = _build_yogya_expenses()
        b = calculate_balances(expenses, [])
        txs = simplify_debts(b)
        assert len(txs) == 2

    def test_settlement_debtor_is_beta(self):
        expenses = _build_yogya_expenses()
        b = calculate_balances(expenses, [])
        txs = simplify_debts(b)
        assert all(t["from"] == "beta" for t in txs)

    def test_settlement_creditors_are_alpha_and_gamma(self):
        expenses = _build_yogya_expenses()
        b = calculate_balances(expenses, [])
        txs = simplify_debts(b)
        recipients = {t["to"] for t in txs}
        assert recipients == {"alpha", "gamma"}

    def test_settlement_amounts(self):
        expenses = _build_yogya_expenses()
        b = calculate_balances(expenses, [])
        txs = simplify_debts(b)
        to_alpha = next(t["amount"] for t in txs if t["to"] == "alpha")
        to_gamma = next(t["amount"] for t in txs if t["to"] == "gamma")
        assert abs(to_alpha - 4_167_218.71) < 5
        assert abs(to_gamma - 1_844_943.61) < 5

    def test_settlement_total_equals_beta_debt(self):
        """Total transferred must exactly cover beta's debt."""
        expenses = _build_yogya_expenses()
        b = calculate_balances(expenses, [])
        txs = simplify_debts(b)
        assert abs(_total_transferred(txs) - abs(b["beta"])) < 0.01

    def test_full_settlement_clears_all_balances(self):
        """After recording both settlements, all balances reach zero."""
        expenses = _build_yogya_expenses()
        b = calculate_balances(expenses, [])
        txs = simplify_debts(b)
        stls = [
            {"fromUserId": t["from"], "toUserId": t["to"], "amountInDestinationCurrency": t["amount"]}
            for t in txs
        ]
        b2 = calculate_balances(expenses, stls)
        for uid, bal in b2.items():
            assert abs(bal) < 0.01, f"{uid} not settled: {bal}"


# ── bilateral_debts ────────────────────────────────────────────────────────────

class TestBilateralDebts:
    def _exp(self, paid_by, total, splits):
        return {
            "paidBy": paid_by,
            "amountInDestinationCurrency": total,
            "splits": [{"userId": u, "amountInDestinationCurrency": a} for u, a in splits],
        }

    def test_two_members_simple(self):
        """Alice paid 100, split equally → Bob owes Alice 50."""
        exp = self._exp("alice", 100, [("alice", 50), ("bob", 50)])
        txs = bilateral_debts([exp], [])
        assert len(txs) == 1
        assert txs[0]["from"] == "bob"
        assert txs[0]["to"] == "alice"
        assert abs(txs[0]["amount"] - 50) < 0.01

    def test_cross_payments_net_to_one(self):
        """Alice paid 100 for Bob; Bob paid 60 for Alice. Net: Bob owes Alice 40."""
        exp1 = self._exp("alice", 100, [("bob", 100)])
        exp2 = self._exp("bob", 60, [("alice", 60)])
        txs = bilateral_debts([exp1, exp2], [])
        assert len(txs) == 1
        assert txs[0]["from"] == "bob"
        assert txs[0]["to"] == "alice"
        assert abs(txs[0]["amount"] - 40) < 0.01

    def test_payer_not_in_splits_bilateral(self):
        """Alice paid 100 for Bob only. Bob owes Alice 100."""
        exp = self._exp("alice", 100, [("bob", 100)])
        txs = bilateral_debts([exp], [])
        assert len(txs) == 1
        assert txs[0]["from"] == "bob"
        assert txs[0]["to"] == "alice"
        assert abs(txs[0]["amount"] - 100) < 0.01

    def test_yogya_breakfast_and_extra_bed(self):
        """
        Aditya paid breakfast (356,399) for Danny+Aditya.
        Danny paid extra bed (400,000) for Aditya only.
        Net: Aditya owes Danny 221,800.50
        (This is the real Yogyakarta scenario the user reported.)
        """
        breakfast = calculate_equal_splits(356_399, ["danny", "aditya"], "aditya")
        exp1 = self._exp("aditya", 356_399,
                         [(s["userId"], s["amountInDestinationCurrency"]) for s in breakfast])
        exp2 = self._exp("danny", 400_000, [("aditya", 400_000)])

        txs = bilateral_debts([exp1, exp2], [])
        assert len(txs) == 1
        assert txs[0]["from"] == "aditya"
        assert txs[0]["to"] == "danny"
        # 400,000 - 178,199.50 = 221,800.50
        assert abs(txs[0]["amount"] - 221_800.50) < 1

    def test_three_members_preserves_bilateral_relationship(self):
        """
        Three members. Jason, Aditya, Danny.
        Breakfast (Aditya paid, Danny+Aditya only) and Extra Bed (Danny paid, Aditya only).
        Plus a shared expense (Jason paid all 3).
        The Aditya-Danny bilateral must appear regardless of Jason's involvement.
        """
        breakfast = calculate_equal_splits(356_399, ["danny", "aditya"], "aditya")
        exp1 = self._exp("aditya", 356_399,
                         [(s["userId"], s["amountInDestinationCurrency"]) for s in breakfast])
        exp2 = self._exp("danny", 400_000, [("aditya", 400_000)])
        # Shared dinner: Jason paid 300,000 equally split all 3
        dinner = calculate_equal_splits(300_000, ["jason", "aditya", "danny"], "jason")
        exp3 = self._exp("jason", 300_000,
                         [(s["userId"], s["amountInDestinationCurrency"]) for s in dinner])

        txs = bilateral_debts([exp1, exp2, exp3], [])

        # Aditya-Danny bilateral must be present
        aditya_danny = next(
            (t for t in txs if {t["from"], t["to"]} == {"aditya", "danny"}), None
        )
        assert aditya_danny is not None, "Aditya-Danny transaction missing"
        assert aditya_danny["from"] == "aditya"
        assert aditya_danny["to"] == "danny"
        assert abs(aditya_danny["amount"] - 221_800.50) < 1

        # Jason-Aditya and Jason-Danny bilaterals must also be present
        jason_aditya = next(
            (t for t in txs if {t["from"], t["to"]} == {"jason", "aditya"}), None
        )
        assert jason_aditya is not None, "Jason-Aditya transaction missing"

        jason_danny = next(
            (t for t in txs if {t["from"], t["to"]} == {"jason", "danny"}), None
        )
        assert jason_danny is not None, "Jason-Danny transaction missing"

    def test_already_settled_pair_disappears(self):
        """After Aditya pays Danny 221,800, their bilateral should be zero."""
        breakfast = calculate_equal_splits(356_399, ["danny", "aditya"], "aditya")
        exp1 = self._exp("aditya", 356_399,
                         [(s["userId"], s["amountInDestinationCurrency"]) for s in breakfast])
        exp2 = self._exp("danny", 400_000, [("aditya", 400_000)])

        settlement = {"fromUserId": "aditya", "toUserId": "danny",
                      "amountInDestinationCurrency": 221_800.50}
        txs = bilateral_debts([exp1, exp2], [settlement])
        assert all({t["from"], t["to"]} != {"aditya", "danny"} for t in txs)

    def test_all_settled_no_transactions(self):
        exp = self._exp("alice", 100, [("alice", 50), ("bob", 50)])
        stl = {"fromUserId": "bob", "toUserId": "alice", "amountInDestinationCurrency": 50}
        txs = bilateral_debts([exp], [stl])
        assert txs == []

    def test_net_conservation(self):
        """Sum of (from debits) must equal sum of (to credits) per member."""
        breakfast = calculate_equal_splits(356_399, ["danny", "aditya"], "aditya")
        exp1 = self._exp("aditya", 356_399,
                         [(s["userId"], s["amountInDestinationCurrency"]) for s in breakfast])
        exp2 = self._exp("danny", 400_000, [("aditya", 400_000)])
        dinner = calculate_equal_splits(300_000, ["jason", "aditya", "danny"], "jason")
        exp3 = self._exp("jason", 300_000,
                         [(s["userId"], s["amountInDestinationCurrency"]) for s in dinner])

        txs = bilateral_debts([exp1, exp2, exp3], [])

        # Net flow per member from bilateral transactions must match calculate_balances
        balances = calculate_balances([exp1, exp2, exp3], [])
        net_from_txs = {m: 0.0 for m in balances}
        for t in txs:
            net_from_txs[t["from"]] = net_from_txs.get(t["from"], 0) - t["amount"]
            net_from_txs[t["to"]]   = net_from_txs.get(t["to"], 0)   + t["amount"]

        for member, bal in balances.items():
            assert abs(net_from_txs.get(member, 0) - bal) < 0.05, \
                f"{member}: balance={bal:.2f}, bilateral net={net_from_txs.get(member,0):.2f}"


# ── Trip Alpha integration (real anonymised trip, fixture-driven) ─────────────

import json
import os

_FIXTURE_PATH = os.path.join(os.path.dirname(__file__), "fixtures", "trip_alpha.json")


def _load_trip_alpha():
    with open(_FIXTURE_PATH) as f:
        return json.load(f)


def _build_trip_alpha_expenses(data: dict) -> list[dict]:
    """Build expense dicts from the JSON fixture using the real split calculator."""
    expenses = []
    for row in data["expenses"]:
        raw_splits = calculate_equal_splits(row["amount"], row["split"], row["payer"])
        expenses.append({
            "paidBy": row["payer"],
            "amountInDestinationCurrency": row["amount"],
            "splits": [
                {"userId": s["userId"], "amountInDestinationCurrency": s["amountInDestinationCurrency"]}
                for s in raw_splits
            ],
        })
    return expenses


class TestTripAlphaIntegration:
    """
    Settlement integration test driven by tests/fixtures/trip_alpha.json.
    Real trip data, member names anonymised (alpha/beta/gamma).
    All expected values were independently verified from the live app export.
    """

    @staticmethod
    def _data():
        return _load_trip_alpha()

    def test_fixture_grand_total(self):
        data = self._data()
        total = sum(e["amount"] for e in data["expenses"])
        assert abs(total - data["expected"]["grand_total"]) < 0.01, \
            f"Grand total mismatch: {total:.2f} vs {data['expected']['grand_total']}"

    def test_expense_count(self):
        data = self._data()
        assert len(data["expenses"]) == 40

    def test_net_zero_invariant(self):
        data = self._data()
        expenses = _build_trip_alpha_expenses(data)
        b = calculate_balances(expenses, [])
        assert abs(sum(b.values())) < 0.01, f"Non-zero sum: {sum(b.values())}"

    def test_net_balances_match_fixture(self):
        data = self._data()
        expenses = _build_trip_alpha_expenses(data)
        b = calculate_balances(expenses, [])
        for member, expected in data["expected"]["net_balances"].items():
            assert abs(b.get(member, 0) - expected) < 0.10, \
                f"{member}: got {b.get(member,0):.2f}, expected {expected:.2f}"

    def test_alpha_is_debtor(self):
        data = self._data()
        expenses = _build_trip_alpha_expenses(data)
        b = calculate_balances(expenses, [])
        assert b["alpha"] < 0, "alpha should owe money"

    def test_beta_gamma_are_creditors(self):
        data = self._data()
        expenses = _build_trip_alpha_expenses(data)
        b = calculate_balances(expenses, [])
        assert b["beta"]  > 0, "beta should be owed money"
        assert b["gamma"] > 0, "gamma should be owed money"

    def test_bilateral_transaction_count(self):
        data = self._data()
        expenses = _build_trip_alpha_expenses(data)
        txs = bilateral_debts(expenses, [])
        assert len(txs) == 3, f"Expected 3 transactions, got {len(txs)}"

    def test_bilateral_amounts_match_fixture(self):
        data = self._data()
        expenses = _build_trip_alpha_expenses(data)
        txs = bilateral_debts(expenses, [])
        tx_map = {(t["from"], t["to"]): t["amount"] for t in txs}

        for expected_tx in data["expected"]["bilateral_transactions"]:
            key = (expected_tx["from"], expected_tx["to"])
            actual = tx_map.get(key)
            assert actual is not None, \
                f"Missing transaction {key[0]} → {key[1]}"
            assert abs(actual - expected_tx["amount"]) < 0.10, \
                f"{key[0]} → {key[1]}: got {actual:.2f}, expected {expected_tx['amount']:.2f}"

    def test_bilateral_clears_all_balances(self):
        """The definitive correctness test: after applying all bilateral transactions,
        every member balance reaches exactly zero."""
        data = self._data()
        expenses = _build_trip_alpha_expenses(data)
        b = calculate_balances(expenses, [])
        txs = bilateral_debts(expenses, [])

        # Convert bilateral transactions to settlement format
        settlements = [
            {"fromUserId": t["from"], "toUserId": t["to"],
             "amountInDestinationCurrency": t["amount"]}
            for t in txs
        ]
        final = calculate_balances(expenses, settlements)
        for member, balance in final.items():
            assert abs(balance) < 0.10, \
                f"{member} not cleared: residual IDR {balance:+.2f}"

    def test_gamma_beta_bilateral_exists(self):
        """gamma (Aditya) owes beta (Danny) net from Extra Bed vs Breakfast."""
        data = self._data()
        expenses = _build_trip_alpha_expenses(data)
        txs = bilateral_debts(expenses, [])
        gamma_beta = next((t for t in txs
                           if t["from"] == "gamma" and t["to"] == "beta"), None)
        assert gamma_beta is not None, "gamma→beta transaction missing"
        # Extra Bed: beta paid 400k for gamma → gamma owes 400k
        # Breakfast: gamma paid 356399 split beta|gamma → beta owes 178199.50
        # Net: gamma owes beta 400000 - 178199.50 = 221800.50
        # But there is also gamma paid Soto (198000/3=66000) where beta owes gamma
        # Net net: 400000 - 178199.50 - 66000 + 66000 = 221800.50... let fixture be truth
        expected = data["expected"]["bilateral_transactions"]
        exp_gb = next(e for e in expected if e["from"] == "gamma" and e["to"] == "beta")
        assert abs(gamma_beta["amount"] - exp_gb["amount"]) < 0.10
