
def calculate_balances(expenses: list[dict], settlements: list[dict]) -> dict[str, float]:
    """Return net balance per member: positive = owed money, negative = owes money."""
    balances: dict[str, float] = {}

    for exp in expenses:
        paid_by = exp.get("paidBy")
        total = exp.get("amountInDestinationCurrency", 0)
        splits = exp.get("splits", [])

        # Payer gets credit
        balances[paid_by] = balances.get(paid_by, 0) + total

        # Each participant owes their share
        for split in splits:
            uid = split.get("userId")
            amount = split.get("amountInDestinationCurrency", 0)
            balances[uid] = balances.get(uid, 0) - amount

    # Subtract already-settled amounts
    for s in settlements:
        from_uid = s.get("fromUserId")
        to_uid = s.get("toUserId")
        amount = s.get("amountInDestinationCurrency", 0)
        balances[from_uid] = balances.get(from_uid, 0) + amount
        balances[to_uid] = balances.get(to_uid, 0) - amount

    return balances


def bilateral_debts(expenses: list[dict], settlements: list[dict]) -> list[dict]:
    """
    Compute net pairwise flows between every pair of members.
    For each pair (A, B) the gross amounts A owes B and B owes A are netted,
    producing one transaction per pair that has an outstanding balance.
    This preserves real-world relationships (e.g. Aditya↔Danny from a shared
    breakfast and extra bed) instead of routing everything through a single debtor.
    """
    from collections import defaultdict

    # pair_flows[(debtor, creditor)] = gross amount debtor owes creditor (from expenses)
    pair_flows: dict[tuple, float] = defaultdict(float)

    for exp in expenses:
        payer = exp.get("paidBy")
        for split in exp.get("splits", []):
            uid = split.get("userId")
            if uid == payer:
                continue  # payer's own share creates no inter-person debt
            amount = split.get("amountInDestinationCurrency", 0)
            pair_flows[(uid, payer)] += amount

    # Recorded settlements reduce the gross debt for that pair
    for s in settlements:
        from_uid = s.get("fromUserId")
        to_uid = s.get("toUserId")
        amount = s.get("amountInDestinationCurrency", 0)
        pair_flows[(from_uid, to_uid)] -= amount

    # For every unique {A, B} pair, compute the net direction and amount
    seen: set[frozenset] = set()
    transactions = []

    for (a, b) in list(pair_flows.keys()):
        pair = frozenset([a, b])
        if pair in seen:
            continue
        seen.add(pair)

        a_owes_b = pair_flows.get((a, b), 0)
        b_owes_a = pair_flows.get((b, a), 0)
        net = a_owes_b - b_owes_a

        if net > 0.005:
            transactions.append({"from": a, "to": b, "amount": round(net, 2)})
        elif net < -0.005:
            transactions.append({"from": b, "to": a, "amount": round(-net, 2)})

    transactions.sort(key=lambda t: t["amount"], reverse=True)
    return transactions


def simplify_debts(balances: dict[str, float]) -> list[dict]:
    """Greedy debt simplification — returns minimal list of transactions."""
    creditors = sorted(
        [(uid, bal) for uid, bal in balances.items() if bal > 0.005],
        key=lambda x: x[1], reverse=True
    )
    debtors = sorted(
        [(uid, -bal) for uid, bal in balances.items() if bal < -0.005],
        key=lambda x: x[1], reverse=True
    )

    transactions = []
    ci, di = 0, 0
    creditors = list(creditors)
    debtors = list(debtors)

    while ci < len(creditors) and di < len(debtors):
        c_uid, c_amt = creditors[ci]
        d_uid, d_amt = debtors[di]
        transfer = min(c_amt, d_amt)
        transactions.append({
            "from": d_uid,
            "to": c_uid,
            "amount": round(transfer, 2),
        })
        creditors[ci] = (c_uid, c_amt - transfer)
        debtors[di] = (d_uid, d_amt - transfer)
        if creditors[ci][1] < 0.005:
            ci += 1
        if debtors[di][1] < 0.005:
            di += 1

    return transactions
