import uuid
from datetime import datetime, timezone
from fastapi import APIRouter, Depends, HTTPException
from app.dependencies import get_current_user
from app.models.trip import TripCreate, TripUpdate
from app.models.notification import NudgeRequest
from app.services.firestore import get_db, doc_to_dict
from app.utils.country_currency import country_code_to_currency
from app.utils.validators import require_trip_member, require_trip_owner

router = APIRouter()


def _resolve_currency(destination: str) -> str:
    # Simple heuristic: try to find country code from known names
    # Real lookup happens in frontend via Nominatim; backend trusts what's sent
    # but we still try a best-effort match
    dest_lower = destination.lower()
    name_to_code = {
        "indonesia": "ID", "singapore": "SG", "thailand": "TH", "malaysia": "MY",
        "japan": "JP", "korea": "KR", "vietnam": "VN", "philippines": "PH",
        "australia": "AU", "united states": "US", "usa": "US", "uk": "GB",
        "united kingdom": "GB", "germany": "DE", "france": "FR", "italy": "IT",
        "spain": "ES", "india": "IN", "china": "CN", "hong kong": "HK",
        "taiwan": "TW", "new zealand": "NZ", "canada": "CA",
    }
    for name, code in name_to_code.items():
        if name in dest_lower:
            return country_code_to_currency(code)
    return "USD"


@router.post("")
async def create_trip(body: TripCreate, current_user: dict = Depends(get_current_user)):
    db = get_db()
    trip_id = f"trip_{uuid.uuid4().hex[:12]}"
    now = datetime.now(timezone.utc)

    # Get user profile for member entry
    user_doc = db.collection("users").document(current_user["uid"]).get()
    user_data = doc_to_dict(user_doc) or {}

    dest_currency = body.destinationCurrency or _resolve_currency(body.destination)

    trip = {
        "tripId": trip_id,
        "name": body.name,
        "destination": body.destination,
        "destinationCurrency": dest_currency,
        "startDate": body.startDate,
        "endDate": body.endDate,
        "budget": body.budget,
        "budgetCurrency": body.budgetCurrency or dest_currency,
        "createdBy": current_user["uid"],
        "members": [{
            "userId": current_user["uid"],
            "email": current_user["email"],
            "displayName": user_data.get("displayName", current_user.get("name", "")),
            "photoURL": user_data.get("photoURL"),
            "homeCurrency": user_data.get("homeCurrency", "USD"),
            "role": "owner",
            "joinedAt": now,
            "ghostId": None,
        }],
        "memberUids": [current_user["uid"]],
        "invites": [],
        "customCategories": [],
        "status": "active",
        "createdAt": now,
        "updatedAt": now,
    }

    db.collection("trips").document(trip_id).set(trip)
    return {"tripId": trip_id, "destinationCurrency": dest_currency}


@router.get("")
async def list_trips(current_user: dict = Depends(get_current_user)):
    db = get_db()
    uid = current_user["uid"]
    # array_contains on memberUids — O(user's trips) instead of full scan
    docs = db.collection("trips").where("memberUids", "array_contains", uid).stream()
    result = []
    for doc in docs:
        data = doc.to_dict()
        if data.get("status") == "archived":
            continue
        data["tripId"] = doc.id
        result.append(data)
    return result


@router.get("/{trip_id}")
async def get_trip(trip_id: str, current_user: dict = Depends(get_current_user)):
    db = get_db()
    doc = db.collection("trips").document(trip_id).get()
    data = doc_to_dict(doc)
    if not data:
        raise HTTPException(status_code=404, detail="Trip not found")
    require_trip_member(data, current_user["uid"])
    return data


@router.patch("/{trip_id}")
async def update_trip(trip_id: str, body: TripUpdate, current_user: dict = Depends(get_current_user)):
    db = get_db()
    doc = db.collection("trips").document(trip_id).get()
    data = doc_to_dict(doc)
    if not data:
        raise HTTPException(status_code=404, detail="Trip not found")
    require_trip_owner(data, current_user["uid"])
    updates = {k: v for k, v in body.model_dump().items() if v is not None}
    updates["updatedAt"] = datetime.now(timezone.utc)
    if "destination" in updates:
        updates["destinationCurrency"] = _resolve_currency(updates["destination"])
    db.collection("trips").document(trip_id).update(updates)
    return {"ok": True}


