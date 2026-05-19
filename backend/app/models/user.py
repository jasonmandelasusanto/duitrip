from pydantic import BaseModel
from typing import Optional


class UserProfile(BaseModel):
    uid: str
    email: str
    displayName: Optional[str] = None
    photoURL: Optional[str] = None
    homeCurrency: str = "USD"


class UserUpdate(BaseModel):
    displayName: Optional[str] = None
    homeCurrency: Optional[str] = None
    photoURL: Optional[str] = None
