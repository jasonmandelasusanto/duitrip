from fastapi import HTTPException, status


def require_trip_member(trip: dict, uid: str):
    real_members = [m for m in trip.get("members", []) if m.get("role") != "ghost"]
    uids = [m.get("userId") for m in real_members]
    if uid not in uids:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Not a trip member")


def require_trip_owner(trip: dict, uid: str):
    if trip.get("createdBy") != uid:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Only trip owner can perform this action")