@router.post("/{trip_id}/duplicate")
async def duplicate_trip(trip_id: str, current_user: dict = Depends(get_current_user)):
    db = get_db()
    doc = db.collection("trips").document(trip_id).get()
    data = doc_to_dict(doc)
    if not data:
        raise HTTPException(status_code=404, detail="Trip not found")
    require_trip_member(data, current_user["uid"])

    new_id = f"trip_{uuid.uuid4().hex[:12]}"
    now = datetime.now(timezone.utc)
    new_trip = {
        "tripId": new_id,
        "name": f"{data['name']} (copy)",
        "destination": data["destination"],
        "destinationCurrency": data["destinationCurrency"],
        "startDate": data["startDate"],
        "endDate": data["endDate"],
        "budget": data.get("budget"),
        "budgetCurrency": data.get("budgetCurrency", data.get("destinationCurrency")),
        "createdBy": current_user["uid"],
        "members": data.get("members", []),
        "memberUids": data.get("memberUids", [current_user["uid"]]),
        "invites": [],
        "customCategories": data.get("customCategories", []),
        "status": "active",
        "createdAt": now,
        "updatedAt": now,
    }
    db.collection("trips").document(new_id).set(new_trip)
    return {"tripId": new_id}


@router.delete("/{trip_id}")
async def delete_trip(trip_id: str, current_user: dict = Depends(get_current_user)):
    db = get_db()
    doc = db.collection("trips").document(trip_id).get()
    data = doc_to_dict(doc)
    if not data:
        raise HTTPException(status_code=404, detail="Trip not found")
    require_trip_owner(data, current_user["uid"])
    db.collection("trips").document(trip_id).update({"status": "archived"})
    return {"ok": True}


@router.post("/{trip_id}/nudge")
async def nudge_member(trip_id: str, body: NudgeRequest, current_user: dict = Depends(get_current_user)):
    db = get_db()
    doc = db.collection("trips").document(trip_id).get()
    data = doc_to_dict(doc)
    if not data:
        raise HTTPException(status_code=404, detail="Trip not found")

    members = data.get("members", [])
    sender = next((m for m in members if m.get("userId") == current_user["uid"]), None)
    if not sender:
        raise HTTPException(status_code=403, detail="Not a trip member")

    recipient = next((m for m in members if m.get("userId") == body.toUserId and m.get("role") != "ghost"), None)
    if not recipient:
        raise HTTPException(status_code=404, detail="Recipient not found")
    if body.toUserId == current_user["uid"]:
        raise HTTPException(status_code=400, detail="Cannot nudge yourself")

    # One nudge per sender-recipient-trip per hour
    recent = list(db.collection("notifications")
        .where("toUserId", "==", body.toUserId)
        .where("fromUid", "==", current_user["uid"])
        .where("tripId", "==", trip_id)
        .stream())
    from datetime import timedelta
    cutoff = (datetime.now(timezone.utc) - timedelta(hours=1)).isoformat()
    if any(n.to_dict().get("createdAt", "") > cutoff for n in recent):
        raise HTTPException(status_code=429, detail="Already nudged recently — wait an hour")

    notif_id = f"notif_{uuid.uuid4().hex[:12]}"
    sender_name = sender.get("displayName") or current_user.get("name", "Someone")
    db.collection("notifications").document(notif_id).set({
        "notifId": notif_id,
        "toUserId": body.toUserId,
        "fromUid": current_user["uid"],
        "fromName": sender_name,
        "tripId": trip_id,
        "tripName": data.get("name", ""),
        "amount": body.amount,
        "currency": body.currency,
        "read": False,
        "createdAt": datetime.now(timezone.utc).isoformat(),
    })

    # Send FCM push notification if recipient has a token
    try:
        recipient_doc = db.collection("users").document(body.toUserId).get()
        if recipient_doc.exists:
            fcm_token = (recipient_doc.to_dict() or {}).get("fcmToken")
            if fcm_token:
                from firebase_admin import messaging
                messaging.send(messaging.Message(
                    notification=messaging.Notification(
                        title=f"Nudge from {sender_name}",
                        body=f"You owe {body.amount} {body.currency} on {data.get('name', 'your trip')}",
                    ),
                    token=fcm_token,
                ))
    except Exception:
        pass  # FCM failures must never break the nudge flow

    return {"ok": True}
