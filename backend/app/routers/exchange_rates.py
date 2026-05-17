from fastapi import APIRouter, Query
from app.services import exchange_rates as fx_service

router = APIRouter()


@router.get("")
async def get_rates(base: str = Query(...), symbols: str = Query(...)):
    symbol_list = [s.strip() for s in symbols.split(",")]
    rates = await fx_service.fetch_rates(base, symbol_list)
    stale = fx_service.is_stale(base, symbol_list)
    return {"base": base, "rates": rates, "stale": stale}
