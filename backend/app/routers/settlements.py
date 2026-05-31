import asyncio
import uuid
from datetime import datetime, timezone
from typing import Optional
from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel
from app.dependencies import get_current_user
from app.models.settlement import SettlementCreate
from app.services.firestore import get_db, doc_to_dict, stream_docs
from app.services.settlement import calculate_balances, simplify_debts, bilateral_debts
from app.services import exchange_rates as fx_service
from app.utils.validators import require_trip_member

router = APIRouter()


def _get_trip(db, trip_id: str) -> dict:
    doc = db.collection("trips").document(trip_id).get()
    data = doc_to_dict(doc)
    if not data:
        raise HTTPException(status_code=404, detail="Trip not found")
    return data


@router.get("/{trip_id}/settlement")
async def get_settlement(trip_id: str, current_user: dict = Depends(get_current_user)):
    db = get_db()
    trip = _get_trip(db, trip_id)
    require_trip_member(trip, current_user["uid"])

    expenses, settlements_raw = await asyncio.gather(
        stream_docs(db.collection("trips").document(trip_id).collection("expenses")),
        stream_docs(db.collection("trips").document(trip_id).collection("settlements")),
    )

    balances = calculate_balances(expenses, settlements_raw)
    transactions = bilateral_debts(expenses, settlements_raw)

    dest_currency = trip["destinationCurrency"]
    members = trip.get("members", [])
    member_map = {}
    for m in members:
        uid = m.get("userId") or m.get("ghostId")
        if uid:
            member_map[uid] = m

    # Fetch live rates for home currency conversions
    home_currencies = list({m.get("homeCurrency", "USD") for m in members if m.get("homeCurrency")})
    rates = await fx_service.fetch_rates(dest_currency, [c for c in home_currencies if c != dest_currency])
    rates[dest_currency] = 1.0

    now = datetime.now(timezone.utc)

    tx_result = []
    for tx in transactions:
        from_m = member_map.get(tx["from"], {})
        to_m = member_map.get(tx["to"], {})
        from_currency = from_m.get("homeCurrency", dest_currency)
        to_currency = to_m.get("homeCurrency", dest_currency)
        amt = tx["amount"]
        tx_result.append({
            "from": {"userId": tx["from"], "displayName": from_m.get("displayName", tx["from"]),
                     "isGhost": str(tx["from"]).startswith("ghost_")},
            "to": {"userId": tx["to"], "displayName": to_m.get("displayName", tx["to"]),
                   "isGhost": str(tx["to"]).startswith("ghost_")},
            "amountInDestinationCurrency": amt,
            "destinationCurrency": dest_currency,
            "amountInFromHomeCurrency": round(amt * rates.get(from_currency, 1.0), 2),
            "fromHomeCurrency": from_currency,
            "amountInToHomeCurrency": round(amt * rates.get(to_currency, 1.0), 2),
            "toHomeCurrency": to_currency,
        })

    total = sum(e.get("amountInDestinationCurrency", 0) for e in expenses)

    per_member = []
    for uid, bal in balances.items():
        m = member_map.get(uid, {})
        home_currency = m.get("homeCurrency", dest_currency)
        per_member.append({
            "userId": uid,
            "displayName": m.get("displayName", uid),
            "isGhost": str(uid).startswith("ghost_"),
            "totalPaid": round(sum(e.get("amountInDestinationCurrency", 0) for e in expenses if e.get("paidBy") == uid), 2),
            "totalOwed": round(sum(sp.get("amountInDestinationCurrency", 0) for e in expenses for sp in e.get("splits", []) if sp.get("userId") == uid), 2),
            "balance": round(bal, 2),
            "balanceInHomeCurrency": round(bal * rates.get(home_currency, 1.0), 2),
            "homeCurrency": home_currency,
        })

    return {
        "calculatedAt": now.isoformat(),
        "ratesNote": f"Amounts in {dest_currency} reflect rates locked at time of each expense. Home currency equivalents (≈) use today's live rates.",
        "stale": fx_service.is_stale(dest_currency, home_currencies),
        "transactions": tx_result,
        "summary": {
            "totalExpenses": round(total, 2),
            "destinationCurrency": dest_currency,
            "perMember": per_member,
        },
    }


