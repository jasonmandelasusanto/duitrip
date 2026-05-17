import httpx
from app.config import settings


async def verify_turnstile(token: str, remote_ip: str | None = None) -> bool:
    """Verify a Cloudflare Turnstile token. Returns True if valid."""
    if not settings.CLOUDFLARE_TURNSTILE_SECRET_KEY:
        # Not configured — skip verification (local dev)
        return True
    if not token:
        return False

    data = {"secret": settings.CLOUDFLARE_TURNSTILE_SECRET_KEY, "response": token}
    if remote_ip:
        data["remoteip"] = remote_ip

    try:
        async with httpx.AsyncClient(timeout=10.0, follow_redirects=True) as client:
            resp = await client.post(
                "https://challenges.cloudflare.com/turnstile/v0/siteverify",
                data=data,
            )
            resp.raise_for_status()
            result = resp.json()
            return result.get("success", False)
    except Exception:
        return False
