import uuid
from datetime import datetime, timezone
from fastapi import APIRouter, Depends, HTTPException
from app.dependencies import get_current_user
from app.models.trip import GhostMemberCreate, GhostMemberUpdate, GhostPromote, InviteCreate
from app.services.firestore import get_db, doc_to_dict
from app.utils.validators import require_trip_member, require_trip_owner

router = APIRouter()


def _get_trip(db, trip_id: str) -> dict:
    doc = db.collection("trips").document(trip_id).get()
    data = doc_to_dict(doc)
    if not data:
        raise HTTPException(status_code=404, detail="Trip not found")
    return data


@router.post("/{trip_id}/members/ghost")
async def add_ghost(trip_id: str, body: GhostMemberCreate, current_user: dict = Depends(get_current_user)):
    db = get_db()
    trip = _get_trip(db, trip_id)
    require_trip_member(trip, current_user["uid"])

    ghost_id = f"ghost_{uuid.uuid4().hex[:8]}"
    ghost = {
        "userId": None,
        "email": None,
        "displayName": body.displayName,
        "photoURL": None,
        "homeCurrency": body.homeCurrency,
        "role": "ghost",
        "joinedAt": None,
        "ghostId": ghost_id,
    }

    members = trip.get("members", [])
    members.append(ghost)
    db.collection("trips").document(trip_id).update({
        "members": members,
        "updatedAt": datetime.now(timezone.utc),
    })
    return {"ghostId": ghost_id, "displayName": body.displayName, "homeCurrency": body.homeCurrency, "role": "ghost"}


@router.patch("/{trip_id}/members/ghost/{ghost_id}")
async def update_ghost(trip_id: str, ghost_id: str, body: GhostMemberUpdate,
                       current_user: dict = Depends(get_current_user)):
    db = get_db()
    trip = _get_trip(db, trip_id)
    require_trip_member(trip, current_user["uid"])

    members = trip.get("members", [])
    updated = False
    for m in members:
        if m.get("ghostId") == ghost_id:
            if body.displayName:
                m["displayName"] = body.displayName
            if body.homeCurrency:
                m["homeCurrency"] = body.homeCurrency
            updated = True
            break

    if not updated:
        raise HTTPException(status_code=404, detail="Ghost member not found")

    db.collection("trips").document(trip_id).update({"members": members, "updatedAt": datetime.now(timezone.utc)})
    return {"ok": True}


@router.post("/{trip_id}/members/ghost/{ghost_id}/promote")
async def promote_ghost(trip_id: str, ghost_id: str, body: GhostPromote,
                        current_user: dict = Depends(get_current_user)):
    db = get_db()
    trip = _get_trip(db, trip_id)
    require_trip_owner(trip, current_user["uid"])

    # Add invite
    invites = trip.get("invites", [])
    invites.append({
        "email": body.email,
        "invitedBy": current_user["uid"],
        "invitedAt": datetime.now(timezone.utc),
        "status": "pending",
        "ghostId": ghost_id,
    })
    db.collection("trips").document(trip_id).update({
        "invites": invites,
        "updatedAt": datetime.now(timezone.utc),
    })
    return {"ok": True, "message": f"Invite sent to {body.email}"}


@router.post("/{trip_id}/invites")
async def invite_member(trip_id: str, body: InviteCreate, current_user: dict = Depends(get_current_user)):
    db = get_db()
    trip = _get_trip(db, trip_id)
    require_trip_member(trip, current_user["uid"])

    # Check not already member
    for m in trip.get("members", []):
        if m.get("email") == body.email:
            raise HTTPException(status_code=400, detail="Already a member")

    invites = trip.get("invites", [])
    for inv in invites:
        if inv.get("email") == body.email and inv.get("status") == "pending":
            raise HTTPException(status_code=400, detail="Already invited")

    invites.append({
        "email": body.email,
        "invitedBy": current_user["uid"],
        "invitedAt": datetime.now(timezone.utc),
        "status": "pending",
    })
    db.collection("trips").document(trip_id).update({
        "invites": invites,
        "updatedAt": datetime.now(timezone.utc),
    })
    return {"ok": True}


@router.post("/{trip_id}/invites/accept")
async def accept_invite(trip_id: str, current_user: dict = Depends(get_current_user)):
    db = get_db()
    trip = _get_trip(db, trip_id)
    email = current_user["email"]

    invites = trip.get("invites", [])
    matched = None
    for inv in invites:
        if inv.get("email") == email and inv.get("status") == "pending":
            matched = inv
            break

    if not matched:
        raise HTTPException(status_code=400, detail="No pending invite found for your email")

    matched["status"] = "accepted"

    user_doc = db.collection("users").document(current_user["uid"]).get()
    user_data = doc_to_dict(user_doc) or {}

    new_member = {
        "userId": current_user["uid"],
        "email": email,
        "displayName": user_data.get("displayName", current_user.get("name", "")),
        "photoURL": user_data.get("photoURL"),
        "homeCurrency": user_data.get("homeCurrency", "USD"),
        "role": "member",
        "joinedAt": datetime.now(timezone.utc),
        "ghostId": None,
    }

    members = trip.get("members", [])
    # If this was a ghost promotion, replace the ghost
    ghost_id = matched.get("ghostId")
    if ghost_id:
        for i, m in enumerate(members):
            if m.get("ghostId") == ghost_id:
                members[i] = new_member
                break
    else:
        members.append(new_member)

    db.collection("trips").document(trip_id).update({
        "members": members,
        "invites": invites,
        "updatedAt": datetime.now(timezone.utc),
    })
    return {"ok": True, "tripId": trip_id}