@router.post("/{trip_id}/settlements")
async def record_settlement(trip_id: str, body: SettlementCreate,
                            current_user: dict = Depends(get_current_user)):
    db = get_db()
    trip = _get_trip(db, trip_id)
    require_trip_member(trip, current_user["uid"])

    settlement_id = f"stl_{uuid.uuid4().hex[:12]}"
    now = datetime.now(timezone.utc)

    settlement = {
        "settlementId": settlement_id,
        "fromUserId": body.fromUserId,
        "toUserId": body.toUserId,
        "amountInDestinationCurrency": body.amountInDestinationCurrency,
        "destinationCurrency": trip["destinationCurrency"],
        "note": body.note,
        "settledAt": now,
        "createdBy": current_user["uid"],
    }

    db.collection("trips").document(trip_id).collection("settlements").document(settlement_id).set(settlement)
    return settlement


class SettlementUpdate(BaseModel):
    note: Optional[str] = None


@router.get("/{trip_id}/settlements")
async def list_settlements(trip_id: str, current_user: dict = Depends(get_current_user)):
    db = get_db()
    trip = _get_trip(db, trip_id)
    require_trip_member(trip, current_user["uid"])

    members = trip.get("members", [])
    member_map = {}
    for m in members:
        uid = m.get("userId") or m.get("ghostId")
        if uid:
            member_map[uid] = m

    docs = db.collection("trips").document(trip_id).collection("settlements").stream()
    result = []
    for doc in docs:
        s = doc.to_dict()
        from_m = member_map.get(s.get("fromUserId"), {})
        to_m = member_map.get(s.get("toUserId"), {})
        s["fromDisplayName"] = from_m.get("displayName", s.get("fromUserId", ""))
        s["toDisplayName"] = to_m.get("displayName", s.get("toUserId", ""))
        result.append(s)

    result.sort(key=lambda x: x.get("settledAt", ""), reverse=True)
    return result


@router.patch("/{trip_id}/settlements/{settlement_id}")
async def update_settlement(trip_id: str, settlement_id: str, body: SettlementUpdate,
                            current_user: dict = Depends(get_current_user)):
    db = get_db()
    trip = _get_trip(db, trip_id)
    require_trip_member(trip, current_user["uid"])

    ref = db.collection("trips").document(trip_id).collection("settlements").document(settlement_id)
    doc = ref.get()
    if not doc.exists:
        raise HTTPException(status_code=404, detail="Settlement not found")

    ref.update({"note": body.note})
    return {"ok": True}


@router.delete("/{trip_id}/settlements/{settlement_id}")
async def delete_settlement(trip_id: str, settlement_id: str,
                            current_user: dict = Depends(get_current_user)):
    db = get_db()
    trip = _get_trip(db, trip_id)
    require_trip_member(trip, current_user["uid"])

    ref = db.collection("trips").document(trip_id).collection("settlements").document(settlement_id)
    if not ref.get().exists:
        raise HTTPException(status_code=404, detail="Settlement not found")

    ref.delete()
    return {"ok": True}


