import uuid
from datetime import datetime, timezone
from fastapi import APIRouter, Depends, HTTPException
from app.dependencies import get_current_user
from app.models.trip import CustomCategoryCreate
from app.services.firestore import get_db, doc_to_dict
from app.utils.validators import require_trip_member

router = APIRouter()


def _get_trip(db, trip_id: str) -> dict:
    doc = db.collection("trips").document(trip_id).get()
    data = doc_to_dict(doc)
    if not data:
        raise HTTPException(status_code=404, detail="Trip not found")
    return data


@router.post("/{trip_id}/categories")
async def add_category(trip_id: str, body: CustomCategoryCreate, current_user: dict = Depends(get_current_user)):
    db = get_db()
    trip = _get_trip(db, trip_id)
    require_trip_member(trip, current_user["uid"])

    custom = trip.get("customCategories", [])
    if len(custom) >= 20:
        raise HTTPException(status_code=400, detail="Max 20 custom categories reached")

    names = [c["name"].lower() for c in custom]
    if body.name.lower() in names:
        raise HTTPException(status_code=400, detail="Category name already exists")

    cat_id = f"custom_{uuid.uuid4().hex[:8]}"
    now = datetime.now(timezone.utc)
    new_cat = {"id": cat_id, "name": body.name, "emoji": body.emoji, "createdBy": current_user["uid"], "createdAt": now}
    custom.append(new_cat)

    db.collection("trips").document(trip_id).update({
        "customCategories": custom,
        "updatedAt": now,
    })
    return new_cat


@router.delete("/{trip_id}/categories/{category_id}")
async def delete_category(trip_id: str, category_id: str, current_user: dict = Depends(get_current_user)):
    db = get_db()
    trip = _get_trip(db, trip_id)
    require_trip_member(trip, current_user["uid"])

    custom = trip.get("customCategories", [])
    cat = next((c for c in custom if c["id"] == category_id), None)
    if not cat:
        raise HTTPException(status_code=404, detail="Category not found")

    # Check no expenses use it
    expenses = db.collection("trips").document(trip_id).collection("expenses").where("category", "==", cat["name"]).limit(1).stream()
    if any(True for _ in expenses):
        raise HTTPException(status_code=400, detail="Category is in use — reassign expenses first")

    custom = [c for c in custom if c["id"] != category_id]
    db.collection("trips").document(trip_id).update({
        "customCategories": custom,
        "updatedAt": datetime.now(timezone.utc),
    })
    return {"ok": True}
