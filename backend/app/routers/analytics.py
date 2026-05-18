from fastapi import APIRouter, Depends, HTTPException
from app.dependencies import get_current_user
from app.services.firestore import get_db, doc_to_dict
from app.utils.validators import require_trip_member
from app.utils.categories import get_emoji

router = APIRouter()


def _get_trip(db, trip_id: str) -> dict:
    doc = db.collection("trips").document(trip_id).get()
    data = doc_to_dict(doc)
    if not data:
        raise HTTPException(status_code=404, detail="Trip not found")
    return data


@router.get("/{trip_id}/analytics")
async def get_analytics(trip_id: str, current_user: dict = Depends(get_current_user)):
    db = get_db()
    trip = _get_trip(db, trip_id)
    require_trip_member(trip, current_user["uid"])

    expenses = [doc.to_dict() for doc in
                db.collection("trips").document(trip_id).collection("expenses").stream()]

    dest_currency = trip["destinationCurrency"]
    uid = current_user["uid"]
    members = trip.get("members", [])
    member_map = {(m.get("userId") or m.get("ghostId")): m for m in members}

    # Group aggregations
    total = sum(e.get("amountInDestinationCurrency", 0) for e in expenses)
    real_members = [m for m in members if m.get("role") != "ghost"]
    n = len(real_members) or 1

    by_category: dict[str, float] = {}
    by_day: dict[str, dict] = {}
    by_member_paid: dict[str, float] = {}

    for exp in expenses:
        amt = exp.get("amountInDestinationCurrency", 0)
        cat = exp.get("category", "Other")
        by_category[cat] = by_category.get(cat, 0) + amt

        created = exp.get("createdAt")
        date_str = created.strftime("%Y-%m-%d") if hasattr(created, "strftime") else str(created)[:10]
        if date_str not in by_day:
            by_day[date_str] = {"amount": 0, "expenseCount": 0}
        by_day[date_str]["amount"] += amt
        by_day[date_str]["expenseCount"] += 1

        payer = exp.get("paidBy")
        by_member_paid[payer] = by_member_paid.get(payer, 0) + amt

    category_list = [
        {"category": cat, "emoji": get_emoji(cat), "amount": round(amt, 2),
         "percentage": round(amt / total * 100, 2) if total else 0}
        for cat, amt in sorted(by_category.items(), key=lambda x: -x[1])
    ]
    day_list = [
        {"date": d, "amount": round(v["amount"], 2), "expenseCount": v["expenseCount"]}
        for d, v in sorted(by_day.items())
    ]
    member_list = [
        {"userId": m_uid, "displayName": member_map.get(m_uid, {}).get("displayName", m_uid),
         "isGhost": str(m_uid).startswith("ghost_"),
         "totalPaid": round(amt, 2), "percentage": round(amt / total * 100, 2) if total else 0}
        for m_uid, amt in sorted(by_member_paid.items(), key=lambda x: -x[1])
    ]

    # Individual aggregations
    my_total = 0.0
    my_by_category: dict[str, float] = {}
    timeline = []

    for exp in expenses:
        for sp in exp.get("splits", []):
            if sp.get("userId") == uid:
                share = sp.get("amountInDestinationCurrency", 0)
                my_total += share
                cat = exp.get("category", "Other")
                my_by_category[cat] = my_by_category.get(cat, 0) + share

                created = exp.get("createdAt")
                date_str = created.strftime("%Y-%m-%d") if hasattr(created, "strftime") else str(created)[:10]
                timeline.append({
                    "date": date_str,
                    "expenseId": exp.get("expenseId", ""),
                    "description": exp.get("description", ""),
                    "myShare": round(share, 2),
                    "category": cat,
                    "emoji": get_emoji(cat),
                })

    timeline.sort(key=lambda x: x["date"])

    my_category_list = [
        {"category": cat, "emoji": get_emoji(cat), "amount": round(amt, 2),
         "percentage": round(amt / my_total * 100, 2) if my_total else 0}
        for cat, amt in sorted(my_by_category.items(), key=lambda x: -x[1])
    ]

    group_avg = round(total / n, 2) if n else 0
    my_user_info = member_map.get(uid, {})

    return {
        "tripId": trip_id,
        "destinationCurrency": dest_currency,
        "dateRange": {"from": trip.get("startDate"), "to": trip.get("endDate")},
        "group": {
            "totalSpend": round(total, 2),
            "totalSpendPerMember": round(total / n, 2),
            "byCategory": category_list,
            "byDay": day_list,
            "byMember": member_list,
        },
        "individual": {
            "userId": uid,
            "displayName": my_user_info.get("displayName", uid),
            "totalShare": round(my_total, 2),
            "homeCurrency": my_user_info.get("homeCurrency", dest_currency),
            "byCategory": my_category_list,
            "vsGroupAverage": {
                "myShare": round(my_total, 2),
                "groupAverage": group_avg,
                "difference": round(my_total - group_avg, 2),
                "percentageDifference": round((my_total - group_avg) / group_avg * 100, 2) if group_avg else 0,
            },
            "timeline": timeline,
        },
    }
