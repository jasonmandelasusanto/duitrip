import uuid
from datetime import datetime, timezone
from fastapi import APIRouter, Depends, HTTPException, status
from app.dependencies import get_current_user
from app.models.trip import TripCreate, TripUpdate
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
    # Firestore doesn't support array-of-object queries well; fetch all and filter
    all_trips = db.collection("trips").where("status", "!=", "archived").stream()
    result = []
    for doc in all_trips:
        data = doc.to_dict()
        members = data.get("members", [])
        real_uids = [m.get("userId") for m in members if m.get("role") != "ghost"]
        if uid in real_uids:
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
