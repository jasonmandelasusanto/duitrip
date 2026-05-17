from pydantic import BaseModel
from typing import Optional, List


class SplitInput(BaseModel):
    userId: str
    percentage: Optional[float] = None
    exactAmount: Optional[float] = None
    exactCurrency: Optional[str] = None


class ExpenseCreate(BaseModel):
    description: str
    category: str
    originalAmount: float
    originalCurrency: str
    paidBy: str
    splitMode: str = "equal"  # "equal" | "percentage" | "exact"
    splits: List[SplitInput] = []


class ExpenseUpdate(BaseModel):
    description: Optional[str] = None
    category: Optional[str] = None
    originalAmount: Optional[float] = None
    originalCurrency: Optional[str] = None
    paidBy: Optional[str] = None
    splitMode: Optional[str] = None
    splits: Optional[List[SplitInput]] = None
