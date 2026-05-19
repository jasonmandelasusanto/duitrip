from pydantic import BaseModel


class NudgeRequest(BaseModel):
    toUserId: str
    amount: float
    currency: str
