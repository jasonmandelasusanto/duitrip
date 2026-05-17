from pydantic import BaseModel
from typing import Optional


class SettlementCreate(BaseModel):
    fromUserId: str
    toUserId: str
    amountInDestinationCurrency: float
    note: Optional[str] = None
