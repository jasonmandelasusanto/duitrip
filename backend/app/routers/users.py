from fastapi import APIRouter, Depends, Header, HTTPException, Request, status
from app.dependencies import get_current_user
from app.models.user import UserProfile, UserUpdate
from app.services.firestore import get_db, doc_to_dict
from app.services.turnstile import verify_turnstile

router = APIRouter()


@router.get("/me")
async def get_me(current_user: dict = Depends(get_current_user)):
    db = get_db()
    doc = db.collection("users").document(current_user["uid"]).get()
    data = doc_to_dict(doc)
    if not data:
        return {"uid": current_user["uid"], "email": current_user["email"],
                "displayName": current_user.get("name", ""), "homeCurrency": "USD"}
    data["uid"] = current_user["uid"]
    return data


@router.patch("/me")
async def update_me(body: UserUpdate, current_user: dict = Depends(get_current_user)):
    db = get_db()
    ref = db.collection("users").document(current_user["uid"])
    updates = {k: v for k, v in body.model_dump().items() if v is not None}
    if updates:
        ref.set(updates, merge=True)
    return {"ok": True}


@router.delete("/me")
async def delete_me(current_user: dict = Depends(get_current_user)):
    from firebase_admin import auth as admin_auth
    db = get_db()
    uid = current_user["uid"]
    db.collection("users").document(uid).delete()
    admin_auth.delete_user(uid)
    return {"ok": True}


@router.post("/me/init")
async def init_me(
    body: UserProfile,
    request: Request,
    current_user: dict = Depends(get_current_user),
    cf_turnstile_token: str = Header(None, alias="X-Turnstile-Token"),
):
    db = get_db()
    remote_ip = request.client.host if request.client else None
    if not await verify_turnstile(cf_turnstile_token or "", remote_ip):
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="CAPTCHA verification failed")

    from datetime import datetime, timezone
    db.collection("users").document(current_user["uid"]).set({
        "uid": current_user["uid"],
        "email": current_user["email"],
        "displayName": body.displayName or current_user.get("name", ""),
        "photoURL": body.photoURL,
        "homeCurrency": body.homeCurrency,
        "createdAt": datetime.now(timezone.utc),
    }, merge=True)
    return {"ok": True}