@router.get("/{trip_id}/balance")
async def get_balance(trip_id: str, current_user: dict = Depends(get_current_user)):
    db = get_db()
    trip = _get_trip(db, trip_id)
    require_trip_member(trip, current_user["uid"])

    expenses, settlements_raw = await asyncio.gather(
        stream_docs(db.collection("trips").document(trip_id).collection("expenses")),
        stream_docs(db.collection("trips").document(trip_id).collection("settlements")),
    )

    balances = calculate_balances(expenses, settlements_raw)
    dest_currency = trip["destinationCurrency"]
    members = trip.get("members", [])

    home_currencies = list({m.get("homeCurrency", "USD") for m in members})
    rates = await fx_service.fetch_rates(dest_currency, [c for c in home_currencies if c != dest_currency])
    rates[dest_currency] = 1.0

    member_map = {}
    for m in members:
        uid = m.get("userId") or m.get("ghostId")
        if uid:
            member_map[uid] = m

    uid = current_user["uid"]
    my_balance = balances.get(uid, 0)
    my_info = member_map.get(uid, {})
    home_currency = my_info.get("homeCurrency", dest_currency)

    owed_to_me_by = []
    i_owe_to = []

    for other_uid, other_bal in balances.items():
        if other_uid == uid:
            continue
        other_m = member_map.get(other_uid, {})
        # net flow between uid and other_uid
        net = 0.0
        for exp in expenses:
            payer = exp.get("paidBy")
            for sp in exp.get("splits", []):
                if sp.get("userId") == other_uid and payer == uid:
                    net += sp.get("amountInDestinationCurrency", 0)
                elif sp.get("userId") == uid and payer == other_uid:
                    net -= sp.get("amountInDestinationCurrency", 0)

        for s in settlements_raw:
            if s.get("fromUserId") == other_uid and s.get("toUserId") == uid:
                net -= s.get("amountInDestinationCurrency", 0)
            elif s.get("fromUserId") == uid and s.get("toUserId") == other_uid:
                net += s.get("amountInDestinationCurrency", 0)

        if abs(net) < 0.01:
            continue

        their_currency = other_m.get("homeCurrency", dest_currency)
        entry = {
            "userId": other_uid,
            "displayName": other_m.get("displayName", other_uid),
            "isGhost": str(other_uid).startswith("ghost_"),
            "amount": round(abs(net), 2),
            "amountInTheirCurrency": round(abs(net) * rates.get(their_currency, 1.0), 2),
            "theirCurrency": their_currency,
            "status": "outstanding",
        }

        if net > 0:
            owed_to_me_by.append(entry)
        else:
            i_owe_to.append(entry)

    total_owed_to_me = sum(e["amount"] for e in owed_to_me_by)
    total_i_owe = sum(e["amount"] for e in i_owe_to)

    now = datetime.now(timezone.utc)
    all_members = []
    for m_uid, bal in balances.items():
        m = member_map.get(m_uid, {})
        m_currency = m.get("homeCurrency", dest_currency)
        all_members.append({
            "userId": m_uid,
            "displayName": m.get("displayName", m_uid),
            "isGhost": str(m_uid).startswith("ghost_"),
            "netBalance": round(bal, 2),
            "netBalanceInHomeCurrency": round(bal * rates.get(m_currency, 1.0), 2),
            "homeCurrency": m_currency,
            "status": "owed" if bal > 0.005 else ("owes" if bal < -0.005 else "settled"),
        })

    return {
        "tripId": trip_id,
        "destinationCurrency": dest_currency,
        "calculatedAt": now.isoformat(),
        "myBalance": {
            "userId": uid,
            "displayName": my_info.get("displayName", uid),
            "totalOwedToMe": round(total_owed_to_me, 2),
            "totalOwedToMeInHomeCurrency": round(total_owed_to_me * rates.get(home_currency, 1.0), 2),
            "homeCurrency": home_currency,
            "totalIOwe": round(total_i_owe, 2),
            "totalIOweInHomeCurrency": round(total_i_owe * rates.get(home_currency, 1.0), 2),
            "netBalance": round(my_balance, 2),
            "netBalanceInHomeCurrency": round(my_balance * rates.get(home_currency, 1.0), 2),
            "owedToMeBy": owed_to_me_by,
            "iOweTo": i_owe_to,
        },
        "allMembers": all_members,
    }
