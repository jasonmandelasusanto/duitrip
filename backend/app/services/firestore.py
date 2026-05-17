import os
from google.cloud.firestore_v1 import DocumentReference

_db = None


def get_db():
    global _db
    if _db is None:
        if os.environ.get("FIRESTORE_EMULATOR_HOST"):
            # Emulator mode: the emulator ignores credentials, so use anonymous auth
            # and connect directly via google-cloud-firestore (bypasses firebase-admin
            # credential loading which requires ADC or a service account).
            from google.cloud import firestore as gcp_firestore
            from google.auth.credentials import AnonymousCredentials
            _db = gcp_firestore.Client(
                project=os.environ.get("FIREBASE_PROJECT_ID", "demo-duitrip"),
                credentials=AnonymousCredentials(),
            )
        else:
            from firebase_admin import firestore
            _db = firestore.client()
    return _db


def doc_to_dict(doc) -> dict | None:
    if not doc.exists:
        return None
    data = doc.to_dict()
    data["id"] = doc.id
    return data
