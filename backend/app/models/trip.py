from pydantic import BaseModel
from typing import Optional
from datetime import datetime


class TripMember(BaseModel):
    userId: Optional[str] = None
    email: Optional[str] = None
    displayName: str
    photoURL: Optional[str] = None
    homeCurrency: str
    role: str  # "owner" | "member" | "ghost"
    joinedAt: Optional[datetime] = None
    ghostId: Optional[str] = None


class TripInvite(BaseModel):
    email: str
    invitedBy: str
    status: str = "pending"


class CustomCategory(BaseModel):
    id: str
    name: str
    emoji: str = "🏷️"
    createdBy: str


class TripCreate(BaseModel):
    name: str
    destination: str
    destinationCurrency: Optional[str] = None
    startDate: str
    endDate: str
    budget: Optional[float] = None
    budgetCurrency: Optional[str] = None


class TripUpdate(BaseModel):
    name: Optional[str] = None
    destination: Optional[str] = None
    startDate: Optional[str] = None
    endDate: Optional[str] = None
    budget: Optional[float] = None
    budgetCurrency: Optional[str] = None


class GhostMemberCreate(BaseModel):
    displayName: str
    homeCurrency: str


class GhostMemberUpdate(BaseModel):
    displayName: Optional[str] = None
    homeCurrency: Optional[str] = None


class GhostPromote(BaseModel):
    email: str


class InviteCreate(BaseModel):
    email: str


class CustomCategoryCreate(BaseModel):
    name: str
    emoji: str = "🏷️"
