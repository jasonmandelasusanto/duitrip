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
    notes: Optional[str] = None
    customRate: Optional[float] = None  # override live exchange rate
    receiptUrl: Optional[str] = None   # base64 data URL or storage URL
    isRecurring: Optional[bool] = False
    expenseDate: Optional[str] = None  # YYYY-MM-DD; None/today → "latest" rates


class ExpenseUpdate(BaseModel):
    description: Optional[str] = None
    category: Optional[str] = None
    originalAmount: Optional[float] = None
    originalCurrency: Optional[str] = None
    paidBy: Optional[str] = None
    splitMode: Optional[str] = None
    splits: Optional[List[SplitInput]] = None
    notes: Optional[str] = None
    customRate: Optional[float] = None
    receiptUrl: Optional[str] = None
