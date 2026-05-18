import uuid
from datetime import datetime, timezone
from fastapi import APIRouter, Depends, HTTPException, Query
from app.dependencies import get_current_user
from app.models.expense import ExpenseCreate, ExpenseUpdate
from app.services.firestore import get_db, doc_to_dict
from app.services import exchange_rates as fx_service
from app.services.currency import calculate_equal_splits, calculate_percentage_splits, calculate_exact_splits
from app.utils.validators import require_trip_member

router = APIRouter()


def _get_trip(db, trip_id: str) -> dict:
    doc = db.collection("trips").document(trip_id).get()
    data = doc_to_dict(doc)
    if not data:
        raise HTTPException(status_code=404, detail="Trip not found")
    return data


def _member_home_currencies(members: list) -> dict[str, str]:
    result = {}
    for m in members:
        uid = m.get("userId") or m.get("ghostId")
        if uid:
            result[uid] = m.get("homeCurrency", "USD")
    return result


async def _build_expense(body: ExpenseCreate, trip: dict, uid: str) -> dict:
    dest_currency = trip["destinationCurrency"]
    members = trip.get("members", [])
    home_currencies = _member_home_currencies(members)

    # All unique currencies we need rates for
    all_currencies = list(set(home_currencies.values()) | {body.originalCurrency, dest_currency})
    rates = await fx_service.fetch_rates(dest_currency, [c for c in all_currencies if c != dest_currency])
    rates[dest_currency] = 1.0

    # Convert to destination currency
    if body.originalCurrency == dest_currency:
        total_dest = body.originalAmount
        rate_used = 1.0
    else:
        orig_rate = rates.get(body.originalCurrency, 1.0)
        # orig_rate is dest per 1 dest → need dest per orig
        # frankfurter gives: base=dest, symbols=orig → rates[orig] = how many orig per 1 dest
        # So: 1 orig = 1 / rates[orig] dest
        rate_used = 1.0 / orig_rate if orig_rate else 1.0
        total_dest = round(body.originalAmount * rate_used, 2)

    # Splits
    split_ids = [s.userId for s in body.splits] if body.splits else [m.get("userId") or m.get("ghostId") for m in members]
    split_mode = body.splitMode

    if split_mode == "equal":
        raw_splits = calculate_equal_splits(total_dest, split_ids, body.paidBy)
    elif split_mode == "percentage":
        pct_inputs = [{"userId": s.userId, "percentage": s.percentage or 0} for s in body.splits]
        raw_splits = calculate_percentage_splits(total_dest, pct_inputs, body.paidBy)
    elif split_mode == "exact":
        exact_inputs = [{"userId": s.userId, "exactAmount": s.exactAmount or 0, "exactCurrency": s.exactCurrency or dest_currency} for s in body.splits]
        raw_splits = calculate_exact_splits(total_dest, exact_inputs, rates, dest_currency, body.paidBy)
    else:
        raw_splits = calculate_equal_splits(total_dest, split_ids, body.paidBy)

    # Derive percentages and home currency amounts
    final_splits = []
    for sp in raw_splits:
        member_id = sp["userId"]
        home_currency = home_currencies.get(member_id, dest_currency)
        home_rate = rates.get(home_currency, 1.0) if home_currency != dest_currency else 1.0
        # home_rate = how many home_currency per 1 dest_currency
        amt_dest = sp["amountInDestinationCurrency"]
        amt_home = round(amt_dest * home_rate, 2)
        pct = sp.get("percentage", round(amt_dest / total_dest * 100, 4) if total_dest else 0)
        final_splits.append({
            "userId": member_id,
            "percentage": pct,
            "amountInDestinationCurrency": amt_dest,
            "amountInHomeCurrency": amt_home,
            "homeCurrency": home_currency,
        })

    now = datetime.now(timezone.utc)
    return {
        "description": body.description,
        "category": body.category,
        "originalAmount": body.originalAmount,
        "originalCurrency": body.originalCurrency,
        "destinationCurrency": dest_currency,
        "amountInDestinationCurrency": total_dest,
        "exchangeRateUsed": rate_used,
        "exchangeRateTimestamp": now.isoformat(),
        "exchangeRates": rates,
        "splitMode": split_mode,
        "paidBy": body.paidBy,
        "splits": final_splits,
        "receiptUrl": None,
        "createdBy": uid,
        "createdAt": now,
        "updatedAt": now,
    }


@router.post("/{trip_id}/expenses")
async def add_expense(trip_id: str, body: ExpenseCreate, current_user: dict = Depends(get_current_user)):
    db = get_db()
    trip = _get_trip(db, trip_id)
    require_trip_member(trip, current_user["uid"])

    expense_id = f"exp_{uuid.uuid4().hex[:12]}"
    expense = await _build_expense(body, trip, current_user["uid"])
    expense["expenseId"] = expense_id

    db.collection("trips").document(trip_id).collection("expenses").document(expense_id).set(expense)
    db.collection("trips").document(trip_id).update({"updatedAt": datetime.now(timezone.utc)})
    return expense


