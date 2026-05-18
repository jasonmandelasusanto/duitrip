import time
import httpx

_cache: dict[str, dict] = {}
_cache_ttl = 3600  # 1 hour


async def fetch_rates(base: str, symbols: list[str]) -> dict[str, float]:
    """Fetch exchange rates from frankfurter.app with TTL cache."""
    symbols_key = ",".join(sorted(symbols))
    cache_key = f"{base}:{symbols_key}"
    now = time.time()

    if cache_key in _cache and now - _cache[cache_key]["ts"] < _cache_ttl:
        return _cache[cache_key]["rates"]

    try:
        url = "https://api.frankfurter.app/latest"
        async with httpx.AsyncClient(timeout=10.0, follow_redirects=True) as client:
            resp = await client.get(url, params={"base": base, "symbols": symbols_key})
            resp.raise_for_status()
            data = resp.json()
            rates = data.get("rates", {})
            rates[base] = 1.0  # include self
            _cache[cache_key] = {"rates": rates, "ts": now, "stale": False}
            return rates
    except Exception:
        if cache_key in _cache:
            _cache[cache_key]["stale"] = True
            return _cache[cache_key]["rates"]
        # Fallback: return 1.0 for all
        return {s: 1.0 for s in symbols + [base]}


def is_stale(base: str, symbols: list[str]) -> bool:
    symbols_key = ",".join(sorted(symbols))
    cache_key = f"{base}:{symbols_key}"
    return _cache.get(cache_key, {}).get("stale", False)
