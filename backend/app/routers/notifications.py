from fastapi import APIRouter, Depends, HTTPException
from app.dependencies import get_current_user
from app.services.firestore import get_db, doc_to_dict

router = APIRouter()


@router.get("")
async def get_notifications(current_user: dict = Depends(get_current_user)):
    db = get_db()
    uid = current_user["uid"]
    docs = list(db.collection("notifications").where("toUserId", "==", uid).stream())
    notifs = [doc_to_dict(doc) for doc in docs if doc.exists]
    notifs.sort(key=lambda x: x.get("createdAt", ""), reverse=True)
    return notifs[:50]


@router.post("/read-all")
async def mark_all_read(current_user: dict = Depends(get_current_user)):
    db = get_db()
    uid = current_user["uid"]
    docs = list(db.collection("notifications").where("toUserId", "==", uid).where("read", "==", False).stream())
    for doc in docs:
        doc.reference.update({"read": True})
    return {"ok": True}


@router.delete("/{notif_id}")
async def delete_notification(notif_id: str, current_user: dict = Depends(get_current_user)):
    db = get_db()
    ref = db.collection("notifications").document(notif_id)
    doc = ref.get()
    if not doc.exists or doc.to_dict().get("toUserId") != current_user["uid"]:
        raise HTTPException(status_code=404, detail="Not found")
    ref.delete()
    return {"ok": True}