@router.get("/{trip_id}/expenses")
async def list_expenses(
    trip_id: str,
    category: str = Query(None),
    paid_by: str = Query(None, alias="paidBy"),
    limit: int = Query(50),
    offset: int = Query(0),
    current_user: dict = Depends(get_current_user),
):
    db = get_db()
    trip = _get_trip(db, trip_id)
    require_trip_member(trip, current_user["uid"])

    query = db.collection("trips").document(trip_id).collection("expenses")
    if category:
        query = query.where("category", "==", category)
    if paid_by:
        query = query.where("paidBy", "==", paid_by)

    expenses = [doc.to_dict() for doc in query.order_by("createdAt", direction="DESCENDING").stream()]

    # Build settlement lookup for member status
    settlements_raw = db.collection("trips").document(trip_id).collection("settlements").stream()
    settlements = [s.to_dict() for s in settlements_raw]

    result = []
    for exp in expenses[offset: offset + limit]:
        payer = exp.get("paidBy")
        member_statuses = []
        for sp in exp.get("splits", []):
            uid = sp["userId"]
            is_ghost = uid.startswith("ghost_")
            is_payer = uid == payer
            settled = any(
                s.get("fromUserId") == uid or s.get("toUserId") == uid
                for s in settlements
            )
            if is_payer:
                st = "paid"
            elif settled:
                st = "settled"
            else:
                st = "outstanding"

            member_info = next((m for m in trip.get("members", []) if (m.get("userId") or m.get("ghostId")) == uid), {})
            member_statuses.append({
                "userId": uid,
                "displayName": member_info.get("displayName", uid),
                "isGhost": is_ghost,
                "isPayer": is_payer,
                "amountInDestinationCurrency": sp["amountInDestinationCurrency"],
                "amountInHomeCurrency": sp.get("amountInHomeCurrency", 0),
                "homeCurrency": sp.get("homeCurrency", "USD"),
                "status": st,
            })
        exp["memberStatuses"] = member_statuses
        result.append(exp)

    return result


@router.get("/{trip_id}/expenses/{expense_id}")
async def get_expense(trip_id: str, expense_id: str, current_user: dict = Depends(get_current_user)):
    db = get_db()
    trip = _get_trip(db, trip_id)
    require_trip_member(trip, current_user["uid"])
    doc = db.collection("trips").document(trip_id).collection("expenses").document(expense_id).get()
    data = doc_to_dict(doc)
    if not data:
        raise HTTPException(status_code=404, detail="Expense not found")
    return data


@router.patch("/{trip_id}/expenses/{expense_id}")
async def update_expense(trip_id: str, expense_id: str, body: ExpenseUpdate,
                         current_user: dict = Depends(get_current_user)):
    db = get_db()
    trip = _get_trip(db, trip_id)
    require_trip_member(trip, current_user["uid"])

    doc = db.collection("trips").document(trip_id).collection("expenses").document(expense_id).get()
    existing = doc_to_dict(doc)
    if not existing:
        raise HTTPException(status_code=404, detail="Expense not found")

    if existing.get("createdBy") != current_user["uid"] and trip.get("createdBy") != current_user["uid"]:
        raise HTTPException(status_code=403, detail="Not authorized")

    # Merge fields
    create_body = ExpenseCreate(
        description=body.description or existing["description"],
        category=body.category or existing["category"],
        originalAmount=body.originalAmount or existing["originalAmount"],
        originalCurrency=body.originalCurrency or existing["originalCurrency"],
        paidBy=body.paidBy or existing["paidBy"],
        splitMode=body.splitMode or existing.get("splitMode", "equal"),
        splits=body.splits or [],
    )

    updated = await _build_expense(create_body, trip, current_user["uid"])
    updated["expenseId"] = expense_id
    updated["createdBy"] = existing["createdBy"]
    updated["createdAt"] = existing["createdAt"]

    db.collection("trips").document(trip_id).collection("expenses").document(expense_id).set(updated)
    return updated


@router.delete("/{trip_id}/expenses/{expense_id}")
async def delete_expense(trip_id: str, expense_id: str, current_user: dict = Depends(get_current_user)):
    db = get_db()
    trip = _get_trip(db, trip_id)
    require_trip_member(trip, current_user["uid"])

    doc = db.collection("trips").document(trip_id).collection("expenses").document(expense_id).get()
    existing = doc_to_dict(doc)
    if not existing:
        raise HTTPException(status_code=404, detail="Expense not found")

    is_owner = trip.get("createdBy") == current_user["uid"]
    is_creator = existing.get("createdBy") == current_user["uid"]

    if not is_owner and not is_creator:
        raise HTTPException(status_code=403, detail="Not authorized")

    if is_creator and not is_owner:
        created_at = existing.get("createdAt")
        if created_at:
            now = datetime.now(timezone.utc)
            if isinstance(created_at, datetime):
                age = now - created_at.replace(tzinfo=timezone.utc) if created_at.tzinfo is None else now - created_at
                if age.total_seconds() > 86400:
                    raise HTTPException(status_code=403, detail="Can only delete within 24 hours")

    db.collection("trips").document(trip_id).collection("expenses").document(expense_id).delete()
    return {"ok": True}
