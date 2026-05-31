import math


def floor2(value: float) -> float:
    return math.floor(value * 100) / 100


def calculate_equal_splits(total: float, member_ids: list[str], payer_id: str) -> list[dict]:
    n = len(member_ids)
    base = floor2(total / n)
    remainder = round(total - base * n, 2)
    # Remainder goes to payer if they're in the split; otherwise to the first member,
    # so that splits always sum exactly to total regardless of who paid.
    payer_in_splits = payer_id in member_ids
    remainder_holder = payer_id if payer_in_splits else (member_ids[0] if member_ids else payer_id)
    splits = []
    for uid in member_ids:
        amt = base + remainder if uid == remainder_holder else base
        splits.append({"userId": uid, "amountInDestinationCurrency": round(amt, 2)})
    return splits


def calculate_percentage_splits(total: float, inputs: list[dict], payer_id: str) -> list[dict]:
    total_pct = sum(s["percentage"] for s in inputs)
    if abs(total_pct - 100) > 0.01:
        raise ValueError(f"Percentages must sum to 100, got {total_pct}")

    splits = []
    running = 0.0
    for s in inputs:
        amt = floor2((s["percentage"] / 100) * total)
        running += amt
        splits.append({"userId": s["userId"], "percentage": s["percentage"], "amountInDestinationCurrency": amt})

    remainder = round(total - running, 2)
    assigned = False
    for sp in splits:
        if sp["userId"] == payer_id:
            sp["amountInDestinationCurrency"] = round(sp["amountInDestinationCurrency"] + remainder, 2)
            assigned = True
            break
    if not assigned and splits:
        splits[0]["amountInDestinationCurrency"] = round(splits[0]["amountInDestinationCurrency"] + remainder, 2)

    return splits


def calculate_exact_splits(
    total_dest: float,
    inputs: list[dict],
    rates: dict[str, float],
    dest_currency: str,
    payer_id: str,
) -> list[dict]:
    splits = []
    running = 0.0
    for s in inputs:
        exact_currency = s.get("exactCurrency", dest_currency)
        exact_amount = s.get("exactAmount", 0.0)
        # Convert to destination currency
        if exact_currency == dest_currency:
            amt_dest = exact_amount
        else:
            # rate: dest per 1 unit of exact_currency
            rate_dest_per_exact = rates.get(dest_currency, 1.0) / rates.get(exact_currency, 1.0)
            amt_dest = floor2(exact_amount * rate_dest_per_exact)
        running += amt_dest
        splits.append({
            "userId": s["userId"],
            "amountInDestinationCurrency": round(amt_dest, 2),
        })

    remainder = round(total_dest - running, 2)
    if abs(remainder) > 0.02:
        raise ValueError(f"Exact split amounts don't match total (diff={remainder})")

    assigned = False
    for sp in splits:
        if sp["userId"] == payer_id:
            sp["amountInDestinationCurrency"] = round(sp["amountInDestinationCurrency"] + remainder, 2)
            assigned = True
            break
    if not assigned and splits:
        splits[0]["amountInDestinationCurrency"] = round(splits[0]["amountInDestinationCurrency"] + remainder, 2)

    return splits
