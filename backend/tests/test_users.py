"""
Tests for /users/me endpoint.

Key invariant: the response must ALWAYS contain a `uid` field matching the
authenticated user, regardless of what is (or isn't) stored in Firestore.
This catches the class of bug where a Firestore doc written without a `uid`
field causes the frontend to receive user=={...} with uid undefined, which
then breaks any component that reads user.uid on first render.
"""

from unittest.mock import MagicMock, patch
import pytest
from fastapi.testclient import TestClient

from app.main import app
from app.dependencies import get_current_user


MOCK_USER = {"uid": "user-abc-123", "email": "test@example.com", "name": "Test User"}


def _override_auth():
    return MOCK_USER


@pytest.fixture
def client():
    app.dependency_overrides[get_current_user] = _override_auth
    yield TestClient(app)
    app.dependency_overrides.clear()


def _mock_doc(data: dict | None):
    """Return a mock Firestore DocumentSnapshot."""
    doc = MagicMock()
    doc.exists = data is not None
    doc.id = MOCK_USER["uid"]
    doc.to_dict.return_value = dict(data) if data else {}
    return doc


# ---------------------------------------------------------------------------
# /users/me — uid always present
# ---------------------------------------------------------------------------

class TestGetMe:
    def test_uid_present_when_doc_has_uid(self, client):
        """Normal path: Firestore doc includes uid field."""
        firestore_data = {
            "uid": MOCK_USER["uid"],
            "email": "test@example.com",
            "displayName": "Test User",
            "homeCurrency": "USD",
        }
        with patch("app.routers.users.get_db") as mock_db:
            mock_db.return_value.collection.return_value.document.return_value.get.return_value = (
                _mock_doc(firestore_data)
            )
            r = client.get("/api/users/me")

        assert r.status_code == 200
        assert r.json()["uid"] == MOCK_USER["uid"]

    def test_uid_present_when_doc_missing_uid_field(self, client):
        """
        Bug regression: Firestore doc exists but was written without a `uid`
        field (e.g. old user, or PATCH that never included uid). The endpoint
        must still return uid from the auth token.
        """
        firestore_data = {
            # intentionally no "uid" key
            "email": "test@example.com",
            "displayName": "Test User",
            "homeCurrency": "SGD",
        }
        with patch("app.routers.users.get_db") as mock_db:
            mock_db.return_value.collection.return_value.document.return_value.get.return_value = (
                _mock_doc(firestore_data)
            )
            r = client.get("/api/users/me")

        assert r.status_code == 200
        data = r.json()
        assert "uid" in data, "uid must always be present in /users/me response"
        assert data["uid"] == MOCK_USER["uid"]

    def test_uid_present_when_doc_does_not_exist(self, client):
        """User authenticated but no Firestore doc yet (pre-onboarding)."""
        with patch("app.routers.users.get_db") as mock_db:
            mock_db.return_value.collection.return_value.document.return_value.get.return_value = (
                _mock_doc(None)
            )
            r = client.get("/api/users/me")

        assert r.status_code == 200
        data = r.json()
        assert data["uid"] == MOCK_USER["uid"]

    def test_uid_from_auth_not_from_doc(self, client):
        """
        Even if the Firestore doc somehow has a different uid stored,
        the response uid must come from the verified auth token.
        """
        firestore_data = {
            "uid": "stale-or-wrong-uid",
            "email": "test@example.com",
            "displayName": "Test",
            "homeCurrency": "USD",
        }
        with patch("app.routers.users.get_db") as mock_db:
            mock_db.return_value.collection.return_value.document.return_value.get.return_value = (
                _mock_doc(firestore_data)
            )
            r = client.get("/api/users/me")

        assert r.status_code == 200
        assert r.json()["uid"] == MOCK_USER["uid"]

    def test_response_shape(self, client):
        """Response must include all fields the frontend depends on."""
        firestore_data = {
            "uid": MOCK_USER["uid"],
            "email": "test@example.com",
            "displayName": "Test User",
            "homeCurrency": "IDR",
            "photoURL": None,
        }
        with patch("app.routers.users.get_db") as mock_db:
            mock_db.return_value.collection.return_value.document.return_value.get.return_value = (
                _mock_doc(firestore_data)
            )
            r = client.get("/api/users/me")

        assert r.status_code == 200
        data = r.json()
        for field in ("uid", "email", "displayName", "homeCurrency"):
            assert field in data, f"Field '{field}' missing from /users/me response"
